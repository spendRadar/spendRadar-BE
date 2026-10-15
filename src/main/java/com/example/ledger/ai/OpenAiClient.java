package com.example.ledger.ai;

import com.example.ledger.advice.AdviceUnavailable;
import com.example.ledger.chat.ChatContracts;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OpenAiClient {
    private final AiSettings settings;
    private final AiHttpClient http;
    private final JsonMapper json = new JsonMapper();
    private final String interpretationPrompt = resource("prompts/interpret.txt");
    private final String explanationPrompt = resource("prompts/explain.txt");
    private final JsonNode analysisSchema = json.readTree(resource("prompts/analysis.schema.json"));

    public OpenAiClient(AiSettings settings, AiHttpClient http) {
        this.settings = settings;
        this.http = http;
    }

    public ChatContracts.Analysis interpret(ChatContracts.Request request, LocalDate today) {
        var input = new ArrayList<Map<String, String>>();
        if (request.history() != null) {
            request.history().forEach(message -> input.add(Map.of(
                "role", message.role().name().toLowerCase(Locale.ROOT), "content", message.content())));
        }
        input.add(Map.of("role", "user", "content", request.message()));
        var output = call(interpretationPrompt + "\n서버 기준 오늘: " + today,
            input, "expense_question", analysisSchema);
        try {
            var result = json.treeToValue(output, ChatContracts.Analysis.class);
            if (result.intent() == null || result.categories() == null
                || result.categories().size() > 8 || result.categories().stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException();
            }
            for (Long value : Arrays.asList(result.budget(), result.purchasePrice(), result.availableBalance())) {
                if (value != null && (value < 0 || value > 1000000000000L)) {
                    throw new IllegalArgumentException();
                }
            }
            return result;
        } catch (RuntimeException e) {
            throw invalidResponse();
        }
    }

    public String explain(String question, Object evidence) {
        var schema = Map.of("type", "object", "additionalProperties", false,
            "required", List.of("answer"), "properties", Map.of("answer", Map.of("type", "string")));
        var result = call(explanationPrompt,
            List.of(Map.of("role", "user", "content", json.writeValueAsString(
                Map.of("question", question, "evidence", evidence)))), "expense_explanation", schema);
        if (!result.path("answer").isTextual() || result.path("answer").asText().isBlank()) {
            throw invalidResponse();
        }
        return result.path("answer").asText();
    }

    private JsonNode call(String instructions, Object input, String name, Object schema) {
        if (settings.openaiKey() == null || settings.openaiKey().isBlank()) {
            throw new AdviceUnavailable(503, "LLM_NOT_CONFIGURED", "OPENAI_API_KEY를 설정해주세요.");
        }
        var body = Map.of(
            "model", settings.openaiModel(), "store", false,
            "instructions", instructions, "input", input,
            "reasoning", Map.of("effort", "none"), "max_output_tokens", 1600,
            "text", Map.of("format", Map.of("type", "json_schema", "name", name,
                "strict", true, "schema", schema))
        );
        var response = http.post(settings.openaiBaseUrl().replaceAll("/+$", "") + "/responses",
            settings.openaiKey(), body, "OPENAI");
        if (!"completed".equals(response.path("status").asText())) {
            throw invalidResponse();
        }
        for (var output : response.path("output")) {
            if (!"message".equals(output.path("type").asText())) {
                continue;
            }
            for (var content : output.path("content")) {
                if ("refusal".equals(content.path("type").asText())) {
                    throw new AdviceUnavailable(422, "LLM_REFUSED", "질문을 소비 상담 내용으로 다시 작성해주세요.");
                }
                if ("output_text".equals(content.path("type").asText())) {
                    try {
                        return json.readTree(content.path("text").asText());
                    } catch (RuntimeException e) {
                        throw invalidResponse();
                    }
                }
            }
        }
        throw invalidResponse();
    }

    private AdviceUnavailable invalidResponse() {
        return new AdviceUnavailable(502, "LLM_INVALID_RESPONSE", "LLM의 구조화 응답을 확인할 수 없습니다.");
    }

    private static String resource(String path) {
        try (var stream = new ClassPathResource(path).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("프롬프트 파일을 읽을 수 없습니다: " + path, e);
        }
    }
}
