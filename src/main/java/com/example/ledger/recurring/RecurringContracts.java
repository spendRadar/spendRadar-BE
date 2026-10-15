package com.example.ledger.recurring;

import com.example.ledger.common.LedgerTypes.Category;
import jakarta.validation.constraints.*;

public final class RecurringContracts {

    private RecurringContracts() {}

    public record IncomeRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull @Positive @Max(1000000000000L) Long amount,
        @NotNull @Min(1) @Max(28) Integer paymentDay,
        @NotNull Boolean active
    ) {}

    public record ExpenseRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull @Positive @Max(1000000000000L) Long amount,
        @NotNull @Min(1) @Max(28) Integer paymentDay,
        @NotNull Boolean active,
        @NotNull Category category
    ) {}

    public record IncomeResponse(Long id, String name, long amount, int paymentDay, boolean active) {}

    public record ExpenseResponse(
        Long id,
        String name,
        long amount,
        int paymentDay,
        boolean active,
        Category category
    ) {}

}
