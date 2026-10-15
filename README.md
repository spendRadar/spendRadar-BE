# AI 가계부 — CRUD + Chronos-2 + 대화 API

가계부 · 정기수입 · 고정지출 CRUD가 작동하는 Spring Boot 프로젝트입니다.
백엔드 REST API 프로젝트이며, 프론트는 별도 프로젝트에서 연결합니다.
카테고리별 월 지출 예측, 예산 비교, 구매 상담과 대화 API를 구현했습니다.
GPT-5.4 mini는 질문 해석·설명을, Python Chronos-2는 변동지출 예측을 담당합니다.
설정이 없으면 CRUD는 작동하며 AI 요청은 설정 필요 상태를 반환합니다.

**AI 실행은 [설정 안내](docs/AI_SETUP.md)를 먼저 보세요.** API 키 외에 Python 서버 실행이 필요합니다.

3년치 시연 데이터는 [합성 데이터 안내](docs/SYNTHETIC_DATA.md)에 따라 `synthetic` 프로필로 실행합니다. 개인 DB와 분리됩니다.

- [최종 개발 프롬프트](docs/DEVELOPMENT_PROMPT.md)
- [현재 설계와 구현 범위](docs/DESIGN.md)
- [GitHub 업로드 안내](docs/GITHUB_UPLOAD.md)
- [API 요청 예시](docs/api.http)

## 가장 빠른 실행 (Windows / VS Code)

1. ZIP으로 받았다면 압축을 풉니다.
2. VS Code의 파일 → 폴더 열기에서 **build.gradle이 들어 있는 ai-ledger 폴더**를 엽니다.
3. JDK 21이 설치되어 있어야 합니다. 터미널에서 `java -version`으로 확인합니다.
4. 터미널 → 새 터미널을 열고 아래 명령을 실행합니다.

```powershell
.\gradlew.bat bootRun
```

5. 브라우저나 API 클라이언트에서 `http://localhost:8080/api/ledgers?month=2026-10`을 조회합니다.
6. 등록·수정·삭제는 아래 API 명세에 따라 API 클라이언트 또는 팀원 프론트에서 호출합니다.
7. 종료하려면 터미널에서 Ctrl+C를 누릅니다.

내장 화면은 제거했으므로 루트 주소 `/`에는 가계부 화면이 제공되지 않습니다.

처음 실행할 때 인터넷 연결이 필요하며 Gradle과 라이브러리를 내려받습니다.
별도 Gradle 설치는 필요 없습니다. 기본 포트가 사용 중이면 다음을 사용합니다.

```powershell
.\gradlew.bat bootRun --args="--server.port=8081"
```

이때 접속 주소는 http://localhost:8081 입니다.

## 기본 데모 DB

기본 프로필 demo는 파일 기반 H2 DB를 사용합니다. MySQL 설치 없이 실행됩니다.
프로젝트 안의 data 폴더에 저장되므로 서버를 재시작해도 기록이 유지됩니다.
초기 데이터는 비어 있습니다. API를 통해 원하는 시연 데이터를 등록하세요.
같은 DB를 쓰는 서버를 동시에 두 개 실행하지 마세요.

## MySQL로 변경

MySQL에서 개발용 데이터베이스 `ai_ledger`를 생성하고 해당 DB에 권한이 있는
개발용 계정을 준비합니다. 아래 값은 본인의 로컬 설정으로 바꾸세요.

```sql
CREATE DATABASE ai_ledger CHARACTER SET utf8mb4;
```

VS Code PowerShell 터미널에서:

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/ai_ledger?serverTimezone=Asia/Seoul&characterEncoding=UTF-8'
$env:DB_USERNAME='본인_DB_계정'
$env:DB_PASSWORD='본인_DB_비밀번호'
.\gradlew.bat bootRun --args="--spring.profiles.active=mysql"
```

테이블은 JPA가 생성합니다. demo와 mysql은 서로 다른 DB이며 데이터가 자동 이관되지 않습니다.
`ddl-auto=update`는 개발 편의를 위한 설정입니다. MySQL 환경 실행은 별도 확인이 필요합니다.

## 구현 범위와 규칙

- Java 21 / Spring Boot 4.1.1 / Gradle Wrapper / JPA / Validation
- 가계부 CRUD, 월별 조회 및 실제 거래의 수입·지출·차액 집계
- 정기수입 CRUD, 고정지출 CRUD (매월 반복만 지원)
- 금액: 1원 이상 정수, 최대 1조 원. DB에는 정수형으로 저장
- 매월 예정일: 1~28일. 말일 처리는 다음 단계로 유보
- 고정지출·정기수입 설정은 거래를 자동 생성하지 않음
- 정기 항목을 실제 거래 합계에 합산하지 않음
- FIXED 거래는 변동지출 합계에서 제외
- API의 balance는 기록한 수입과 지출의 차액이며 실제 계좌 잔액이 아님
- 단일 사용자 로컬 데모: 인증과 User 테이블은 아직 없음
- 실제 모델 호출 어댑터와 LLM 설명 구현. 로그인·대화 저장·자동 시연 데이터는 미구현
- 기본 서버는 127.0.0.1에만 바인딩. 다중 사용자 공개 서비스로 배포하기 전 인증/사용자별 조회 구현 필요

## 프론트 팀원 연결

API 기본 주소: http://localhost:8080/api
내장 프론트는 제거했습니다. 팀원 프론트에서 위 API 주소에 연결합니다.
별도 프론트 서버에서 상대 경로 `/api`를 사용할 경우 개발 서버 프록시를 설정하세요.
기본 허용 Origin: http://localhost:5173, http://localhost:3000
다른 주소라면 `APP_CORS_ALLOWED_ORIGINS` 환경변수에 쉼표로 구분해 설정합니다.

## API

| 메서드 | 경로 | 동작 |
|---|---|---|
| GET | /api/ledgers?month=2026-10 | 해당 월 거래 목록, 날짜 및 ID 내림차순 |
| GET | /api/ledgers/summary?month=2026-10 | income, expense, balance, fixedExpense, variableExpense |
| GET | /api/ledgers/{id} | 한 건 조회 |
| POST | /api/ledgers | 등록 |
| PUT | /api/ledgers/{id} | 전체 필드 수정 |
| DELETE | /api/ledgers/{id} | 삭제 |

`/api/recurring-incomes`, `/api/fixed-expenses`도 GET 목록, GET /{id}, POST, PUT /{id}, DELETE /{id}를 제공합니다. 월 필터는 가계부에만 적용됩니다.
등록 201, 조회/수정 200, 삭제 204, 잘못된 입력 400, 없는 ID 404를 반환합니다.

### 가계부 POST / PUT 본문

```json
{"date":"2026-10-09","amount":12000,"category":"FOOD","type":"EXPENSE","expenseKind":"VARIABLE","description":"점심"}
```

- type: INCOME / EXPENSE
- expenseKind: FIXED / VARIABLE (지출 필수, 수입은 생략 가능하며 응답은 null)
- category: FOOD / TRANSPORT / SHOPPING / HOUSING / HEALTH / LEISURE / SALARY / OTHER
- description: 선택, 최대 200자

### 정기수입 POST / PUT 본문

```json
{"name":"월급","amount":2500000,"paymentDay":25,"active":true}
```

### 고정지출 POST / PUT 본문

```json
{"name":"월세","amount":500000,"paymentDay":1,"active":true,"category":"HOUSING"}
```

## 코드 읽는 순서

- common/LedgerTypes.java: 공통 분류 enum
- ledger/LedgerContracts.java, recurring/RecurringContracts.java: 기능별 요청·응답 DTO
- ledger/LedgerController.java → LedgerService.java → LedgerRepository.java: 가계부 처리
- recurring/: 정기수입·고정지출 처리. MonthlyItem은 공통 컬럼을 공유하는 JPA 상위 클래스
- common/ApiErrors.java: 입력 오류 응답
- common/WebConfig.java: 프론트 개발 서버 CORS
- resources/application-*.properties: DB별 설정

## 테스트와 빌드

```powershell
.\gradlew.bat test bootJar
```

CRUD 통합 테스트는 별도 메모리 DB와 임의 포트에서 실행되며 데모 거래 데이터를 변경하지 않습니다.
기본 컨텍스트 테스트도 별도 메모리 DB를 사용합니다.
CRUD 전체 흐름, 월 경계, 고정/변동 분리, 정기수입 중복 집계 방지, 잘못된 입력, 없는 ID를 확인합니다.
빌드 후 build/libs 안의 실행 JAR를 `java -jar 파일명.jar`로 실행할 수도 있습니다.

## AI 기능

| 메서드 | 경로 | 동작 |
|---|---|---|
| POST | /api/forecasts/monthly | 이번 달/다음 달 카테고리별 지출 예측 |
| POST | /api/chat | 자연어 질문 해석 → 예측/예산/구매 비교 → 설명 |
| POST | /api/purchase-advice | 최근 180일 전체 변동지출 기반, 내일부터 30일 잔액 비교 |
| GET | /api/ledgers/daily-expenses | start 포함/end 제외, 최대 1년 일별 변동지출 |
| GET | /api/ledgers/category-daily-expenses | 최대 5년, 카테고리별 실제 고정/변동 지출 |

사용 예시·설정·프론트 응답 계약은 [AI 설정 안내](docs/AI_SETUP.md)를 참고하세요.
원본 거래 설명은 시계열 모델로 보내지 않습니다. OpenAI에는 질문/대화 이력과 설명에 필요한 예측·집계 결과가 전달됩니다.
대화와 예측은 DB에 저장하지 않습니다. 프론트가 최근 대화 이력을 다시 전달합니다.

## 검증 범위

Java 테스트는 CRUD/H2 호환성과 실제 HTTP 형식의 가짜 모델 서버를 이용한 연결 계약, 날짜 경계, 비교 계산과 오류 처리를 검증합니다.
Python 테스트는 가짜 모델 객체로 입력 검증·응답 변환·백테스트의 미래 데이터 누수를 확인합니다.
실제 OpenAI 호출은 API 키 준비 후 확인해야 합니다. 실제 지출 정확도와 MySQL 환경은 미검증입니다.

Python 테스트:

```powershell
cd forecast-server
.\.venv\Scripts\python.exe -m pytest -q
```
