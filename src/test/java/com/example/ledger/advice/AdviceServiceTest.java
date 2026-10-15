package com.example.ledger.advice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.ledger.common.LedgerTypes.Category;
import com.example.ledger.ledger.LedgerContracts.DailyExpense;
import com.example.ledger.ledger.LedgerService;
import com.example.ledger.recurring.RecurringContracts.*;
import com.example.ledger.recurring.RecurringService;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AdviceServiceTest {
    private final LedgerService ledgers = mock(LedgerService.class);
    private final RecurringService recurring = mock(RecurringService.class);
    private final ExpenseForecaster model = mock(ExpenseForecaster.class);
    private AdviceService service;
    private final LocalDate today = LocalDate.of(2026, 1, 31);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<ExpenseForecaster> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(model);
        service = new AdviceService(ledgers, recurring, provider,
            Clock.fixed(Instant.parse("2026-01-30T15:00:00Z"), ZoneId.of("Asia/Seoul")));
        when(ledgers.dailyExpenses(today.minusDays(180), today))
            .thenReturn(List.of(new DailyExpense(today.minusDays(1), 1000, 1)));
        when(recurring.incomes()).thenReturn(List.of());
        when(recurring.expenses()).thenReturn(List.of());
        when(model.forecast(anyList(), eq(today.plusDays(1)), eq(30)))
            .thenReturn(predictions(10));
    }

    private ExpenseForecaster.Forecast predictions(long amount) {
        var daily = today.plusDays(1).datesUntil(today.plusDays(31))
            .map(date -> new ExpenseForecaster.Prediction(date, amount)).toList();
        return new ExpenseForecaster.Forecast("test-only-model", daily);
    }

    @Test
    void comparesScenariosAcrossMonthsAndExcludesInactiveSettings() {
        when(recurring.incomes()).thenReturn(List.of(
            new IncomeResponse(1L, "수입", 1000, 1, true),
            new IncomeResponse(2L, "중단", 99999, 2, false)));
        when(recurring.expenses()).thenReturn(List.of(
            new ExpenseResponse(1L, "월세", 200, 1, true, Category.HOUSING),
            new ExpenseResponse(2L, "중단", 99999, 2, false, Category.OTHER)));
        var result = service.advise(new AdviceContracts.Request("물건", 100L, 1000L));
        assertEquals(30, result.dailyBalances().size());
        assertEquals(LocalDate.of(2026, 3, 3), result.end());
        assertEquals(1000, result.minimumWithoutPurchase());
        assertEquals(900, result.minimumWithPurchase());
        assertEquals(1790, result.dailyBalances().getFirst().withoutPurchase());
        assertEquals(2300, result.dailyBalances().getLast().withoutPurchase());
        assertFalse(result.projectedShortfall());
    }

    @Test
    void includesImmediatePurchaseAndLaterShortfall() {
        var immediate = service.advise(new AdviceContracts.Request(null, 2000L, 1000L));
        assertEquals(-1300, immediate.minimumWithPurchase());
        assertTrue(immediate.projectedShortfall());
        var later = service.advise(new AdviceContracts.Request(null, 800L, 1000L));
        assertEquals(-100, later.minimumWithPurchase());
        assertTrue(later.projectedShortfall());
    }

    @Test
    void rejectsMissingHistoryBeforeCallingModel() {
        when(ledgers.dailyExpenses(any(), any())).thenReturn(List.of());
        var error = assertThrows(AdviceUnavailable.class,
            () -> service.advise(new AdviceContracts.Request(null, 100L, 1000L)));
        assertEquals("INSUFFICIENT_HISTORY", error.code());
        verify(model, never()).forecast(anyList(), any(), anyInt());
    }

    @Test
    void rejectsIncompleteAndNegativeForecasts() {
        when(model.forecast(anyList(), any(), anyInt()))
            .thenReturn(new ExpenseForecaster.Forecast("test-only-model", List.of()));
        assertEquals("INVALID_FORECAST", assertThrows(AdviceUnavailable.class,
            () -> service.advise(new AdviceContracts.Request(null, 100L, 1000L))).code());
        when(model.forecast(anyList(), any(), anyInt())).thenReturn(predictions(-1));
        assertEquals("INVALID_FORECAST", assertThrows(AdviceUnavailable.class,
            () -> service.advise(new AdviceContracts.Request(null, 100L, 1000L))).code());
    }

    @Test
    void reportsModelFailureWithoutReturningAdvice() {
        when(model.forecast(anyList(), any(), anyInt())).thenThrow(new IllegalStateException("offline"));
        assertEquals("MODEL_UNAVAILABLE", assertThrows(AdviceUnavailable.class,
            () -> service.advise(new AdviceContracts.Request(null, 100L, 1000L))).code());
    }
}
