package com.example.ledger.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;

@ConfigurationProperties("app.ai")
@Validated
public record AiSettings(
    String openaiKey, @NotBlank String openaiModel, @NotBlank String openaiBaseUrl,
    String chronosUrl, String chronosToken,
    @Min(1) @Max(900) int timeoutSeconds,
    @Min(90) @Max(1826) int minHistoryDays
) {}
