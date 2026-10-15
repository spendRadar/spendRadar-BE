package com.example.ledger.forecast;

import com.example.ledger.advice.AdviceUnavailable;
import com.example.ledger.ai.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ChronosClient {
    public record Observation(LocalDate date, long amount) {}
    public record Series(String id, List<Observation> history) {}
    public record Point(String id, LocalDate date, long amount) {}
    public record Result(String modelId, List<Point> predictions) {}

    private final AiSettings settings;
    private final AiHttpClient http;

    public ChronosClient(AiSettings settings, AiHttpClient http) {
        this.settings = settings;
        this.http = http;
    }

    public void requireConfigured() {
        if (settings.chronosUrl() == null || settings.chronosUrl().isBlank()) {
            throw new AdviceUnavailable(503, "MODEL_NOT_CONFIGURED", "CHRONOS_URL에 Python 예측 서버 주소를 설정해주세요.");
        }
    }

    public Result forecast(List<Series> series, LocalDate start, int days) {
        requireConfigured();
        var response = http.post(settings.chronosUrl().replaceAll("/+$", "") + "/forecast",
            settings.chronosToken(), Map.of("series", series, "start", start, "days", days), "CHRONOS");
        try {
            String modelId = response.path("modelId").asText("");
            var rows = response.path("predictions");
            if (modelId.isBlank() || !rows.isArray() || rows.size() != series.size() * days) {
                throw new IllegalArgumentException();
            }
            var ids = new HashSet<String>();
            series.forEach(item -> ids.add(item.id()));
            var seen = new HashSet<String>();
            var points = new ArrayList<Point>();
            for (var row : rows) {
                String id = row.path("id").asText("");
                var date = LocalDate.parse(row.path("date").asText(""));
                var amountNode = row.path("amount");
                if (!amountNode.isIntegralNumber() || !amountNode.canConvertToLong()) {
                    throw new IllegalArgumentException();
                }
                long amount = amountNode.longValue();
                if (!ids.contains(id) || date.isBefore(start) || !date.isBefore(start.plusDays(days))
                    || amount < 0 || amount > 1000000000000L || !seen.add(id + ":" + date)) {
                    throw new IllegalArgumentException();
                }
                points.add(new Point(id, date, amount));
            }
            points.sort(Comparator.comparing(Point::id).thenComparing(Point::date));
            return new Result(modelId, List.copyOf(points));
        } catch (RuntimeException e) {
            throw new AdviceUnavailable(502, "INVALID_FORECAST", "모델의 날짜·카테고리·금액을 확인할 수 없습니다.");
        }
    }
}
