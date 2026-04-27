package com.back.coach.external.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.gateway")
public record AiGatewayProperties(
        String apiKey,
        String baseUrl,
        String model
) {

    public AiGatewayProperties {
        apiKey = apiKey == null ? "" : apiKey;
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("ai.gateway.base-url must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("ai.gateway.model must not be blank");
        }
    }
}
