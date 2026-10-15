package com.example.ledger.recurring;

import com.example.ledger.recurring.RecurringContracts.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recurring-incomes")
public class IncomeController {

    private final RecurringService service;

    public IncomeController(RecurringService service) {
        this.service = service;
    }

    @GetMapping
    public List<IncomeResponse> list() {
        return service.incomes();
    }

    @GetMapping("/{id}")
    public IncomeResponse get(@PathVariable Long id) {
        return service.income(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncomeResponse create(@Valid @RequestBody IncomeRequest r) {
        return service.saveIncome(null, r);
    }

    @PutMapping("/{id}")
    public IncomeResponse update(@PathVariable Long id, @Valid @RequestBody IncomeRequest r) {
        return service.saveIncome(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.deleteIncome(id);
    }
}
