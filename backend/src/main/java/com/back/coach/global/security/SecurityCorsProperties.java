package com.back.coach.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "security.cors")
public record SecurityCorsProperties(
        List<String> allowedOrigins
) {

    private static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of("http://localhost:3000");

    public SecurityCorsProperties {
        allowedOrigins = normalize(allowedOrigins);
    }

    private static List<String> normalize(List<String> origins) {
        if (origins == null || origins.isEmpty()) {
            return DEFAULT_ALLOWED_ORIGINS;
        }
        List<String> normalized = origins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(String::trim)
                .toList();
        return normalized.isEmpty() ? DEFAULT_ALLOWED_ORIGINS : normalized;
    }
}
