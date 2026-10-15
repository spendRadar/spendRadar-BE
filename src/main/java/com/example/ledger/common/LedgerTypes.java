package com.example.ledger.common;

public final class LedgerTypes {

    private LedgerTypes() {}

    public enum EntryType {
        INCOME,
        EXPENSE
    }

    public enum ExpenseKind {
        FIXED,
        VARIABLE
    }

    public enum Category {
        FOOD,
        TRANSPORT,
        SHOPPING,
        HOUSING,
        HEALTH,
        LEISURE,
        SALARY,
        OTHER
    }

}

// 카테고리 enum 정의