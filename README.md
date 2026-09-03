# FactoryFlow ERP–MES PoC

Spring Boot와 MS SQL Server를 기반으로 ERP와 MES 간 작업지시 연동을 구현하는 개인 PoC 프로젝트입니다.

단순한 API 연결보다 분산된 업무 시스템 사이에서 발생할 수 있는 중복 요청, 순서 역전, 변경 충돌을 안정적으로 처리하는 것을 목표로 합니다.

## 핵심 목표

- ERP 작업지시 생성 및 변경
- ERP에서 MES로 작업지시 전송
- 작업지시 번호를 공통 식별자로 사용
- 버전을 이용한 최신 변경사항 판별
- 재전송 시 중복 생성을 방지하는 멱등 처리
- 생산 시작 후 변경 요청에 대한 승인·거부 처리
- 처리 결과와 변경 이력 조회

## 프로젝트 구성

| 서비스 | 역할 | 포트 |
|---|---|---:|
| ERP | 작업지시 생성·변경 및 MES 전송 | 8080 |
| MES | 작업지시 수신 및 생산 상태 관리 | 8081 |

## 기술 스택

- Java 17
- Spring Boot 4.0.8
- Gradle
- Spring Data JPA
- MS SQL Server

## 디렉터리 구조

```text
factoryflow-poc
├── erp
├── mes
└── docs
