package com.example.ledger.ledger;

import com.example.ledger.common.LedgerTypes.*;
import com.example.ledger.ledger.LedgerContracts.*;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "ledger", indexes = @Index(name = "idx_ledger_date", columnList = "entry_date"))
public class Ledger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "entry_date", nullable = false)
    LocalDate date;

    @Column(nullable = false)
    long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    EntryType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    ExpenseKind expenseKind;

    @Column(length = 200)
    String description;

    protected Ledger() {}

    void apply(LedgerRequest r) {
        date = r.date();
        amount = r.amount();
        category = r.category();
        type = r.type();
        expenseKind = type == EntryType.EXPENSE ? r.expenseKind() : null;
        description = r.description() == null ? "" : r.description().trim();
    }

    LedgerResponse response() {
        return new LedgerResponse(
            id, date, amount, category, type,
            type == EntryType.EXPENSE ? expenseKind : null, description
        );
    }
}
