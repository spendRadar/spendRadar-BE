package com.example.ledger.forecast;

import com.example.ledger.common.LedgerTypes.Category;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public final class ForecastContracts {
    private ForecastContracts() {}

    public record Request(
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}") String month,
        @NotNull LocalDate historyStart,
        @AssertTrue(message = "선택 기간의 거래 기록이 완전한지 확인해주세요.") boolean historyComplete,
        @Size(max = 8) List<@NotNull Category> categories
    ) {}

    public record HistoricalMonth(String month, long recordedExpense) {}
    public record DailyForecast(LocalDate date, long variableExpense, long scheduledFixedExpense) {}
    public record CategoryForecast(
        Category category, String source, long actualExpense, long predictedVariableExpense,
        long scheduledFixedExpense, long estimatedTotal, Double previousThreeMonthAverage,
        Double changeFromAveragePercent, List<HistoricalMonth> sameMonthHistory,
        List<DailyForecast> dailyForecast
    ) {}
    public record Response(
        String month, LocalDate actualThrough, LocalDate historyStart, String modelId,
        long estimatedTotal, List<CategoryForecast> categories, List<String> assumptions
    ) {}
}
