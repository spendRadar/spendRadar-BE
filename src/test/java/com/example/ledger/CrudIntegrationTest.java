package com.example.ledger;

import static org.junit.jupiter.api.Assertions.*;

import com.example.ledger.ledger.LedgerRepository;
import com.example.ledger.recurring.*;
import java.net.*;
import java.net.http.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "app.ai.openai-key=", "app.ai.chronos-url=",
        "spring.datasource.url=jdbc:h2:mem:integration;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    }
)
class CrudIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    LedgerRepository ledgers;

    @Autowired
    RecurringIncomeRepository incomes;

    @Autowired
    FixedExpenseRepository expenses;

    final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void clean() {
        ledgers.deleteAll();
        incomes.deleteAll();
        expenses.deleteAll();
    }

    HttpResponse<String> call(String method, String path, String body) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header(
            "Content-Type",
            "application/json"
        );
        return client.send(
            b
                .method(
                    method,
                    body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)
                )
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    long id(String body) {
        var m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(body);
        assertTrue(m.find());
        return Long.parseLong(m.group(1));
    }

    @Test
    void chatWithoutKeyReportsConfigurationAndValidatesInput() throws Exception {
        var response = call("POST", "/api/chat", "{\"message\":\"이번 달 지출은?\"}");
        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("LLM_NOT_CONFIGURED"));
        assertEquals(400, call("POST", "/api/chat", "{\"message\":\"\"}").statusCode());
    }

    @Test
    void monthlyForecastRequiresHistoryConfirmation() throws Exception {
        var response = call("POST", "/api/forecasts/monthly",
            "{\"month\":\"2026-10\",\"historyStart\":\"2023-01-01\",\"historyComplete\":false}");
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("historyComplete"));
    }

    String entry(String date, long amount, String type, String kind) {
        return """
        {"date":"%s","amount":%d,"category":"OTHER","type":"%s","expenseKind":"%s","description":"test"}
        """.formatted(date, amount, type, kind);
    }

    @Test
    void ledgerLifecycle() throws Exception {
        var r = call("POST", "/api/ledgers", entry("2026-01-10", 5000, "EXPENSE", "VARIABLE"));
        assertEquals(201, r.statusCode());
        long id = id(r.body());
        assertEquals(200, call("GET", "/api/ledgers/" + id, null).statusCode());
        assertTrue(call("GET", "/api/ledgers?month=2026-01", null).body().contains("5000"));
        assertEquals("[]", call("GET", "/api/ledgers?month=2026-02", null).body());
        assertEquals(
            200,
            call("PUT", "/api/ledgers/" + id, entry("2026-01-10", 8000, "EXPENSE", "FIXED")).statusCode()
        );
        assertTrue(
            call("GET", "/api/ledgers/" + id, null)
                .body()
                .contains("8000")
        );
        assertEquals(204, call("DELETE", "/api/ledgers/" + id, null).statusCode());
        assertEquals(404, call("GET", "/api/ledgers/" + id, null).statusCode());
    }

    @Test
    void monthlyTotalsExcludeSettingsAndOtherMonths() throws Exception {
        call("POST", "/api/ledgers", entry("2026-01-01", 100000, "INCOME", "VARIABLE"));
        call("POST", "/api/ledgers", entry("2026-01-31", 20000, "EXPENSE", "FIXED"));
        call("POST", "/api/ledgers", entry("2026-01-15", 3000, "EXPENSE", "VARIABLE"));
        call("POST", "/api/ledgers", entry("2026-02-01", 99999, "EXPENSE", "VARIABLE"));
        call(
            "POST",
            "/api/recurring-incomes",
            "{\"name\":\"salary\",\"amount\":900000,\"paymentDay\":1,\"active\":true}"
        );
        String s = call("GET", "/api/ledgers/summary?month=2026-01", null).body();
        for (String expected : new String[] {
            "\"income\":100000",
            "\"expense\":23000",
            "\"balance\":77000",
            "\"fixedExpense\":20000",
            "\"variableExpense\":3000"
        }) {
            assertTrue(s.contains(expected), s);
        }
    }

    @Test
    void recurringCrud() throws Exception {
        for (String path : new String[] { "/api/recurring-incomes", "/api/fixed-expenses" }) {
            String cat = path.contains("fixed") ? ",\"category\":\"HOUSING\"" : "";
            String body = "{\"name\":\"monthly\",\"amount\":700000,\"paymentDay\":25,\"active\":true" + cat + "}";
            var r = call("POST", path, body);
            assertEquals(201, r.statusCode());
            long id = id(r.body());
            assertEquals(200, call("GET", path + "/" + id, null).statusCode());
            assertTrue(call("GET", path, null).body().contains("monthly"));
            assertEquals(
                200,
                call("PUT", path + "/" + id, body.replace("700000", "600000").replace("true", "false")).statusCode()
            );
            assertTrue(
                call("GET", path + "/" + id, null)
                    .body()
                    .contains("\"active\":false")
            );
            assertEquals(204, call("DELETE", path + "/" + id, null).statusCode());
            assertEquals(404, call("GET", path + "/" + id, null).statusCode());
        }
    }

    @Test
    void invalidInputAndMissingItems() throws Exception {
        assertEquals(400, call("POST", "/api/ledgers", entry("2026-01-01", 0, "EXPENSE", "VARIABLE")).statusCode());
        assertEquals(400, call("POST", "/api/ledgers", entry("bad-date", 1, "EXPENSE", "VARIABLE")).statusCode());
        assertEquals(400, call("POST", "/api/ledgers", "{}").statusCode());
        assertEquals(400, call("GET", "/api/ledgers?month=bad", null).statusCode());
        assertEquals(
            400,
            call(
                "POST",
                "/api/recurring-incomes",
                "{\"name\":\"a\",\"amount\":1,\"paymentDay\":31,\"active\":true}"
            ).statusCode()
        );
        assertEquals(
            404,
            call("PUT", "/api/ledgers/999999", entry("2026-01-01", 1, "EXPENSE", "VARIABLE")).statusCode()
        );
    }

    @Test
    void incomeDoesNotRequireExpenseKindButExpenseDoes() throws Exception {
        String income = """
            {"date":"2026-01-01","amount":100000,"category":"SALARY","type":"INCOME"}
            """;
        var created = call("POST", "/api/ledgers", income);
        assertEquals(201, created.statusCode());
        assertTrue(created.body().contains("\"expenseKind\":null"));
        long itemId = id(created.body());
        assertEquals(400, call("PUT", "/api/ledgers/" + itemId,
            income.replace("INCOME", "EXPENSE")).statusCode());
        assertEquals(200, call("PUT", "/api/ledgers/" + itemId,
            entry("2026-01-01", 1000, "EXPENSE", "FIXED")).statusCode());
        var updated = call("PUT", "/api/ledgers/" + itemId, income);
        assertEquals(200, updated.statusCode());
        assertTrue(updated.body().contains("\"expenseKind\":null"));
    }

    @Test
    void dailyExpensesExcludeFixedIncomeAndEndDate() throws Exception {
        call("POST", "/api/ledgers", entry("2026-01-01", 1000, "EXPENSE", "VARIABLE"));
        call("POST", "/api/ledgers", entry("2026-01-01", 2000, "EXPENSE", "VARIABLE"));
        call("POST", "/api/ledgers", entry("2026-01-01", 9000, "EXPENSE", "FIXED"));
        call("POST", "/api/ledgers", entry("2026-01-01", 100000, "INCOME", "VARIABLE"));
        call("POST", "/api/ledgers", entry("2026-01-03", 9999, "EXPENSE", "VARIABLE"));
        var result = call("GET", "/api/ledgers/daily-expenses?start=2026-01-01&end=2026-01-03", null);
        assertEquals(200, result.statusCode());
        var mapper = new tools.jackson.databind.json.JsonMapper();
        var expected = mapper.readTree("""
            [
                {"date":"2026-01-01","recordedVariableExpense":3000,"transactionCount":4},
                {"date":"2026-01-02","recordedVariableExpense":0,"transactionCount":0}
            ]
            """);
        assertEquals(expected, mapper.readTree(result.body()));
        assertEquals(400, call("GET",
            "/api/ledgers/daily-expenses?start=2026-01-03&end=2026-01-01", null).statusCode());
        assertEquals(400, call("GET",
            "/api/ledgers/daily-expenses?start=2026-01-01&end=2028-01-01", null).statusCode());
        assertEquals(400, call("GET",
            "/api/ledgers/daily-expenses?start=bad&end=2026-01-03", null).statusCode());
    }

    @Test
    void adviceRejectsInvalidInputAndReportsMissingModel() throws Exception {
        var result = call("POST", "/api/purchase-advice", """
            {"itemName":"헤드폰","purchasePrice":300000,"availableBalance":1000000}
            """);
        assertEquals(503, result.statusCode());
        assertTrue(result.body().contains("MODEL_NOT_CONFIGURED"));
        assertEquals(400, call("POST", "/api/purchase-advice",
            "{\"purchasePrice\":0,\"availableBalance\":1000}").statusCode());
        assertEquals(400, call("POST", "/api/purchase-advice",
            "{\"purchasePrice\":100,\"availableBalance\":-1}").statusCode());
    }

    @Test
    void queryErrorsHaveConsistentMessages() throws Exception {
        for (String path : new String[] {
            "/api/ledgers", "/api/ledgers/not-an-id",
            "/api/ledgers/daily-expenses?start=bad&end=2026-01-03"
        }) {
            var result = call("GET", path, null);
            assertEquals(400, result.statusCode());
            assertTrue(result.body().contains("message"));
        }
        var result = call("POST", "/api/ledgers", """
            {"date":"2026-01-01","amount":1000,"category":"FOOD","type":"EXPENSE"}
            """);
        assertEquals(400, result.statusCode());
        assertTrue(result.body().contains("\"expenseKind\":"));
    }

    @Test
    void categoryHistorySeparatesCategoriesAndExpenseKinds() throws Exception {
        assertEquals(201, call("POST", "/api/ledgers",
            entry("2024-02-28", 1000, "EXPENSE", "VARIABLE").replace("OTHER", "FOOD")).statusCode());
        call("POST", "/api/ledgers", entry("2024-02-28", 2000, "EXPENSE", "VARIABLE").replace("OTHER", "FOOD"));
        call("POST", "/api/ledgers", entry("2024-02-28", 5000, "EXPENSE", "FIXED").replace("OTHER", "FOOD"));
        call("POST", "/api/ledgers", entry("2024-02-28", 90000, "INCOME", "VARIABLE").replace("OTHER", "FOOD"));
        call("POST", "/api/ledgers", entry("2024-02-28", 7000, "EXPENSE", "VARIABLE").replace("OTHER", "SHOPPING"));
        call("POST", "/api/ledgers", entry("2024-03-01", 9999, "EXPENSE", "VARIABLE").replace("OTHER", "FOOD"));
        var response = call("GET", "/api/ledgers/category-daily-expenses?start=2024-02-28&end=2024-03-01&categories=FOOD,SHOPPING", null);
        assertEquals(200, response.statusCode());
        var mapper = new tools.jackson.databind.json.JsonMapper();
        assertEquals(mapper.readTree("""
            [
              {"date":"2024-02-28","category":"FOOD","recordedVariableExpense":3000,"recordedFixedExpense":5000,"expenseTransactionCount":3},
              {"date":"2024-02-28","category":"SHOPPING","recordedVariableExpense":7000,"recordedFixedExpense":0,"expenseTransactionCount":1},
              {"date":"2024-02-29","category":"FOOD","recordedVariableExpense":0,"recordedFixedExpense":0,"expenseTransactionCount":0},
              {"date":"2024-02-29","category":"SHOPPING","recordedVariableExpense":0,"recordedFixedExpense":0,"expenseTransactionCount":0}
            ]
            """), mapper.readTree(response.body()));
    }

    @Test
    void categoryHistorySupportsMultipleYearsAndValidatesFilters() throws Exception {
        var mapper = new tools.jackson.databind.json.JsonMapper();
        var response = call("GET", "/api/ledgers/category-daily-expenses?start=2022-01-01&end=2025-01-01&categories=FOOD,FOOD", null);
        assertEquals(200, response.statusCode());
        assertEquals(1096, mapper.readTree(response.body()).size());
        var all = call("GET", "/api/ledgers/category-daily-expenses?start=2025-01-01&end=2025-01-02", null);
        assertEquals(200, all.statusCode());
        assertEquals(8, mapper.readTree(all.body()).size());
        for (String query : new String[] {
            "start=2025-01-01&end=2025-01-01",
            "start=2025-01-02&end=2025-01-01",
            "start=2020-01-01&end=2026-01-01",
            "start=2025-01-01&end=2025-01-02&categories=UNKNOWN"
        }) {
            assertEquals(400, call("GET", "/api/ledgers/category-daily-expenses?" + query, null).statusCode());
        }
    }
}
