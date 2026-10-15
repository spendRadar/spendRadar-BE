package com.example.ledger.forecast;

import com.example.ledger.advice.AdviceUnavailable;
import com.example.ledger.ai.AiSettings;
import com.example.ledger.common.LedgerTypes.Category;
import com.example.ledger.ledger.LedgerContracts.CategoryDailyExpense;
import com.example.ledger.ledger.LedgerService;
import com.example.ledger.recurring.RecurringService;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ForecastService {
    private final LedgerService ledgers;
    private final RecurringService recurring;
    private final ChronosClient chronos;
    private final Clock clock;
    private final AiSettings settings;

    public ForecastService(LedgerService ledgers, RecurringService recurring, ChronosClient chronos,
                           Clock clock, AiSettings settings) {
        this.ledgers = ledgers;
        this.recurring = recurring;
        this.chronos = chronos;
        this.clock = clock;
        this.settings = settings;
    }

    // DB 서비스 호출은 각각 완료한 후 외부 모델을 호출합니다.
    public ForecastContracts.Response forecast(ForecastContracts.Request request) {
        var today = LocalDate.now(clock);
        var currentMonth = YearMonth.from(today);
        var month = YearMonth.parse(request.month());
        if (!month.equals(currentMonth) && !month.equals(currentMonth.plusMonths(1))) {
            throw new IllegalArgumentException("이번 달 또는 다음 달만 예측할 수 있습니다.");
        }
        if (!request.historyComplete()) {
            throw new AdviceUnavailable(422, "HISTORY_NOT_CONFIRMED", "누락된 거래가 없는지 확인해주세요.");
        }
        if (request.historyStart() == null || request.historyStart().isBefore(today.minusYears(5))
            || ChronoUnit.DAYS.between(request.historyStart(), today) < settings.minHistoryDays()) {
            throw new AdviceUnavailable(422, "INSUFFICIENT_HISTORY", "최소 " + settings.minHistoryDays()
                + "일, 최대 5년의 일별 기록이 필요합니다.");
        }
        var requested = request.categories();
        var history = ledgers.categoryDailyExpenses(request.historyStart(), today,
            requested == null || requested.isEmpty() ? null : requested);
        var fixedExpenses = recurring.expenses().stream().filter(item -> item.active()).toList();
        var selected = EnumSet.noneOf(Category.class);
        if (requested != null && !requested.isEmpty()) {
            selected.addAll(requested);
        } else {
            history.stream().filter(day -> day.expenseTransactionCount() > 0)
                .forEach(day -> selected.add(day.category()));
            fixedExpenses.forEach(item -> selected.add(item.category()));
        }
        if (selected.isEmpty()) {
            throw new AdviceUnavailable(422, "INSUFFICIENT_HISTORY", "분석할 지출 기록이나 고정지출 설정이 없습니다.");
        }

        var byCategory = new EnumMap<Category, List<CategoryDailyExpense>>(Category.class);
        selected.forEach(category -> byCategory.put(category, new ArrayList<>()));
        history.stream().filter(day -> selected.contains(day.category()))
            .forEach(day -> byCategory.get(day.category()).add(day));
        var series = new ArrayList<ChronosClient.Series>();
        for (var category : selected) {
            var days = byCategory.get(category);
            if (days.stream().anyMatch(day -> day.recordedVariableExpense() > 0)) {
                series.add(new ChronosClient.Series(category.name(), days.stream()
                    .map(day -> new ChronosClient.Observation(day.date(), day.recordedVariableExpense())).toList()));
            }
        }
        var end = month.plusMonths(1).atDay(1);
        var predictions = new HashMap<String, Map<LocalDate, Long>>();
        String modelId = "not-used";
        if (!series.isEmpty()) {
            var result = chronos.forecast(series, today, (int) ChronoUnit.DAYS.between(today, end));
            modelId = result.modelId();
            result.predictions().forEach(point -> predictions
                .computeIfAbsent(point.id(), ignored -> new HashMap<>()).put(point.date(), point.amount()));
        }

        var categories = new ArrayList<ForecastContracts.CategoryForecast>();
        var baselineStart = currentMonth.minusMonths(3).atDay(1);
        for (var category : selected) {
            var days = byCategory.get(category);
            long actual = days.stream().filter(day -> YearMonth.from(day.date()).equals(month))
                .mapToLong(day -> day.recordedVariableExpense() + day.recordedFixedExpense()).sum();
            var forecastDays = new ArrayList<ForecastContracts.DailyForecast>();
            LocalDate first = month.atDay(1).isAfter(today) ? month.atDay(1) : today;
            first.datesUntil(end).forEach(date -> {
                long fixed = fixedExpenses.stream()
                    .filter(item -> item.category() == category && item.paymentDay() == date.getDayOfMonth())
                    .mapToLong(item -> item.amount()).sum();
                long variable = predictions.getOrDefault(category.name(), Map.of()).getOrDefault(date, 0L);
                forecastDays.add(new ForecastContracts.DailyForecast(date, variable, fixed));
            });
            long variable = forecastDays.stream().mapToLong(ForecastContracts.DailyForecast::variableExpense).sum();
            long fixed = forecastDays.stream().mapToLong(ForecastContracts.DailyForecast::scheduledFixedExpense).sum();
            long total = actual + variable + fixed;
            Double average = request.historyStart().isAfter(baselineStart) ? null : days.stream()
                .filter(day -> !day.date().isBefore(baselineStart) && day.date().isBefore(currentMonth.atDay(1)))
                .mapToLong(day -> day.recordedVariableExpense() + day.recordedFixedExpense()).sum() / 3.0;
            Double change = average == null || average == 0 ? null : (total - average) * 100.0 / average;
            var sameMonths = new ArrayList<ForecastContracts.HistoricalMonth>();
            for (int years = 1; years <= 4; years++) {
                var pastMonth = month.minusYears(years);
                if (!pastMonth.atDay(1).isBefore(request.historyStart())
                    && !pastMonth.plusMonths(1).atDay(1).isAfter(today)) {
                    long sum = days.stream().filter(day -> YearMonth.from(day.date()).equals(pastMonth))
                        .mapToLong(day -> day.recordedVariableExpense() + day.recordedFixedExpense()).sum();
                    sameMonths.add(new ForecastContracts.HistoricalMonth(pastMonth.toString(), sum));
                }
            }
            categories.add(new ForecastContracts.CategoryForecast(category,
                predictions.containsKey(category.name()) ? "CHRONOS" : "NO_VARIABLE_HISTORY",
                actual, variable, fixed, total, average, change, sameMonths, forecastDays));
        }
        categories.sort(Comparator.comparingLong(ForecastContracts.CategoryForecast::estimatedTotal).reversed());
        return new ForecastContracts.Response(month.toString(), today.minusDays(1), request.historyStart(), modelId,
            categories.stream().mapToLong(ForecastContracts.CategoryForecast::estimatedTotal).sum(), categories,
            List.of(
                "실제 지출은 어제까지 반영하며 오늘부터의 금액은 예측입니다. 오늘 입력한 거래도 이번 예측의 학습·실적에서 제외합니다.",
                "사용자가 선택 기간의 기록이 완전하다고 확인한 경우에만 빈 날짜를 0원으로 처리합니다.",
                "변동지출 이력이 없는 카테고리는 AI 예측 없이 예정 고정지출과 실적만 반영합니다.",
                "미래 고정지출은 현재 활성 설정을 사용합니다. 과거 고정지출이 자동 반복된다고 추측하지 않습니다.",
                "변동지출은 일별 예측 중앙값을 합한 추정치입니다. 월 합계의 평균·중앙값·신뢰구간은 아니며 음수 예측은 0원으로 보정합니다.",
                "비교 평균은 현재 달 이전의 완료된 3개월이며 예측 오차·확률은 검증되지 않았습니다."
            ));
    }
}
