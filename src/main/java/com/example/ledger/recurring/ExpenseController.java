package com.example.ledger.recurring;

import com.example.ledger.recurring.RecurringContracts.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fixed-expenses")
public class ExpenseController {

    private final RecurringService service;

    public ExpenseController(RecurringService service) {
        this.service = service;
    }

    @GetMapping
    public List<ExpenseResponse> list() {
        return service.expenses();
    }

    @GetMapping("/{id}")
    public ExpenseResponse get(@PathVariable Long id) {
        return service.expense(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(@Valid @RequestBody ExpenseRequest r) {
        return service.saveExpense(null, r);
    }

    @PutMapping("/{id}")
    public ExpenseResponse update(@PathVariable Long id, @Valid @RequestBody ExpenseRequest r) {
        return service.saveExpense(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.deleteExpense(id);
    }
}


// 고정 지출 API 요청 처리