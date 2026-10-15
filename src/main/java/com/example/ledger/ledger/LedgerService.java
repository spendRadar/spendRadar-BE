package com.example.ledger.ledger;

import com.example.ledger.common.LedgerTypes.*;
import com.example.ledger.ledger.LedgerContracts.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LedgerService {

    private final LedgerRepository repo;

    public LedgerService(LedgerRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<LedgerResponse> list(String month) {
        var m = YearMonth.parse(month);
        return repo
            .findByDateGreaterThanEqualAndDateLessThanOrderByDateDescIdDesc(m.atDay(1), m.plusMonths(1).atDay(1))
            .stream()
            .map(Ledger::response)
            .toList();
    }

    /** 기록된 변동지출만 날짜별로 합산합니다. 기록 누락 여부는 판단하지 않습니다. */
    @Transactional(readOnly = true)
    public List<DailyExpense> dailyExpenses(LocalDate start, LocalDate end) {
        if (!start.isBefore(end) || start.plusYears(1).isBefore(end)) {
            throw new IllegalArgumentException("조회 기간은 시작일 이상, 종료일 미만이며 최대 1년입니다.");
        }

        var totals = new TreeMap<LocalDate, Long>();
        var counts = new HashMap<LocalDate, Long>();
        for (var item : repo.findByDateGreaterThanEqualAndDateLessThanOrderByDateDescIdDesc(start, end)) {
            counts.merge(item.date, 1L, Long::sum);
            if (item.type == EntryType.EXPENSE && item.expenseKind == ExpenseKind.VARIABLE) {
                totals.merge(item.date, item.amount, Long::sum);
            }
        }

        return start.datesUntil(end)
            .map(date -> new DailyExpense(date, totals.getOrDefault(date, 0L), counts.getOrDefault(date, 0L)))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryDailyExpense> categoryDailyExpenses(
        LocalDate start, LocalDate end, List<Category> categories
    ) {
        if (!start.isBefore(end) || start.plusYears(5).isBefore(end)) {
            throw new IllegalArgumentException("조회 기간은 시작일 포함·종료일 제외, 최대 5년입니다.");
        }
        var selected = categories == null ? EnumSet.allOf(Category.class) : EnumSet.noneOf(Category.class);
        if (categories != null) {
            if (categories.isEmpty() || categories.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("카테고리를 확인해주세요.");
            }
            selected.addAll(categories);
        }
        var totals = new HashMap<LocalDate, Map<Category, LedgerRepository.CategoryExpenseTotal>>();
        for (var row : repo.sumExpensesByDateAndCategory(
            start, end, List.copyOf(selected), EntryType.EXPENSE, ExpenseKind.VARIABLE, ExpenseKind.FIXED
        )) {
            totals.computeIfAbsent(row.getDate(), ignored -> new EnumMap<>(Category.class))
                .put(row.getCategory(), row);
        }
        var result = new ArrayList<CategoryDailyExpense>();
        start.datesUntil(end).forEach(date -> {
            var day = totals.getOrDefault(date, Map.of());
            for (var category : selected) {
                var row = day.get(category);
                result.add(new CategoryDailyExpense(
                    date, category,
                    row == null ? 0 : row.getVariableExpense(),
                    row == null ? 0 : row.getFixedExpense(),
                    row == null ? 0 : row.getTransactionCount()
                ));
            }
        });
        return result;
    }

    @Transactional(readOnly = true)
    public LedgerResponse get(Long id) {
        return find(id).response();
    }

    public LedgerResponse create(LedgerRequest r) {
        var item = new Ledger();
        item.apply(r);
        return repo.save(item).response();
    }

    public LedgerResponse update(Long id, LedgerRequest r) {
        var item = find(id);
        item.apply(r);
        return item.response();
    }

    public void delete(Long id) {
        repo.delete(find(id));
    }

    private Ledger find(Long id) {
        return repo.findById(id).orElseThrow(NoSuchElementException::new);
    }

    @Transactional(readOnly = true)
    public Summary summary(String month) {
        long income = 0;
        long expense = 0;
        long fixed = 0;
        long variable = 0;

        for (var item : list(month)) {
            if (item.type() == EntryType.INCOME) {
                income += item.amount();
            } else {
                expense += item.amount();
                if (item.expenseKind() == ExpenseKind.FIXED) {
                    fixed += item.amount();
                } else {
                    variable += item.amount();
                }
            }
        }
        return new Summary(month, income, expense, income - expense, fixed, variable);
    }
}
