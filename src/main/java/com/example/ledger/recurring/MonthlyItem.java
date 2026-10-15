package com.example.ledger.recurring;

import jakarta.persistence.*;

@MappedSuperclass
abstract class MonthlyItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, length = 80)
    String name;

    @Column(nullable = false)
    long amount;

    @Column(nullable = false)
    int paymentDay;

    @Column(nullable = false)
    boolean active;

    void apply(String name, long amount, int paymentDay, boolean active) {
        this.name = name.trim();
        this.amount = amount;
        this.paymentDay = paymentDay;
        this.active = active;
    }
}

// DB 항목 정의
