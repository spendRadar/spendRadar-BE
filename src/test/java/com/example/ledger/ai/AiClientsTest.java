package com.example.ledger.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.example.ledger.advice.AdviceUnavailable;
import com.example.ledger.chat.ChatContracts;
import com.example.ledger.forecast.ChronosClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AiClientsTest {
    private HttpServer server;
    private AiSettings settings;
    private final JsonMapper json = new JsonMapper();
    private final AtomicReference<JsonNode> requestBody = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private String response;
    private int status = 200;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestBody.set(json.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        settings = new AiSettings("test-only-key", "gpt-5.4-mini", url, url, "test-service-token", 5, 90);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void serializesDatesAndChecksChronosPredictionCoverage() {
        response = """
            {"modelId":"test-only","predictions":[{"id":"FOOD","date":"2026-10-14","amount":123}]}
            """;
        var client = new ChronosClient(settings, new AiHttpClient(settings));
        var result = client.forecast(List.of(new ChronosClient.Series("FOOD",
            List.of(new ChronosClient.Observation(LocalDate.of(2026, 10, 13), 100)))),
            LocalDate.of(2026, 10, 14), 1);
        assertEquals(123, result.predictions().getFirst().amount());
        assertEquals("2026-10-14", requestBody.get().path("start").asText());
        assertEquals("Bearer test-service-token", auth.get());
        response = """
            {"modelId":"test-only","predictions":[{"id":"OTHER","date":"2026-10-14","amount":123}]}
            """;
        assertThrows(AdviceUnavailable.class, () -> client.forecast(List.of(new ChronosClient.Series("FOOD", List.of())),
            LocalDate.of(2026, 10, 14), 1));
    }

    @Test
    void usesResponsesStructuredOutputsAndParsesTheMessageEnvelope() {
        var analysis = new ChatContracts.Analysis(ChatContracts.Intent.BUDGET_CHECK,
            "2026-10", List.of(), 300000L, null, null, null);
        response = json.writeValueAsString(Map.of("status", "completed", "output", List.of(
            Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", json.writeValueAsString(analysis)))))));
        var result = new OpenAiClient(settings, new AiHttpClient(settings)).interpret(
            new ChatContracts.Request("이번 달 30만원으로 될까?", List.of(), null, false), LocalDate.of(2026, 10, 14));
        assertEquals(300000, result.budget());
        assertFalse(requestBody.get().path("store").asBoolean());
        assertEquals("json_schema", requestBody.get().path("text").path("format").path("type").asText());
        assertTrue(requestBody.get().path("instructions").asText().contains("2026-10-14"));
        assertEquals("Bearer test-only-key", auth.get());
    }

    @Test
    void rejectsIncompleteAndUpstreamErrorsWithoutExposingTheirBody() {
        var client = new OpenAiClient(settings, new AiHttpClient(settings));
        response = "{\"status\":\"incomplete\",\"output\":[]}";
        assertEquals("LLM_INVALID_RESPONSE", assertThrows(AdviceUnavailable.class,
            () -> client.explain("질문", Map.of())).code());
        response = "secret upstream details";
        status = 401;
        var error = assertThrows(AdviceUnavailable.class, () -> client.explain("질문", Map.of()));
        assertFalse(error.getMessage().contains(response));
        assertEquals("OPENAI_UNAVAILABLE", error.code());
    }
}
