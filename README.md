# FactoryFlow ERP–MES PoC

ERP가 작업지시 원본을 관리하고 MES가 생산 실행을 관리하는 독립 Spring Boot 애플리케이션입니다. 계획 변경, 통신 실패 재전송, 역순 수신, 생산 시작 후 변경 제한을 다룹니다.

- Java 17 / Spring Boot 4.0.8 / Spring Data JPA / RestClient / Validation
- 운영 DB: MSSQL의 독립 ERP·MES 데이터베이스. 테스트 DB: 애플리케이션별 H2
- ERP: 8080 / MES: 8081

## 구현 범위

- ERP 생성·계획 변경·FAILED 재전송 API, 전송 상태와 최근 오류 저장
- MES의 `sourceSystem + externalId` 식별 및 버전 비교, 처리 결과 DTO
- MES 실행 상태 변경과 생산 시작 후 주요 계획 변경 차단
- ERP 전송 시도·MES 수신 처리 이력 저장, eventId 추적 및 최신순 페이징 조회
- 서비스 단위 테스트, MockMvc·H2 API 통합 테스트, ERP RestClient 요청·응답 검증
- Swagger/OpenAPI 요청·응답 예시와 오류 계약

작업지시 번호는 별도의 유일성 제약입니다. `eventId`는 ERP 전송 시도와 MES 수신 이력을 연결하는 추적 ID이며, 반영 여부는 `sourceSystem + externalId`로 식별한 작업지시의 `sourceVersion`을 비교하여 판단합니다. 동일·과거 버전은 이력만 추가하고 작업지시 본문은 변경하지 않습니다.

## API

| 시스템 | 메서드·경로 | 동작 |
|---|---|---|
| ERP | `POST /api/work-orders` | 버전 1 생성 후 MES 전송 |
| ERP | `PUT /api/work-orders/{id}` | 다섯 계획 필드 변경, 실제 변경 시 버전 +1 |
| ERP | `POST /api/work-orders/{id}/resend` | FAILED의 현재 계획·현재 버전 재전송, 본문 없음 |
| MES | `POST /api/work-orders/sync` | 원천 식별·버전 비교·생산 상태 검증 후 반영 |
| MES | `PATCH /api/work-orders/{id}/execution-status` | 생산 실행 상태 변경 |
| ERP | `GET /api/work-orders/{id}/sync-attempts` | ERP 내부 ID별 전송 시도 이력 |
| ERP | `GET /api/sync-attempts/events/{eventId}` | 추적 ID별 ERP 전송 시도 이력 |
| MES | `GET /api/work-orders/{id}/sync-history` | MES 내부 ID별 수신 이력 |
| MES | `GET /api/sync-history/events/{eventId}` | 추적 ID별 MES 수신 이력 |

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

## 동기화 이력과 추적

```text
ERP WorkOrder
→ MesSyncAttempt (PENDING)
→ eventId를 포함한 HTTP 요청
→ MES WorkOrderSyncHistory (수신 시각·원천 정보 수집)
→ 버전·생산 상태 판정
→ 처리 결과 및 수신 이력 저장, 결과 반환
→ ERP 이력 및 최종 상태 갱신
```

ERP 서비스가 `WorkOrderSyncRequest.from(workOrder)`를 호출해 요청을 만들고, 요청의 eventId·eventType·version으로 이력을 저장한 다음 `MesWorkOrderClient.sync(request)`에 전달합니다. MES 수신 시각은 서비스 진입 시 수집하며, 이력 행은 판정 결과가 확정된 뒤 저장합니다.

| 값 | 의미 | 변경·재전송 시 동작 |
|---|---|---|
| ERP ID / MES ID | 각 DB의 작업지시 내부 PK | 서로 같다고 가정하지 않음 |
| sourceSystem + externalId | MES에서 원천 작업지시 식별 | externalId는 현재 ERP 내부 ID |
| version / sourceVersion | 계획의 업무 버전·MES에 적용된 버전 | 계획 변경은 증가, 실패 재전송은 유지 |
| eventId | ERP 전송 시도와 MES 수신 기록을 연결하는 UUID | 생성·변경·재전송마다 새로 발급 |
| eventType | 해당 버전의 계획 이벤트 종류 | 버전 1은 WORK_ORDER_CREATED, 이후는 WORK_ORDER_UPDATED |

`eventId`에는 양쪽 모두 **유일성 제약을 두지 않습니다.** 같은 HTTP 요청이 반복 수신되면 MES에는 같은 eventId로 여러 행이 생깁니다. 동일 버전 재수신은 `IGNORED_SAME_VERSION`이며, eventId가 같다는 이유로 수신 기록을 생략하지 않습니다. 각 이력 행에는 고유한 내부 이력 ID가 있습니다. 원천·작업지시 및 시각, eventId에 조회 인덱스를 둡니다.

### 이력 모델과 처리 결과

- ERP `MesSyncAttempt`: 작업지시 ID·번호, eventId·eventType, 전송 버전, PENDING/SUCCESS/FAILED, MES 결과, 오류 사유, 요청·완료 시각.
- MES `WorkOrderSyncHistory`: MES 내부 ID(없으면 null), eventId, sourceSystem·externalId, 요청 작업지시 번호, 수신 버전·처리 전 버전·적용 버전, 결과·메시지, 수신·완료 시각.
- 현재 `WorkOrder.mesSyncStatus`와 `mesSyncError`는 최신 상태로 유지됩니다. 재전송 성공 시 최신 오류는 제거되지만 이전 FAILED 이력은 남습니다.
- ERP는 정상 결과 CREATED/UPDATED/IGNORED_SAME_VERSION을 응답 계약 검증 후 SUCCESS로 기록합니다. 정상적인 IGNORED_OLD_VERSION 응답은 MES 결과를 남기되 ERP 상태는 FAILED입니다. 번호·수신 버전이 불일치하거나 적용 버전이 OLD_VERSION 규칙과 맞지 않는 응답은 계약 오류이며 결과를 null로 둡니다.
- HTTP 오류(409 포함)·빈 응답·계약 불일치·통신 실패는 ERP FAILED 이력과 오류 사유로 남습니다. 오류/메시지 필드는 최대 1,000자입니다.

| MES 이력 결과 | 의미 |
|---|---|
| CREATED | 신규 작업지시 생성 |
| UPDATED | 더 높은 계획 버전 반영 |
| IGNORED_SAME_VERSION | 동일 버전 미반영 |
| IGNORED_OLD_VERSION | 과거 버전 미반영 |
| REJECTED_PRODUCTION_STARTED | 생산 시작 이후 또는 취소 상태의 주요 계획 변경 거부 |
| REJECTED_IDENTITY_CONFLICT | 번호·품목 변경 또는 다른 원천 작업지시의 번호 중복 |
| REJECTED_UNSUPPORTED_SOURCE | 서비스에 도달한 ERP 외 원천 요청 거부 |

### 트랜잭션 보존 범위

ERP는 기존 트랜잭션 내 동기 HTTP 구조를 유지합니다. 전송 전에 PENDING 이력을 INSERT하고 통신/응답 예외를 잡아 FAILED로 완료한 뒤, 작업지시 최종 상태와 이력을 함께 커밋합니다. PENDING은 별도 커밋되지 않으므로 외부 조회에서 전송 중 상태를 반드시 볼 수 있는 것은 아닙니다.

MES 정상 처리와 CREATED/UPDATED/IGNORED 이력은 같은 트랜잭션에서 커밋됩니다. 업무 거부는 계획을 변경하기 전에 검증하며, 별도 Spring Bean인 `WorkOrderSyncHistoryWriter.recordRejection`의 `REQUIRES_NEW` 트랜잭션으로 이력을 저장한 뒤 기존 예외를 다시 던집니다. 따라서 409를 반환하며 원래 트랜잭션이 롤백되어도 거부 이력은 남고, MES 계획값과 sourceVersion은 유지됩니다.

이력에는 JPA 연관관계/FK 대신 스칼라 작업지시 ID와 원천 식별자를 저장합니다. 이는 거부 이력 저장 시 작업지시 잠금 의존을 피하고, 아직 MES 작업지시가 없는 신규 요청 거부도 저장하기 위한 판단입니다. 작업지시 생성 전 거부 이력은 mesWorkOrderId가 null이며, 기존 작업지시가 없으면 previousVersion/appliedVersion도 null입니다.

프로세스 중단, ERP DB 커밋 실패, 이력 DB 자체 장애까지 이력을 보장하지 않습니다. MES 반영 후 ERP 커밋 실패를 원자적으로 해결하지도 않습니다. 동시 생성 경쟁으로 발생하는 DB 유일성 오류 등 예기치 않은 저장 실패의 전수 기록, 분산 트랜잭션 및 장애 복구는 이번 범위 밖입니다. Controller 진입 전 Validation/JSON 거부는 영속 이력 대상이 아닙니다.

### 이력 조회 사용법

4개 GET API는 모두 `page=0&size=20`이 기본이며, page는 0 이상, size는 1~100입니다. 요청/수신 시각 내림차순, 동일 시각은 이력 ID 내림차순으로 정렬합니다. 사용자 지정 정렬은 제공하지 않습니다.

```http
GET /api/work-orders/7/sync-attempts?page=0&size=20
GET /api/sync-attempts/events/11111111-1111-1111-1111-111111111111
GET /api/work-orders/11/sync-history?page=0&size=20
GET /api/sync-history/events/11111111-1111-1111-1111-111111111111
```

공통 페이지 응답은 다음 구조이며 content는 각각 전송 또는 수신 이력 DTO입니다. 엔티티 전체나 작업지시를 중첩하지 않습니다.

```json
{
  "content": [{
    "id": 1,
    "erpWorkOrderId": 7,
    "workOrderNo": "WO-001",
    "eventId": "11111111-1111-1111-1111-111111111111",
    "eventType": "WORK_ORDER_CREATED",
    "version": 1,
    "status": "SUCCESS",
    "mesResult": "CREATED",
    "errorReason": null,
    "requestedAt": "2026-09-09T12:00:00",
    "completedAt": "2026-09-09T12:00:01"
  }],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

MES DTO는 `mesWorkOrderId`와 `externalId`를 명시적으로 구분하고 `receivedVersion`, `previousVersion`, `appliedVersion`을 제공합니다. MES 작업지시별 조회는 해당 내부 ID의 `sourceSystem + externalId`로 이력을 찾으므로, 동일 원천 작업지시가 생성되기 전에 발생한 거부 기록도 포함됩니다. eventId 조회는 작업지시 참조가 없어도 가능합니다. 양쪽 eventId 조회 모두 반복 기록에 대비한 페이지 응답입니다.

없는 작업지시/eventId는 404, 잘못된 UUID·페이징 값은 400입니다. 존재하는 작업지시에 이력이 없거나 마지막 페이지를 넘겨 요청하면 200과 빈 content를 반환합니다. 이벤트 존재 여부는 요청 페이지와 별도로 확인하므로, 기존 eventId의 범위 밖 페이지는 404가 아닙니다.

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

- 실제 MSSQL과 두 서버를 동시에 실행하는 E2E 검증
- 승인 대기 및 승인·거부 워크플로
- 자동 재시도와 백오프
- Outbox 및 Kafka/RabbitMQ 같은 메시지 브로커
- 동시성 제어 및 JPA 낙관적 잠금
- 인증·인가
- 운영 모니터링 및 알림
- AX 비가동 원인코드 추천

동일 버전은 동일 계획이라는 ERP 규칙을 전제로 합니다. 같은 버전으로 다른 본문을 전송해도 MES는 기존처럼 미반영합니다. DB 트랜잭션과 HTTP 사이의 원자성, 영속화 장애 및 프로세스 중단 시 복구는 보장하지 않습니다.
