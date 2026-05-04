package com.back.coach.external.github;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GithubApiProperties.class)
public class GithubApiConfig {

    @Bean
    GithubApiClient githubApiClient(GithubApiProperties properties, RestClient.Builder builder) {
        return new RestGithubApiClient(properties, builder);
    }
}
