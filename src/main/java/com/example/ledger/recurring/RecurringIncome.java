package com.example.ledger.recurring;

import com.example.ledger.recurring.RecurringContracts.*;
import jakarta.persistence.*;

@Entity
public class RecurringIncome extends MonthlyItem {

    protected RecurringIncome() {}

    IncomeResponse response() {
        return new IncomeResponse(id, name, amount, paymentDay, active);
    }
}
