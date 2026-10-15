package com.example.ledger.advice;

import com.example.ledger.ledger.LedgerService;
import com.example.ledger.recurring.RecurringService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AdviceService {
    private static final int FORECAST_DAYS = 30;
    private static final int HISTORY_DAYS = 180;

    private final LedgerService ledgers;
    private final RecurringService recurring;
    private final ObjectProvider<ExpenseForecaster> forecasters;
    private final Clock clock;

    public AdviceService(LedgerService ledgers, RecurringService recurring,
                         ObjectProvider<ExpenseForecaster> forecasters, Clock clock) {
        this.ledgers = ledgers;
        this.recurring = recurring;
        this.forecasters = forecasters;
        this.clock = clock;
    }

    // 외부 모델 호출 중 DB 트랜잭션을 유지하지 않습니다.
    public AdviceContracts.Response advise(AdviceContracts.Request request) {
        var forecaster = forecasters.getIfAvailable();
        if (forecaster == null) {
            throw new AdviceUnavailable(503, "MODEL_NOT_CONFIGURED", "시계열 예측 모델이 아직 연결되지 않았습니다.");
        }

        forecaster.requireConfigured();

        var today = LocalDate.now(clock);
        var start = today.plusDays(1);
        var history = ledgers.dailyExpenses(today.minusDays(HISTORY_DAYS), today);
        if (history.stream().noneMatch(day -> day.recordedVariableExpense() > 0)) {
            throw new AdviceUnavailable(422, "INSUFFICIENT_HISTORY", "예측에 사용할 과거 변동지출 기록이 없습니다.");
        }
        var incomes = recurring.incomes();
        var expenses = recurring.expenses();

        ExpenseForecaster.Forecast forecast;
        try {
            forecast = forecaster.forecast(history, start, FORECAST_DAYS);
        } catch (AdviceUnavailable e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AdviceUnavailable(502, "MODEL_UNAVAILABLE", "예측 모델을 호출하지 못했습니다.");
        }
        validate(forecast, start);

        long balance = request.availableBalance();
        long minimumWithout = balance;
        long minimumWith = balance - request.purchasePrice();
        var daily = new ArrayList<AdviceContracts.DailyBalance>();
        for (var prediction : forecast.predictions()) {
            var date = prediction.date();
            long income = incomes.stream()
                .filter(item -> item.active() && item.paymentDay() == date.getDayOfMonth())
                .mapToLong(item -> item.amount()).sum();
            long fixed = expenses.stream()
                .filter(item -> item.active() && item.paymentDay() == date.getDayOfMonth())
                .mapToLong(item -> item.amount()).sum();
            balance = balance + income - fixed - prediction.amount();
            long afterPurchase = balance - request.purchasePrice();
            minimumWithout = Math.min(minimumWithout, balance);
            minimumWith = Math.min(minimumWith, afterPurchase);
            daily.add(new AdviceContracts.DailyBalance(
                date, income, fixed, prediction.amount(), balance, afterPurchase
            ));
        }
        return new AdviceContracts.Response(
            forecast.modelId(), start, start.plusDays(FORECAST_DAYS),
            minimumWithout, minimumWith, minimumWith < 0, List.copyOf(daily),
            List.of(
                "현재 가용 금액은 오늘까지의 거래가 반영된 금액이며 오늘 남은 거래는 포함하지 않습니다.",
                "활성 정기 항목은 예정일에 입력한 금액대로 발생한다고 가정합니다.",
                "예측은 일 마감 기준이며 같은 날 입출금 순서에 따른 잔액 부족은 반영하지 않습니다.",
                "빈 날짜는 기록 누락일 수 있습니다. 모델의 데이터 품질 검증이 필요합니다.",
                "예측 오차와 추가 비상자금은 반영되지 않으며 구매 안전성을 보장하지 않습니다."
            )
        );
    }

    private void validate(ExpenseForecaster.Forecast forecast, LocalDate start) {
        boolean invalid = forecast == null || forecast.modelId() == null || forecast.modelId().isBlank()
            || forecast.predictions() == null || forecast.predictions().size() != FORECAST_DAYS;
        if (!invalid) {
            for (int i = 0; i < FORECAST_DAYS; i++) {
                var prediction = forecast.predictions().get(i);
                if (prediction == null || !start.plusDays(i).equals(prediction.date())
                    || prediction.amount() < 0 || prediction.amount() > 1000000000000L) {
                    invalid = true;
                    break;
                }
            }
        }
        if (invalid) {
            throw new AdviceUnavailable(502, "INVALID_FORECAST", "모델의 날짜별 예측 결과가 올바르지 않습니다.");
        }
    }
}
