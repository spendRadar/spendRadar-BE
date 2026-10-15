package com.example.ledger.forecast;

import com.example.ledger.advice.ExpenseForecaster;
import com.example.ledger.ledger.LedgerContracts.DailyExpense;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/** 기존 구매 상담도 같은 Python 예측 서버를 사용합니다. */
@Component
public class ChronosExpenseForecaster implements ExpenseForecaster {
    private final ChronosClient chronos;

    public ChronosExpenseForecaster(ChronosClient chronos) {
        this.chronos = chronos;
    }

    @Override
    public void requireConfigured() {
        chronos.requireConfigured();
    }

    @Override
    public Forecast forecast(List<DailyExpense> history, LocalDate start, int days) {
        var observations = history.stream()
            .map(day -> new ChronosClient.Observation(day.date(), day.recordedVariableExpense())).toList();
        var result = chronos.forecast(List.of(new ChronosClient.Series("VARIABLE_TOTAL", observations)), start, days);
        return new Forecast(result.modelId(), result.predictions().stream()
            .map(point -> new Prediction(point.date(), point.amount())).toList());
    }
}
