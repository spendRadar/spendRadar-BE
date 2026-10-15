# 3년치 합성 시연 데이터

실제 금융 기록이 아닌, 기능 시연과 모델 연동 확인을 위한 재현 가능한 합성 데이터입니다.
모델이 의도한 계절성을 실제로 잡아낼지는 예측·백테스트 결과로 확인해야 합니다.
합성 자료에서 나온 성능으로 실제 사용자 데이터의 정확도를 주장하면 안 됩니다.

## 준비된 내용

- 기간: 2023-10-15부터 2026-10-14까지, 1,096일. 오늘의 미완료 거래는 제외합니다.
- 거래: 3,188건. 실제 거래 설명에는 모두 `[SYNTHETIC]` 표시가 있습니다.
- 변동지출: FOOD, TRANSPORT, SHOPPING, HOUSING, HEALTH, LEISURE, OTHER 7종.
- 월급 실제 거래 및 정기수입 설정, 월세·통신비·구독 실제 고정지출 및 정기 설정.
- 통신비는 기존 분류에 맞춰 OTHER를 사용합니다.
- 무지출일을 포함한 일별 시계열 7,672행. 0원 거래를 DB에 넣지 않고 CSV에만 0을 기록합니다.

## 넣어둔 패턴

| 특징 | 생성 규칙 |
|---|---|
| 3월 식비 증가 | FOOD의 기본 지출에 1.65배 계절 요인 |
| 여름 여가비 증가 | 7월 1.75배, 8월 1.9배 |
| 가을 쇼핑 증가 | 10월 1.5배, 11월 1.65배 |
| 요일 변화 | 주말 식비·여가·쇼핑 증가, 교통비 감소 |
| 장기 변화 | 연 8% 정도의 완만한 기본 지출 증가 |
| 최근 변화 | 마지막 180일 식비 추가 증가, 교통비 감소 |
| 불규칙성 | 월별·일별 잡음, 무지출일, 한 번의 큰 건강 지출 |

배율은 생성 규칙이며 관측된 월 합계 증가율과 같지는 않습니다.
특정 날짜 지출이 항상 같은 값이 되는 자료가 아니라 날짜·카테고리에 따라 변동합니다.
수입은 완만하게 증가하며 월세 등 고정지출은 변동지출 예측에서 제외됩니다.

## 사용하는 방법

일반 demo DB `data/ledger.mv.db`와 별개인 **`data/synthetic-ledger.mv.db`**에 로딩합니다.
기존 거래를 지우거나 합성 거래와 섞지 않습니다. 일반 DB로 돌아가려면 서버를 종료한 뒤 평소대로 실행하세요.

프로젝트 루트에서:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=synthetic"
```

같은 포트의 기존 Spring Boot 서버가 실행 중이라면 먼저 종료하거나 다른 포트를 사용하세요.
이 프로필은 시작할 때 생성된 `data/synthetic/seed.sql`을 읽습니다.
같은 자료로 반복 실행해도 설명/설정 이름을 기준으로 중복 삽입을 방지합니다.
SQL은 시연 DB 전용입니다. 개인 DB에 직접 실행하지 마세요.
합성 거래의 설명을 수정한 뒤 다시 시작하면 원래 설명의 행이 다시 삽입될 수 있습니다.

팀원이 새로 받은 환경에는 data 폴더가 Git에서 제외되어 있으므로 먼저 생성합니다.
날짜가 바뀌었을 때도 아래 생성기로 최신 3년을 만들 수 있습니다.

```powershell
python scripts/generate_synthetic_data.py
```

현재 자료를 동일하게 재생성하려면:

```powershell
python scripts/generate_synthetic_data.py --end 2026-10-15 --seed 20261015
```

생성 파일을 바꾸어도 이미 DB에 넣은 행은 갱신/삭제하지 않습니다.
다른 날짜·seed로 새 시연을 시작할 때는 별도 DB 경로를 지정하세요.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=synthetic --spring.datasource.url=jdbc:h2:file:./data/synthetic-v2"
```

## 예측과 대화 요청

Python Chronos 서버도 실행한 후 다음 요청을 보냅니다.
월 예측은 OpenAI 키 없이도 가능하며, 대화는 OpenAI 키와 크레딧이 필요합니다.
아래 month는 현재 서버의 달에 맞춰야 합니다.

```http
POST http://localhost:8080/api/forecasts/monthly
Content-Type: application/json

{
  "month": "2026-10",
  "historyStart": "2023-10-15",
  "historyComplete": true,
  "categories": ["FOOD", "SHOPPING", "LEISURE"]
}
```

```http
POST http://localhost:8080/api/chat
Content-Type: application/json

{
  "message": "이번 달 어떤 카테고리 지출을 조심해야 할까? 최근 평균과 비교해줘.",
  "history": [],
  "historyStart": "2023-10-15",
  "historyComplete": true
}
```

지금은 10월이므로 현재 API에서 3월 미래 예측을 직접 요청할 수는 없습니다.
3월 식비 특징은 과거 월별 집계로 확인할 수 있습니다. 백테스트 도구는 CSV를 3월 말에서 잘라
마지막 31일을 숨기는 방식으로 3월 예측을 평가할 수 있습니다.
이때 3월 실제값을 학습에 넣지 않아야 합니다. 월별 특성이 LLM 답변에 반드시 나와야 한다고 강제하지 않습니다.

## 생성 파일

| 파일 (data/synthetic/) | 용도 |
|---|---|
| transactions.json | 기존 거래 POST 요청과 같은 필드의 합성 거래 |
| variable-daily.csv | Chronos 입력·백테스트용 id/date/amount, 고정지출 제외 |
| monthly-summary.csv | 카테고리별 월 지출 점검. 실제 고정+변동 지출이며 첫/마지막 월은 부분 월 |
| recurring.json | 정기수입·고정지출 설정 |
| metadata.json | 기간·난수 seed·패턴 설명·합성 표시 |
| seed.sql | synthetic H2 프로필의 중복 방지 초기 입력 |

백테스트 실행:

```powershell
cd forecast-server
$env:HF_HOME = Join-Path (Get-Location).Path '..\data\huggingface'
.\.venv\Scripts\python.exe backtest.py ..\data\synthetic\variable-daily.csv --days 30 --output ..\data\synthetic\backtest-report.json
```

이 평가는 마지막 30일 기준입니다. 특정 계절을 평가하려면 다른 날짜에서 자른 CSV로 반복하세요.
프로젝트 연결 계약 테스트 통과와 실제 모델의 유용한 예측은 다른 확인 단계입니다.

## 이번 데이터로 확인한 결과

- 실제 REST API에서 거래 3,188건과 날짜×enum 8종 집계 8,768행을 확인했습니다.
- 변동지출 CSV 7,672행의 금액 합계와 DB 집계가 일치합니다. SALARY는 지출 시계열 CSV에서 제외됩니다.
- 서버 재시작 후에도 거래가 3,188건으로 유지되어 동일 데이터 중복 방지를 확인했습니다.
- 2024/2025/2026년 3월 식비는 각 연도의 다른 완료 월 평균 대비 약 1.64/1.85/1.66배입니다.
  2026년 비교는 현재까지의 완료 월 기준입니다. 계절 요인 외 추세·잡음도 반영된 결과입니다.
- 실제 Chronos-2로 2026-09-15부터 30일을 숨겨 검증했습니다. 식비 WAPE는 약 29.8%, 주간 반복 기준은 약 38.2%였습니다.
- 쇼핑·건강·여가·기타처럼 무지출일이 많은 항목은 Chronos WAPE가 약 100% 이상입니다.
  일별 중앙값이 드문 큰 지출을 낮게 예측할 수 있어, 일별 중앙값 합산의 한계가 드러났습니다.
  모델 연결은 작동하지만 모든 카테고리의 유용한 월 예측이 검증된 상태는 아닙니다.
- 전체 검증 수치는 `data/synthetic/backtest-report.json`에 있습니다.

향후 개선은 주별 집계·월 합계 오차 검증·예측 분포 집계 등을 실제 결과와 비교하며 진행할 수 있습니다.

Spring Boot → Python HTTP 서버 → 실제 Chronos-2의 7개 카테고리 월 예측도 확인했습니다.
당시 2026-10 월 예측 응답은 `data/synthetic/monthly-forecast-example.json`에 저장했습니다.
OpenAI 유료 호출은 이번 데이터 준비·검증에서 수행하지 않았습니다. 검증용 서버는 종료했습니다.
