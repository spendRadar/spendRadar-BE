package com.example.ledger.ledger;

import com.example.ledger.ledger.LedgerContracts.*;
import com.example.ledger.common.LedgerTypes.Category;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ledgers")
public class LedgerController {

    private final LedgerService service;

    public LedgerController(LedgerService service) {
        this.service = service;
    }

    @GetMapping
    public List<LedgerResponse> list(@RequestParam String month) {
        return service.list(month);
    }

    @GetMapping("/summary")
    public Summary summary(@RequestParam String month) {
        return service.summary(month);
    }  // 반환하면 http 메세지 형태로 record 클래스를 반환

    @GetMapping("/daily-expenses")
    public List<DailyExpense> dailyExpenses(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return service.dailyExpenses(start, end);
    }

    @GetMapping("/category-daily-expenses")
    public List<CategoryDailyExpense> categoryDailyExpenses(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
        @RequestParam(required = false) List<Category> categories
    ) {
        return service.categoryDailyExpenses(start, end, categories);
    }

    @GetMapping("/{id}")
    public LedgerResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerResponse create(@Valid @RequestBody LedgerRequest r) {
        return service.create(r);
    }

    @PutMapping("/{id}")
    public LedgerResponse update(@PathVariable Long id, @Valid @RequestBody LedgerRequest r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}

// HTTP 요청이 들어오면 맞는 Service 호출