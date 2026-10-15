package com.example.ledger.advice;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public final class AdviceContracts {
    private AdviceContracts() {}

    public record Request(
        @Size(max = 100) String itemName,
        @NotNull @Positive @Max(1000000000000L) Long purchasePrice,
        @NotNull @PositiveOrZero @Max(1000000000000L) Long availableBalance
    ) {}

    public record DailyBalance(
        LocalDate date, long income, long fixedExpense, long predictedVariableExpense,
        long withoutPurchase, long withPurchase
    ) {}

    public record Response(
        String modelId, LocalDate start, LocalDate end,
        long minimumWithoutPurchase, long minimumWithPurchase,
        boolean projectedShortfall, List<DailyBalance> dailyBalances, List<String> assumptions
    ) {}
}

// 구매 상담 입력값, 결과 DTO