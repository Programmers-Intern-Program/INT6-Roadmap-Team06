package com.back.coach.external.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "github.api")
public record GithubApiProperties(String baseUrl) {

    public GithubApiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("github.api.base-url must not be blank");
        }
    }
}
