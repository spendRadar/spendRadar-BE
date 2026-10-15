package com.example.ledger.chat;

import com.example.ledger.advice.*;
import com.example.ledger.ai.OpenAiClient;
import com.example.ledger.forecast.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private final OpenAiClient llm;
    private final ForecastService forecasts;
    private final AdviceService purchases;
    private final Clock clock;

    public ChatService(OpenAiClient llm, ForecastService forecasts, AdviceService purchases, Clock clock) {
        this.llm = llm;
        this.forecasts = forecasts;
        this.purchases = purchases;
        this.clock = clock;
    }

    public ChatContracts.Response chat(ChatContracts.Request request) {
        var analysis = llm.interpret(request, LocalDate.now(clock));
        if (analysis.intent() == ChatContracts.Intent.CLARIFY) {
            return clarify(analysis, analysis.clarificationQuestion() == null
                ? "어느 기간의 지출을 확인하고 싶으신가요?" : analysis.clarificationQuestion());
        }
        if (!Boolean.TRUE.equals(request.historyComplete()) || request.historyStart() == null) {
            return clarify(analysis, "거래 기록 시작일을 지정하고 그 이후의 기록에 누락이 없는지 확인해주세요.");
        }

        ForecastContracts.Response forecast = null;
        AdviceContracts.Response purchase = null;
        ChatContracts.BudgetComparison budget = null;
        var evidence = new LinkedHashMap<String, Object>();
        String fallback;
        if (analysis.intent() == ChatContracts.Intent.PURCHASE_CHECK) {
            if (analysis.purchasePrice() == null || analysis.purchasePrice() <= 0
                || analysis.availableBalance() == null) {
                return clarify(analysis, "구매 금액과 오늘까지의 거래를 반영한 현재 가용 잔액을 알려주세요.");
            }
            if (request.historyStart().isAfter(LocalDate.now(clock).minusDays(180))) {
                return clarify(analysis, "구매 상담에는 최근 180일의 누락 없는 거래 기록이 필요합니다.");
            }
            purchase = purchases.advise(new AdviceContracts.Request(null,
                analysis.purchasePrice(), analysis.availableBalance()));
            evidence.put("purchase", Map.of(
                "start", purchase.start(), "endExclusive", purchase.end(),
                "minimumWithoutPurchase", purchase.minimumWithoutPurchase(),
                "minimumWithPurchase", purchase.minimumWithPurchase(),
                "projectedShortfall", purchase.projectedShortfall(), "assumptions", purchase.assumptions()));
            fallback = "구매 후 향후 30일 예상 최소 잔액은 " + purchase.minimumWithPurchase()
                + "원입니다. 예측 오차와 오늘 남은 거래는 반영하지 않았습니다.";
        } else {
            if (analysis.month() == null) {
                return clarify(analysis, "이번 달과 다음 달 중 어느 기간을 확인할까요?");
            }
            try {
                var month = YearMonth.parse(analysis.month());
                var now = YearMonth.now(clock);
                if (!month.equals(now) && !month.equals(now.plusMonths(1))) {
                    return clarify(analysis, "현재는 이번 달과 다음 달 지출 예측을 지원합니다.");
                }
            } catch (DateTimeException e) {
                return clarify(analysis, "예측할 달을 다시 알려주세요.");
            }
            if (analysis.intent() == ChatContracts.Intent.BUDGET_CHECK && analysis.budget() == null) {
                return clarify(analysis, "선택한 기간과 카테고리에 사용할 예산을 알려주세요.");
            }
            forecast = forecasts.forecast(new ForecastContracts.Request(analysis.month(), request.historyStart(),
                true, analysis.categories()));
            // 일별 차트 데이터는 프론트에만 반환하고 LLM에는 설명용 집계만 보냅니다.
            evidence.put("forecast", Map.of("month", forecast.month(), "actualThrough", forecast.actualThrough(),
                "estimatedTotal", forecast.estimatedTotal(), "assumptions", forecast.assumptions(),
                "categories", forecast.categories().stream().map(CategoryEvidence::from).toList()));
            fallback = forecast.month() + " 선택 카테고리의 월 지출 추정 합계는 " + forecast.estimatedTotal()
                + "원입니다. 상세 수치와 가정은 forecast 필드를 확인해주세요.";
            if (analysis.intent() == ChatContracts.Intent.BUDGET_CHECK) {
                budget = new ChatContracts.BudgetComparison(analysis.budget(), forecast.estimatedTotal(),
                    analysis.budget() - forecast.estimatedTotal());
                evidence.put("budget", budget);
                fallback += " 예산에서 추정 지출을 뺀 금액은 " + budget.remaining() + "원입니다.";
            }
        }
        try {
            return new ChatContracts.Response("COMPLETED", llm.explain(request.message(), evidence),
                "LLM", analysis, forecast, purchase, budget, List.of());
        } catch (AdviceUnavailable e) {
            return new ChatContracts.Response("COMPLETED", fallback, "TEMPLATE", analysis,
                forecast, purchase, budget, List.of("LLM 설명 생성 실패: " + e.code()));
        }
    }

    private ChatContracts.Response clarify(ChatContracts.Analysis analysis, String question) {
        return new ChatContracts.Response("NEEDS_CLARIFICATION", question, "CLARIFICATION",
            analysis, null, null, null, List.of());
    }

    private record CategoryEvidence(
        String category, String source, long actualExpense, long predictedVariableExpense,
        long scheduledFixedExpense, long estimatedTotal, Double previousThreeMonthAverage,
        Double changeFromAveragePercent, List<ForecastContracts.HistoricalMonth> sameMonthHistory
    ) {
        static CategoryEvidence from(ForecastContracts.CategoryForecast item) {
            return new CategoryEvidence(item.category().name(), item.source(), item.actualExpense(),
                item.predictedVariableExpense(), item.scheduledFixedExpense(), item.estimatedTotal(),
                item.previousThreeMonthAverage(), item.changeFromAveragePercent(), item.sameMonthHistory());
        }
    }
}
