package com.example.ledger.ledger;

import com.example.ledger.common.LedgerTypes.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerRepository extends JpaRepository<Ledger, Long> {
    List<Ledger> findByDateGreaterThanEqualAndDateLessThanOrderByDateDescIdDesc(LocalDate start, LocalDate end);

    // 원본 거래 전체를 Java로 가져오지 않고 DB에서 날짜·카테고리별로 집계합니다.
    @Query("""
        select l.date as date, l.category as category,
               sum(case when l.expenseKind = :variable then l.amount else 0 end) as variableExpense,
               sum(case when l.expenseKind = :fixed then l.amount else 0 end) as fixedExpense,
               count(l) as transactionCount
        from Ledger l
        where l.date >= :start and l.date < :end
          and l.type = :type and l.category in :categories
        group by l.date, l.category
        order by l.date, l.category
        """)
    List<CategoryExpenseTotal> sumExpensesByDateAndCategory(
        @Param("start") LocalDate start,
        @Param("end") LocalDate end,
        @Param("categories") List<Category> categories,
        @Param("type") EntryType type,
        @Param("variable") ExpenseKind variable,
        @Param("fixed") ExpenseKind fixed
    );

    interface CategoryExpenseTotal {
        LocalDate getDate();
        Category getCategory();
        long getVariableExpense();
        long getFixedExpense();
        long getTransactionCount();
    }
}
