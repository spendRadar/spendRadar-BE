package com.example.ledger.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.ledger.advice.*;
import com.example.ledger.ai.OpenAiClient;
import com.example.ledger.forecast.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatServiceTest {
    private final OpenAiClient llm = mock(OpenAiClient.class);
    private final ForecastService forecast = mock(ForecastService.class);
    private final AdviceService purchase = mock(AdviceService.class);
    private final ChatService service = new ChatService(llm, forecast, purchase,
        Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"), ZoneId.of("Asia/Seoul")));

    @Test
    void asksForMissingPurchaseBalanceWithoutInvokingPrediction() {
        when(llm.interpret(any(), any())).thenReturn(new ChatContracts.Analysis(
            ChatContracts.Intent.PURCHASE_CHECK, null, List.of(), null, 100000L, null, null));
        var result = service.chat(new ChatContracts.Request("사도 돼?", List.of(), LocalDate.of(2025, 1, 1), true));
        assertEquals("NEEDS_CLARIFICATION", result.status());
        verifyNoInteractions(forecast, purchase);
    }

    @Test
    void computesBudgetOnServerAndRetainsEvidenceWhenExplanationFails() {
        when(llm.interpret(any(), any())).thenReturn(new ChatContracts.Analysis(
            ChatContracts.Intent.BUDGET_CHECK, "2026-10", List.of(), 300000L, null, null, null));
        when(forecast.forecast(any())).thenReturn(new ForecastContracts.Response(
            "2026-10", LocalDate.of(2026, 10, 13), LocalDate.of(2025, 1, 1), "test-only", 350000,
            List.of(), List.of()));
        when(llm.explain(anyString(), any())).thenThrow(new AdviceUnavailable(502, "OPENAI_UNAVAILABLE", "test"));
        var result = service.chat(new ChatContracts.Request("30만원 예산", List.of(), LocalDate.of(2025, 1, 1), true));
        assertEquals(-50000, result.budget().remaining());
        assertEquals("TEMPLATE", result.explanationSource());
        assertEquals("COMPLETED", result.status());
        assertNotNull(result.forecast());
        assertFalse(result.warnings().isEmpty());
    }
}
