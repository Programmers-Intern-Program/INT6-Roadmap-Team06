package com.back.coach.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String secret,
        long accessTtlSeconds,
        long refreshTtlSeconds
) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("security.jwt.secret must not be blank");
        }
        if (accessTtlSeconds <= 0) {
            throw new IllegalArgumentException("security.jwt.access-ttl-seconds must be positive");
        }
        if (refreshTtlSeconds <= 0) {
            throw new IllegalArgumentException("security.jwt.refresh-ttl-seconds must be positive");
        }
    }
}
