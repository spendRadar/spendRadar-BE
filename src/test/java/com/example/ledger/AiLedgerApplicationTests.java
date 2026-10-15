package com.example.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "app.ai.openai-key=", "app.ai.chronos-url=",
    "spring.datasource.url=jdbc:h2:mem:context;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AiLedgerApplicationTests {

    @Test
    void contextLoads() {}
}
