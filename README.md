# FactoryFlow ERP–MES PoC

ERP가 작업지시 원본을 관리하고 MES가 생산 실행을 관리하는 독립 Spring Boot 애플리케이션입니다. 계획 변경, 통신 실패 재전송, 역순 수신, 생산 시작 후 변경 제한을 다룹니다.

- Java 17 / Spring Boot 4.0.8 / Spring Data JPA / RestClient / Validation
- 운영 DB: MSSQL의 독립 ERP·MES 데이터베이스. 테스트 DB: 애플리케이션별 H2
- ERP: 8080 / MES: 8081

## 구현 범위

- ERP 생성·계획 변경·FAILED 재전송 API, 전송 상태와 최근 오류 저장
- MES의 `sourceSystem + externalId` 식별 및 버전 비교, 처리 결과 DTO
- MES 실행 상태 변경과 생산 시작 후 주요 계획 변경 차단
- 서비스 단위 테스트, MockMvc·H2 API 통합 테스트, ERP RestClient 요청·응답 검증
- Swagger/OpenAPI 요청·응답 예시와 오류 계약

작업지시 번호는 별도의 유일성 제약입니다. `eventId`별 이력이나 중복 제거 테이블은 없으며, 동일·과거 버전의 미반영으로 재전송을 처리합니다.

## API

| 시스템 | 메서드·경로 | 동작 |
|---|---|---|
| ERP | `POST /api/work-orders` | 버전 1 생성 후 MES 전송 |
| ERP | `PUT /api/work-orders/{id}` | 다섯 계획 필드 변경, 실제 변경 시 버전 +1 |
| ERP | `POST /api/work-orders/{id}/resend` | FAILED의 현재 계획·현재 버전 재전송, 본문 없음 |
| MES | `POST /api/work-orders/sync` | 원천 식별·버전 비교·생산 상태 검증 후 반영 |
| MES | `PATCH /api/work-orders/{id}/execution-status` | 생산 실행 상태 변경 |

`{id}`는 각각 ERP 또는 MES 내부 PK입니다. MES 응답의 `id`와 ERP의 `id`가 같다고 가정하지 않습니다. MES의 `externalId`가 ERP PK입니다. API 응답은 모두 DTO를 사용합니다.

### ERP 생성·변경

생성 요청:

```json
{
  "workOrderNo": "WO-20260903-001",
  "productCode": "PRODUCT-A001",
  "plannedQuantity": 100,
  "dueDate": "2026-09-10",
  "priority": 1,
  "routingCode": "ROUTING-A",
  "routingRevision": 1
}
```

변경 요청은 다섯 계획 필드를 전달하는 PUT입니다. 작업지시 번호·품목은 변경하지 않습니다. 계획수량·라우팅 리비전은 1 이상, 우선순위는 0 이상입니다. 납기일은 필수이며 라우팅 코드는 null을 허용합니다.

```json
{
  "plannedQuantity": 200,
  "dueDate": "2026-09-12",
  "priority": 2,
  "routingCode": "ROUTING-B",
  "routingRevision": 2
}
```

실제 변경 시에만 `version`을 1 증가시킵니다. 전송 직전 `PENDING` 및 오류 초기화, 성공 시 `SUCCESS`, 실패 시 `FAILED`와 최근 오류를 기록합니다. 무변경 요청은 버전·상태·오류를 유지하며 전송하지 않습니다. FAILED 무변경 작업지시는 재전송 API를 사용합니다.

```json
{
  "id": 1,
  "workOrderNo": "WO-20260903-001",
  "version": 2,
  "mesSyncStatus": "SUCCESS",
  "mesSyncError": null
}
```

ERP 저장 성공은 생성 시 HTTP 201, 변경·재전송 시 HTTP 200입니다. **MES 전송 성공 여부는 `mesSyncStatus`로 판단합니다.** MES가 409를 반환해도 ERP의 변경 계획은 저장되고 `FAILED`로 남습니다. 저장 오류와 외부 시스템 전송 오류를 구분하는 기존 정책을 유지합니다. 최근 오류는 기존 컬럼 길이에 맞춰 최대 1,000자로 저장합니다.

없는 ID는 404, 잘못된 요청·중복 번호 사전 검증은 400, FAILED가 아닌 재전송은 409입니다. DB 유일성 제약 충돌도 409로 처리합니다.

### MES 동기화 결과

동기화 요청은 ERP 현재 계획 전체에 다음 원천 정보를 포함합니다.

```json
{
  "sourceSystem": "ERP",
  "eventId": "11111111-1111-1111-1111-111111111111",
  "eventType": "WORK_ORDER_UPDATED",
  "externalId": 1,
  "workOrderNo": "WO-20260903-001",
  "version": 2,
  "productCode": "PRODUCT-A001",
  "plannedQuantity": 200,
  "dueDate": "2026-09-12",
  "priority": 2,
  "routingCode": "ROUTING-B",
  "routingRevision": 2
}
```

| 상황 | MES 결과 (HTTP 200) | ERP 판단 |
|---|---|---|
| 원천 작업지시 없음 | `CREATED` | 현재 버전 일치 시 SUCCESS |
| 수신 버전 > 현재 버전 | `UPDATED` | 현재 버전 일치 시 SUCCESS |
| 수신 버전 = 현재 버전 | `IGNORED_SAME_VERSION` | 이미 적용됨 → SUCCESS |
| 수신 버전 < 현재 버전 | `IGNORED_OLD_VERSION` | ERP보다 MES가 앞섬 → FAILED, 확인 필요 |

```json
{
  "id": 11,
  "workOrderNo": "WO-20260903-001",
  "receivedVersion": 2,
  "appliedVersion": 2,
  "result": "IGNORED_SAME_VERSION",
  "message": "동일 버전이 이미 적용되어 반영하지 않았습니다."
}
```

동일·과거 버전은 생산 상태와 관계없이 데이터를 변경하지 않습니다. 더 높은 버전만 변경 규칙을 검사합니다. ERP는 빈 응답, 결과·번호·버전 불일치도 FAILED로 기록합니다. 재전송할 때 `eventId`는 새로 발급하지만 **계획과 버전은 유지**합니다. `eventType`은 원본 버전 1이면 CREATED, 이후 버전이면 UPDATED입니다.

### 생산 실행 상태 및 변경 제한

상태 요청:

```json
{"executionStatus": "IN_PROGRESS"}
```

| 현재 상태 | 허용하는 다음 상태 | 주요 계획 변경 |
|---|---|---|
| PLANNED | READY, IN_PROGRESS, CANCELLED | 허용 |
| READY | IN_PROGRESS, CANCELLED | 허용 |
| IN_PROGRESS | COMPLETED, CANCELLED | 차단 |
| COMPLETED | 없음 | 차단 |
| CANCELLED | 없음 | 차단 |

같은 상태를 다시 지정하면 무변경 성공입니다. 이전 상태로 되돌릴 수 없으므로 생산 시작 후 PLANNED로 되돌려 제한을 우회할 수 없습니다. 상태 변경은 `sourceVersion`을 증가시키지 않습니다.

차단하는 주요 계획값은 **계획수량·라우팅 코드·라우팅 리비전**입니다. 납기·우선순위만 변경하는 높은 버전은 허용합니다. 작업지시 번호·품목 변경은 항상 차단합니다. 취소 시점 이력을 추가하지 않는 이번 범위에서는 CANCELLED도 주요 변경을 보수적으로 차단합니다.

```json
{
  "status": 409,
  "message": "생산 시작 후 또는 취소된 작업지시는 계획수량, 라우팅 코드, 라우팅 리비전을 변경할 수 없습니다.",
  "workOrderNo": "WO-20260903-001",
  "executionStatus": "IN_PROGRESS"
}
```

차단 시 MES의 계획·버전은 변경되지 않습니다. 승인 대기 저장이나 자동 승인은 수행하지 않습니다. ERP에서 허용 가능한 계획으로 다시 변경하면 새 버전으로 전송할 수 있습니다.

## 실행 및 Swagger

기존 개인 `application.yaml`은 변경하지 않습니다. 각 애플리케이션의 MSSQL 접속정보를 로컬 설정 또는 환경변수로 준비하고, ERP의 `MES_API_BASE_URL`이 MES 주소를 가리키는지 확인합니다. 비밀정보를 추가로 커밋하지 마세요.

각 애플리케이션 디렉터리에서 Java 17로 실행:

```bash
bash gradlew bootRun
```

Windows에서는 `gradlew.bat bootRun`을 사용할 수 있습니다.

- [ERP Swagger UI](http://localhost:8080/swagger-ui/index.html)
- [MES Swagger UI](http://localhost:8081/swagger-ui/index.html)
- OpenAPI JSON: 각 서버의 `/v3/api-docs`

요청·응답 예시, MES 네 가지 결과, 주요 상태값 및 400·404·409 응답은 OpenAPI에 정의되어 있습니다. 두 애플리케이션의 동기화 응답 계약이 함께 변경되므로 같은 브랜치 버전을 사용하세요.

## 테스트

저장소 루트에서 각각 실행:

```bash
(cd erp && bash gradlew test --no-daemon)
(cd mes && bash gradlew test --no-daemon)
```

`test` 프로파일은 운영 MSSQL 설정 대신 애플리케이션별 H2를 사용합니다. ERP API 테스트는 실제 Controller·Service·Repository·RestClient를 사용하고, 외부 MES 응답만 MockRestServiceServer로 대체합니다. MES API 테스트는 실제 Controller·Service·Repository의 저장 결과를 검사합니다. 두 서버를 띄우는 종단간 테스트와 실제 MSSQL 검증은 포함하지 않습니다.

- ERP: 생성·변경 JSON, 버전 증가, 무변경, 실패 저장, 재실패 오류 갱신, 동일 버전 재전송 성공, 과거 버전 응답·빈 응답 거부, 검증·404·409
- MES: 최초 반영, 최신 버전, 동일 버전 중복 요청, 과거 버전 역순 도착, 각 주요 필드 변경 차단과 전체 데이터 불변, 비주요 변경 허용, 상태 전이·종료 상태·원천 제한·중복 식별자
- OpenAPI: 각 문서 경로·요청/응답 예시·결과·주요 오류 계약 확인

GitHub Actions의 `Work order tests`에서 두 모듈을 검증하고 HTML/JUnit 보고서를 업로드합니다. 실행 결과는 해당 커밋의 CI와 PR 설명을 기준으로 확인합니다.

## 미구현 및 PoC 한계

- 동기화 이력 테이블·이벤트 로그·승인 워크플로·운영자 알림·AX 기능
- 실제 MSSQL 및 두 서버 간 네트워크를 포함한 종단간 검증
- 동시 변경·수신 경쟁에 대한 잠금/재시도, 자동 재전송·백오프, 인증·인가
- 기존처럼 DB 트랜잭션 안에서 동기 HTTP 전송하므로 PENDING은 별도 커밋되지 않습니다. MES 성공 뒤 ERP DB 커밋 실패까지 원자적으로 보장하지 않으며 분산 트랜잭션·outbox는 구현하지 않았습니다.
- 동일 버전의 서로 다른 본문도 미반영합니다. 같은 버전은 같은 계획이라는 ERP 원본 관리 규칙을 전제로 합니다.
