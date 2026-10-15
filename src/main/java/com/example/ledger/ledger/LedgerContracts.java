package com.example.ledger.ledger;

import com.example.ledger.common.LedgerTypes.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public final class LedgerContracts {

    private LedgerContracts() {}

    public record LedgerRequest(
        @NotNull LocalDate date,
        @NotNull @Positive @Max(1000000000000L) Long amount,
        @NotNull Category category,
        @NotNull EntryType type,
        ExpenseKind expenseKind,
        @Size(max = 200) String description
    ) {
        @AssertTrue(message = "지출에는 고정/변동 구분이 필요합니다.")
        public boolean isExpenseKindValid() {
            return type != EntryType.EXPENSE || expenseKind != null;
        }
    }

    public record LedgerResponse(
        Long id,
        LocalDate date,
        long amount,
        Category category,
        EntryType type,
        ExpenseKind expenseKind,
        String description
    ) {}

    public record Summary(
        String month,
        long income,
        long expense,
        long balance,
        long fixedExpense,
        long variableExpense
    ) {}

    // 기록이 없는 날의 0원은 무지출이 확인되었다는 의미가 아님
    public record DailyExpense(LocalDate date, long recordedVariableExpense, long transactionCount) {}

    // 금액은 기록된 거래의 합계입니다. 거래 건수 0은 무지출 확인을 의미하지 않습니다.
    public record CategoryDailyExpense(
        LocalDate date,
        Category category,
        long recordedVariableExpense,
        long recordedFixedExpense,
        long expenseTransactionCount
    ) {}

}

// record를 활용한 DTO 정의, 로직 구현에서 사용
