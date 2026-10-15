package com.example.ledger.advice;

import com.example.ledger.ledger.LedgerContracts.DailyExpense;
import java.time.LocalDate;
import java.util.List;

/** 실제 모델의 호출 구현을 Spring 빈으로 등록하면 상담 서비스에 연결됩니다. */
public interface ExpenseForecaster {
    default void requireConfigured() {}

    Forecast forecast(List<DailyExpense> history, LocalDate start, int days);

    record Prediction(LocalDate date, long amount) {}
    record Forecast(String modelId, List<Prediction> predictions) {}
}


// 모델 호출 인터페이스. 실제 HTTP 호출은 forecast/ChronosExpenseForecaster에서 구현합니다.
