package com.example.ledger.recurring;

import com.example.ledger.common.LedgerTypes.*;
import com.example.ledger.recurring.RecurringContracts.*;
import jakarta.persistence.*;

@Entity
public class FixedExpense extends MonthlyItem {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    Category category;

    protected FixedExpense() {}

    ExpenseResponse response() {
        return new ExpenseResponse(id, name, amount, paymentDay, active, category);
    }
}
