# AI 실행 및 프론트 연결

## 1. 설정 파일

현재 작업 폴더에는 개인 설정 파일을 만들어 두었습니다. 바로 키를 채우면 됩니다.
새로 복제한 환경에서는 프로젝트 루트에서 최초 한 번 실행합니다. 기존 개인 설정 파일은 덮어쓰지 마세요.

```powershell
Copy-Item application-local.properties.example application-local.properties
```

`application-local.properties`에서 다음 값만 채웁니다.

```properties
app.ai.openai-key=발급받은_API_키
```

모델 기본값은 `gpt-5.4-mini`, Python 주소는 `http://127.0.0.1:8001`입니다.
개인 설정 파일은 Git에서 제외했습니다. 프론트에는 키를 넣지 않습니다.
파일 없이 `OPENAI_API_KEY`, `CHRONOS_URL` 환경변수만 지정해도 됩니다.
개인 파일의 명시적인 값이 환경변수용 기본 설정을 덮어쓰므로 두 방식을 혼용하지 않는 편이 좋습니다.
OpenAI API 계정에 해당 모델 사용 권한과 결제 설정이 필요합니다. ChatGPT 구독과 별개입니다.

## 2. Python 서버 준비

Python 3.12 기준입니다. 프로젝트 루트에서 별도 터미널을 엽니다.

```powershell
python -m venv forecast-server/.venv
.\forecast-server\.venv\Scripts\python.exe -m pip install -r forecast-server/requirements.txt
cd forecast-server
$env:HF_HOME = Join-Path (Get-Location).Path '..\data\huggingface'
.\.venv\Scripts\python.exe warmup.py
.\.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8001
```

이미 생성된 `.venv`가 있으면 설치부터 진행해도 됩니다. 이 작업 환경에서는 기존 torch를 재사용하는 가상환경으로 설치했습니다.
Chronos-2는 공개 모델을 로컬 CPU에서 실행하므로 유료 AI API 키는 필요 없습니다.
최초 실행에는 모델 다운로드와 메모리가 필요합니다. `warmup.py`는 합성 데이터만 사용하며 DB에 데이터를 넣지 않습니다.
이 명령은 다운로드 캐시를 준비합니다. 서버 프로세스의 모델 로딩은 첫 예측 요청 때 별도로 발생합니다.
기본 120초보다 오래 걸리면 개인 설정에서 `app.ai.timeout-seconds=300` 등으로 늘린 후 재시작하세요.
GPU 환경에서는 해당 GPU에 맞는 torch 설치 후 `CHRONOS_DEVICE=cuda`로 실행할 수 있습니다. GPU는 미검증입니다.
`GET http://127.0.0.1:8001/health`의 `modelLoaded`는 해당 서버 프로세스에서 가중치를 로드했는지 나타냅니다.

Python 서버를 외부로 연결할 경우 `CHRONOS_API_TOKEN`과 Java의 `app.ai.chronos-token`을 동일하게 설정합니다.
기본 로컬 데모에는 사용자 인증이 없습니다.

## 3. Spring Boot 실행

프로젝트 루트의 다른 터미널에서 실행합니다.

```powershell
.\gradlew.bat bootRun
```

H2 파일 DB를 그대로 사용합니다. 새 테이블이나 MySQL 전환은 필요 없습니다.
API 키를 비워두어도 서버와 기존 CRUD는 실행됩니다.

## 4. 프론트 요청

시연용 3년 자료는 [합성 데이터 안내](SYNTHETIC_DATA.md)의 synthetic 프로필로 사용할 수 있습니다.
이 자료의 historyStart는 2023-10-15이며 일반 개인 DB와 분리됩니다.

아래 날짜는 예시입니다. 실제 서버의 현재 달에 맞춰 변경하세요.
시계열 입력은 현재 **일별 거래 기록** 기준입니다. 월별 합계만 있는 자료를 하루 거래인 것처럼 넣지 마세요.
최소 90일은 입력 검증 기준일 뿐 정확도를 보장하지 않습니다. 연간 패턴 평가에는 여러 해의 실제 기록이 필요합니다.
`historyStart`부터 어제까지 전체 기록을 넣은 뒤에만 `historyComplete=true`로 보냅니다.
미입력 날짜를 무지출 0원으로 간주해도 되는지 프론트가 사용자에게 확인해야 합니다.

### 직접 월 예측

`POST /api/forecasts/monthly`

```json
{
  "month": "2026-10",
  "historyStart": "2023-01-01",
  "historyComplete": true,
  "categories": ["FOOD", "SHOPPING"]
}
```

categories 생략/빈 배열이면 기록 또는 활성 고정지출이 있는 카테고리를 선택합니다.
응답에는 `estimatedTotal`, 카테고리별 `actualExpense`, `predictedVariableExpense`, `scheduledFixedExpense`,
`estimatedTotal`, `previousThreeMonthAverage`, `changeFromAveragePercent`, `sameMonthHistory`, `dailyForecast`가 포함됩니다.
현재 달은 어제까지 실제값 + 오늘부터 남은 기간 예측입니다. 오늘 입력한 거래는 이 응답에서 제외합니다.
다음 달은 해당 월 전체 예측입니다. 이전 3개월 평균은 현재 달 이전의 완료된 3개월입니다.
카테고리별 source가 `NO_VARIABLE_HISTORY`이면 변동지출 AI 예측을 하지 않은 것입니다.

### 자연어 대화

`POST /api/chat`

```json
{
  "message": "이번 달 식비 예산 50만원으로 충분할까?",
  "history": [],
  "historyStart": "2023-01-01",
  "historyComplete": true
}
```

일반 예측: "이번 달 어떤 항목의 소비가 많을 것 같아?"
구매 상담: "30만원짜리 헤드폰을 사도 될까? 오늘 거래를 반영한 가용 잔액은 100만원이야."
구매 상담에는 최근 180일 기록이 필요하며 내일부터 30일을 비교합니다. 월 예측과 기간 정의가 다릅니다.

후속 질문에서는 이전 발화를 함께 보냅니다. 최대 10개 메시지, 각 4000자까지입니다.

```json
{
  "message": "현재 잔액은 100만원이야",
  "history": [
    {"role": "USER", "content": "30만원짜리 헤드폰 사도 될까?"},
    {"role": "ASSISTANT", "content": "현재 가용 잔액을 알려주세요."}
  ],
  "historyStart": "2023-01-01",
  "historyComplete": true
}
```

응답 계약:

- `status`: COMPLETED 또는 NEEDS_CLARIFICATION. 후자는 answer를 질문으로 표시합니다.
- `answer`: 한국어 설명. LLM 생성 문장은 사실 검증을 완전히 보장하지 않습니다.
- `interpretation`: 해석한 의도·기간·카테고리·금액. 프론트에서 사용자에게 표시하면 해석 오류를 수정하기 쉽습니다.
- `forecast`, `budget`, `purchase`: 백엔드가 검증·계산한 수치. 해당 기능이 아니면 null입니다.
- `explanationSource`: LLM / TEMPLATE / CLARIFICATION. 설명 생성만 실패하면 숫자 결과와 TEMPLATE 설명을 반환합니다.
- `warnings`: 부분 실패 안내. 차트와 금액 표시는 answer에서 재추출하지 말고 구조화 필드로 그립니다.

질문 해석 단계와 설명 단계에 OpenAI를 각각 한 번 호출합니다. 추가 질문만 하는 응답은 설명 호출을 생략합니다.
예측은 자동 예약 실행하지 않고 요청 시 수행합니다. 대화·예측 캐시는 아직 없습니다.

### 오류

| 상태 | 대표 code | 의미 |
|---|---|---|
| 400 | fields 또는 message | 입력 검증 실패 |
| 422 | INSUFFICIENT_HISTORY / HISTORY_NOT_CONFIRMED | 기록 부족/완전성 확인 필요 |
| 503 | LLM_NOT_CONFIGURED / MODEL_NOT_CONFIGURED | 키/모델 주소 설정 필요 |
| 502 | OPENAI_UNAVAILABLE / CHRONOS_UNAVAILABLE | 외부 서비스 연결·인증·한도·모델 실행 실패 |
| 502 | LLM_INVALID_RESPONSE / INVALID_FORECAST | 응답 형식·날짜·금액 검증 실패 |

모델 결과가 없을 때 평균값을 AI 예측으로 대신 반환하지 않습니다.
변동지출 이력이 없는 항목의 미래 변동지출 0은 별도 가정이며 source와 assumptions에 표시됩니다.

## 5. 실제 데이터 백테스트

CSV 컬럼은 `id,date,amount`입니다. id는 FOOD 등 카테고리, amount는 해당 일의 **변동지출 합계**입니다.
날짜별로 중복 없이 정렬 가능해야 하며 모든 카테고리의 기간이 같아야 합니다.
무지출이 확인된 날짜만 0으로 채우고, 누락 데이터는 먼저 복원하세요.
최소 90일의 학습 구간 + 검증 구간이 필요합니다.

```csv
id,date,amount
FOOD,2023-01-01,12000
FOOD,2023-01-02,0
```

위 두 행은 형식 예시일 뿐 테스트 가능한 데이터 양이 아닙니다.
`category-daily-expenses` 응답의 `recordedVariableExpense`를 amount로 내보낼 수 있습니다.

```powershell
cd forecast-server
.\.venv\Scripts\python.exe backtest.py 실제데이터.csv --days 30 --output backtest-report.json
```

최신 30일을 모델 입력에서 숨긴 후 실제값과 비교합니다. 7일 반복 기준 모델과 MAE/WAPE를 카테고리별로 비교합니다.
WAPE는 비율(0.2=20%)이며 실제 합계가 0이면 null입니다. 한 번의 검증 구간이므로 다양한 월·계절에서 반복 검증해야 합니다.
백테스트 성능이 확인되기 전에는 서비스 정확도나 신뢰구간을 주장하지 않습니다.

## 참고한 공식 자료

- [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
- [Amazon Chronos-2 모델](https://huggingface.co/amazon/chronos-2)
- [Chronos 공식 저장소](https://github.com/amazon-science/chronos-forecasting)

## 이번 구현에서 확인한 항목

Chronos-2 가중치를 내려받아 로컬 CPU에서 합성 데이터의 7일 예측을 실행했습니다.
실제 지출 데이터의 정확도 평가와 유료 OpenAI 호출은 아직 수행하지 않았습니다.
