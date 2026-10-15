package com.example.ledger.forecast;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.ledger.advice.AdviceUnavailable;
import com.example.ledger.ai.AiSettings;
import com.example.ledger.common.LedgerTypes.Category;
import com.example.ledger.ledger.LedgerContracts.CategoryDailyExpense;
import com.example.ledger.ledger.LedgerService;
import com.example.ledger.recurring.RecurringContracts.ExpenseResponse;
import com.example.ledger.recurring.RecurringService;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ForecastServiceTest {
    private final LedgerService ledgers = mock(LedgerService.class);
    private final RecurringService recurring = mock(RecurringService.class);
    private final ChronosClient chronos = mock(ChronosClient.class);
    private final LocalDate today = LocalDate.of(2026, 10, 14);
    private final LocalDate start = LocalDate.of(2026, 1, 1);
    private ForecastService service;

    @BeforeEach
    void setup() {
        service = new ForecastService(ledgers, recurring, chronos,
            Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"), ZoneId.of("Asia/Seoul")),
            new AiSettings("", "", "", "", "", 120, 90));
        when(ledgers.categoryDailyExpenses(start, today, List.of(Category.FOOD))).thenReturn(
            start.datesUntil(today).map(date -> new CategoryDailyExpense(date, Category.FOOD, 100, 0, 1)).toList());
        when(recurring.expenses()).thenReturn(List.of(
            new ExpenseResponse(1L, "식품 정기배송", 500, 15, true, Category.FOOD),
            new ExpenseResponse(2L, "중단", 9999, 20, false, Category.FOOD),
            new ExpenseResponse(3L, "주거", 9999, 20, true, Category.HOUSING)));
        when(chronos.forecast(anyList(), eq(today), anyInt())).thenAnswer(call -> {
            int days = call.getArgument(2);
            return new ChronosClient.Result("test-only", today.datesUntil(today.plusDays(days))
                .map(date -> new ChronosClient.Point("FOOD", date, 200)).toList());
        });
    }

    @Test
    void combinesClosedActualsWithForecastAndMatchingActiveScheduleOnly() {
        var result = service.forecast(new ForecastContracts.Request("2026-10", start, true, List.of(Category.FOOD)));
        var food = result.categories().getFirst();
        assertEquals(1300, food.actualExpense());
        assertEquals(18 * 200, food.predictedVariableExpense());
        assertEquals(500, food.scheduledFixedExpense());
        assertEquals(5400, result.estimatedTotal());
        assertEquals(today, food.dailyForecast().getFirst().date());
        assertEquals(today.minusDays(1), result.actualThrough());
        assertEquals(9200 / 3.0, food.previousThreeMonthAverage());
        verify(ledgers).categoryDailyExpenses(start, today, List.of(Category.FOOD));
    }

    @Test
    void forecastsAcrossCurrentMonthButReturnsOnlyNextMonth() {
        var result = service.forecast(new ForecastContracts.Request("2026-11", start, true, List.of(Category.FOOD)));
        var food = result.categories().getFirst();
        assertEquals(0, food.actualExpense());
        assertEquals(30, food.dailyForecast().size());
        assertEquals(LocalDate.of(2026, 11, 1), food.dailyForecast().getFirst().date());
        assertEquals(6500, result.estimatedTotal());
        verify(chronos).forecast(anyList(), eq(today), eq(48));
    }

    @Test
    void requiresConfirmedHistoryAndEnoughContextBeforeReadingDb() {
        assertThrows(AdviceUnavailable.class, () -> service.forecast(
            new ForecastContracts.Request("2026-10", start, false, null)));
        assertThrows(AdviceUnavailable.class, () -> service.forecast(
            new ForecastContracts.Request("2026-10", today.minusDays(10), true, null)));
        verifyNoInteractions(ledgers, chronos);
    }

    @Test
    void doesNotLabelNoVariableHistoryAsAiPrediction() {
        when(ledgers.categoryDailyExpenses(start, today, List.of(Category.FOOD))).thenReturn(List.of());
        var result = service.forecast(new ForecastContracts.Request("2026-10", start, true, List.of(Category.FOOD)));
        assertEquals("NO_VARIABLE_HISTORY", result.categories().getFirst().source());
        assertEquals(500, result.estimatedTotal());
        assertEquals("not-used", result.modelId());
        verifyNoInteractions(chronos);
    }
}
