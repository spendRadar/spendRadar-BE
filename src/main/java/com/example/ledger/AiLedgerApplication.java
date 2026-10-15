package com.example.ledger;

import com.example.ledger.ai.AiSettings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableConfigurationProperties(AiSettings.class)
public class AiLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiLedgerApplication.class, args);
    }
}
