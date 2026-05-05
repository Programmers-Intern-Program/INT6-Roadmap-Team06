package com.back.coach.external.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "github.api")
public record GithubApiProperties(
        String baseUrl,
        String oauthBaseUrl,
        String clientId,
        String clientSecret,
        String connectionClientId,
        String connectionClientSecret
) {
    public GithubApiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("github.api.base-url must not be blank");
        }
        if (oauthBaseUrl == null || oauthBaseUrl.isBlank()) {
            throw new IllegalArgumentException("github.api.oauth-base-url must not be blank");
        }
        clientId = clientId == null ? "" : clientId;
        clientSecret = clientSecret == null ? "" : clientSecret;
        connectionClientId = isBlank(connectionClientId) ? clientId : connectionClientId;
        connectionClientSecret = isBlank(connectionClientSecret) ? clientSecret : connectionClientSecret;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
