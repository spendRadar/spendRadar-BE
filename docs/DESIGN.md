# 현재 설계와 구현 범위

## 목표

기록한 거래를 카테고리별 일별 시계열로 만들고 Chronos-2가 미래 변동지출을 예측한다.
Spring Boot가 실제 지출·예정 고정지출·예측값을 조합해 월 합계, 예산 비교 또는 구매 전후 잔액을 계산한다.
GPT-5.4 mini는 질문을 구조화하고 계산된 근거를 설명한다. 특정 달의 계절성만을 강제하지 않는다.

## 실행 흐름

1. ChatController가 문장·최근 대화·기록 기간을 받는다.
2. OpenAiClient가 Responses API의 JSON Schema Structured Outputs로 의도·기간·카테고리·금액을 해석한다.
3. ChatService가 필수값을 검증하고 부족하면 NEEDS_CLARIFICATION으로 질문한다.
4. ForecastService 또는 AdviceService가 DB에서 과거 지출과 활성 정기 설정을 가져온다.
5. ChronosClient → Python POST /forecast → Chronos2Pipeline.predict_df가 일별 예측을 반환한다.
6. Java가 응답 날짜·카테고리·누락·중복·금액 범위를 검증하고 비교값을 계산한다.
7. OpenAiClient가 근거를 한국어로 설명한다. 설명 생성만 실패하면 구조화 수치를 유지하고 TEMPLATE 설명을 반환한다.

외부 모델 호출 중에는 DB 트랜잭션을 유지하지 않는다. 키는 서버 설정에만 둔다.
원본 거래 설명은 모델 입력에 사용하지 않는다. LLM에 질문·대화 이력 및 집계/예측 숫자는 전달된다.
Structured Outputs는 JSON 구조를 제한할 뿐 질문 해석과 자연어 설명의 의미적 정확성을 보장하지 않는다.

## DB

기존 `ledger`, `recurring_income`, `fixed_expense` 세 테이블을 유지한다.

| 테이블 | 주요 컬럼 | 의미 |
|---|---|---|
| ledger | id, date, amount, category, type, expense_kind, description | 실제 거래 |
| recurring_income | id, name, amount, payment_day, active | 매월 예정 수입 |
| fixed_expense | id, name, amount, payment_day, active, category | 매월 예정 고정지출 |

금액은 원 단위 정수다. 총수입은 INCOME 거래의 합계로 계산하며 별도 총수입 컬럼을 만들지 않는다.
정기 설정은 실제 거래를 자동 생성하지 않는다. 실제 월 합계에 정기 설정을 다시 더하지 않는다.
수입의 expenseKind는 생략 가능하며 null로 저장한다. 지출은 FIXED/VARIABLE이 필수다.
현재 category는 enum 8종이며 통신비 전용 TELEPHONE은 아직 없다. 분류가 확장될 때 DB/API enum을 함께 변경해야 한다.

기존 H2 `expense_kind` NOT NULL 제약은 `db/demo-schema.sql`로 완화한다.
기존 행 보존·신규 테이블·반복 초기화를 테스트했다. MySQL은 H2 스크립트를 사용하지 않으며 구형 스키마는 별도 이전이 필요하다.
이번 AI 연결 작업에서 사용자 DB를 삭제하거나 테스트 거래를 삽입하지 않는다.

## 월 예측 규칙

- 이번 달 또는 다음 달만 지원한다. Clock은 Asia/Seoul이다.
- 최소 90일, 최대 5년의 일별 실제 기록을 사용한다. 90일은 호출 기준이며 정확도 보장이 아니다.
- historyComplete 확인 후에만 빈 날짜를 0으로 사용한다. 시스템이 누락을 자동 판별하는 기능은 없다.
- 모델 입력은 어제까지의 VARIABLE 지출이다. FIXED 지출은 시계열 예측에서 제외한다.
- 이번 달 = 어제까지 실제 고정/변동 지출 + 오늘부터 월말까지 변동 예측 + 미래 예정 고정지출.
- 오늘 입력한 거래도 이번 월 예측의 실제값에서는 제외한다. 오늘은 미완료 날짜로 취급한다.
- 다음 달 = 다음 달 전체 변동 예측 + 예정 고정지출. 모델은 오늘부터 다음 달 말까지 예측하고 해당 월만 반환한다.
- 선택 카테고리의 활성 고정지출만 합산하며 이미 지난 예정일을 다시 더하지 않는다.
- 과거 변동지출이 없는 항목은 AI를 호출하지 않고 NO_VARIABLE_HISTORY를 표시한다. 미래 변동 0은 명시적인 가정이다.
- 일별 예측 중앙값을 합산한다. 월 합계 분포의 중앙값·평균·신뢰구간으로 해석하면 안 된다.
- 음수 모델값은 0으로 보정하고 원 단위 반올림한다. 과도한 값·비정상값은 오류 처리한다.
- 비교 기준은 현재 달 이전의 완료된 3개월 평균이다. 기간이 부족하거나 평균이 0이면 해당 비교율은 null이다.
- 과거 같은 달의 실제 합계도 최대 4개 연도까지 제공한다. 모델이 그 달만 참조하는 것은 아니다.

`GET /api/ledgers/category-daily-expenses`는 시작일 포함/종료일 제외로 카테고리별 실제 변동지출·고정지출·지출 건수를 반환한다.
categories 생략 시 모든 enum을 반환한다. 이 집계 API 자체에는 예측이 없다.

## 구매 상담 경로

`POST /api/purchase-advice`는 기존 계약을 유지한다.
최근 180일 전체 변동지출로 내일부터 30일 예측을 받는다. 이 경로는 카테고리별 월 예측과 별개다.
현재 가용 잔액은 오늘까지의 거래가 반영되고 오늘 남은 거래가 없다고 가정한다.
향후 정기수입·고정지출을 반영해 구매 전후 최소 잔액과 일별 잔액을 계산한다.
오늘을 건너뛰는 예측은 Python이 중간 날짜도 예측한 뒤 요청 기간만 잘라 반환한다.
대화 경로는 180일 기록 확인을 요구한다. 기존 직접 구매 API에는 historyComplete 필드가 없으므로 호출자가 데이터 완전성을 확인해야 한다.
예측은 일 마감 기준이며 같은 날 입출금 순서·예측 오차·비상자금은 반영하지 않는다.

## 읽는 순서와 클래스 역할

| 순서 | 위치 | 역할 |
|---|---|---|
| 1 | AiLedgerApplication | Spring Boot 시작 및 AiSettings 등록 |
| 2 | common/LedgerTypes, ledger/LedgerContracts | 분류와 거래 DTO |
| 3 | ledger/Ledger → LedgerRepository → LedgerService → LedgerController | 저장 구조에서 CRUD·집계 요청까지 |
| 4 | recurring/MonthlyItem, RecurringIncome, FixedExpense, RecurringService | 정기 수입·고정지출 설정 |
| 5 | forecast/ForecastContracts → ForecastController → ForecastService | 월 예측 요청·응답·계산 |
| 6 | forecast/ChronosClient, ChronosExpenseForecaster | Python HTTP 호출과 기존 예측 인터페이스 어댑터 |
| 7 | forecast-server/app.py → engine.py | 입력 검증 및 실제 Chronos-2 실행 |
| 8 | chat/ChatContracts → ChatController → ChatService | 대화 계약·분기·예산 계산 |
| 9 | ai/OpenAiClient, resources/prompts | 구조화 질문 해석·근거 기반 설명 |
| 10 | ai/AiHttpClient, AiSettings | HTTP 처리와 환경 설정 |
| 11 | advice/AdviceService | 기존 구매 전후 현금 흐름 비교 |
| 12 | common/ApiErrors, WebConfig, TimeConfig | 오류·CORS·시간 설정 |
| 13 | forecast-server/backtest.py, 각 테스트 | 성능 평가 도구와 계약/계산 검증 |

## 확장 지점과 남은 작업

로그인/사용자별 데이터 격리, 대화·예측 저장, 월별 입력 자료 지원, 비정기 고정지출, 예측 불확실성 구간, 캐시, 프론트는 미구현이다.
예측 모델을 바꿀 때는 Python 엔진과 ChronosClient 계약을 유지하거나 별도 어댑터를 추가한다.
프론트는 구조화 forecast/budget/purchase 필드를 표·그래프로 사용하고 answer는 설명으로 표시한다.

실제 데이터로 여러 시점의 백테스트를 수행해 단순 7일 반복 기준보다 유용한지 먼저 확인해야 한다.
검증 도구는 최신 구간을 숨기는 단일 holdout 방식이다. 자동 승인 기준이나 성능 검증 완료 상태를 제공하지 않는다.
기본 설정은 단일 사용자 로컬 개발용이다.
