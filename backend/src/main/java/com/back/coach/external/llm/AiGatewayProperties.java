package com.back.coach.external.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.gateway")
public record AiGatewayProperties(
        String apiKey,
        String baseUrl,
        String model,
        Timeout timeout
) {

    public record Timeout(
            java.time.Duration connect,
            java.time.Duration read
    ) {
        public Timeout {
            if (connect == null) connect = java.time.Duration.ofSeconds(5);
            if (read == null) read = java.time.Duration.ofSeconds(120);
        }
    }

    public AiGatewayProperties {
        apiKey = apiKey == null ? "" : apiKey;
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("ai.gateway.base-url must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("ai.gateway.model must not be blank");
        }
        if (timeout == null) timeout = new Timeout(null, null);
    }
}
