package com.example.ledger.recurring;

import com.example.ledger.recurring.RecurringContracts.*;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RecurringService {

    private final RecurringIncomeRepository incomes;
    private final FixedExpenseRepository expenses;

    public RecurringService(RecurringIncomeRepository incomes, FixedExpenseRepository expenses) {
        this.incomes = incomes;
        this.expenses = expenses;
    }

    @Transactional(readOnly = true)
    public List<IncomeResponse> incomes() {
        return incomes.findAll(Sort.by("paymentDay", "id")).stream().map(RecurringIncome::response).toList();
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> expenses() {
        return expenses.findAll(Sort.by("paymentDay", "id")).stream().map(FixedExpense::response).toList();
    }

    @Transactional(readOnly = true)
    public IncomeResponse income(Long id) {
        return incomes.findById(id).orElseThrow(NoSuchElementException::new).response();
    }

    @Transactional(readOnly = true)
    public ExpenseResponse expense(Long id) {
        return expenses.findById(id).orElseThrow(NoSuchElementException::new).response();
    }

    public IncomeResponse saveIncome(Long id, IncomeRequest r) {
        var item = id == null ? new RecurringIncome() : incomes.findById(id).orElseThrow(NoSuchElementException::new);
        item.apply(r.name(), r.amount(), r.paymentDay(), r.active());
        return incomes.save(item).response();
    }

    public ExpenseResponse saveExpense(Long id, ExpenseRequest r) {
        var item = id == null ? new FixedExpense() : expenses.findById(id).orElseThrow(NoSuchElementException::new);
        item.apply(r.name(), r.amount(), r.paymentDay(), r.active());
        item.category = r.category();
        return expenses.save(item).response();
    }

    public void deleteIncome(Long id) {
        incomes.delete(incomes.findById(id).orElseThrow(NoSuchElementException::new));
    }

    public void deleteExpense(Long id) {
        expenses.delete(expenses.findById(id).orElseThrow(NoSuchElementException::new));
    }
}
