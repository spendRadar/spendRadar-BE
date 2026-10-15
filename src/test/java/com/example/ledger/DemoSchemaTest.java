package com.example.ledger;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class DemoSchemaTest {
    @Test
    void preservesExistingRowsAndAllowsNullIncomeKind() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:legacy-schema")) {
            var script = new ClassPathResource("db/demo-schema.sql");
            ScriptUtils.executeSqlScript(connection, script);
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE ledger(id BIGINT PRIMARY KEY, expense_kind VARCHAR(16) NOT NULL)");
                statement.execute("INSERT INTO ledger VALUES (1, 'FIXED')");
                ScriptUtils.executeSqlScript(connection, script);
                ScriptUtils.executeSqlScript(connection, script);
                statement.execute("INSERT INTO ledger VALUES (2, NULL)");
                try (var result = statement.executeQuery("SELECT expense_kind FROM ledger WHERE id=1")) {
                    assertTrue(result.next());
                    assertEquals("FIXED", result.getString(1));
                }
            }
        }
    }
}
