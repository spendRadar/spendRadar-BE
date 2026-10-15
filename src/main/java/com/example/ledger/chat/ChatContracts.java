package com.example.ledger.chat;

import com.example.ledger.advice.AdviceContracts;
import com.example.ledger.common.LedgerTypes.Category;
import com.example.ledger.forecast.ForecastContracts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public final class ChatContracts {
    private ChatContracts() {}

    public enum Role { USER, ASSISTANT }
    public enum Intent { FORECAST_EXPENSES, BUDGET_CHECK, PURCHASE_CHECK, CLARIFY }

    public record Message(@NotNull Role role, @NotBlank @Size(max = 4000) String content) {}

    public record Request(
        @NotBlank @Size(max = 2000) String message,
        @Size(max = 10) List<@NotNull @Valid Message> history,
        LocalDate historyStart,
        Boolean historyComplete
    ) {}

    public record Analysis(
        Intent intent, String month, List<Category> categories,
        Long budget, Long purchasePrice, Long availableBalance, String clarificationQuestion
    ) {}

    public record BudgetComparison(long budget, long estimatedExpense, long remaining) {}

    public record Response(
        String status, String answer, String explanationSource, Analysis interpretation,
        ForecastContracts.Response forecast, AdviceContracts.Response purchase,
        BudgetComparison budget, List<String> warnings
    ) {}
}
