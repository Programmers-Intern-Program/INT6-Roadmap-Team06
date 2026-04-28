package com.back.coach.external.github;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GithubApiProperties.class)
public class GithubApiConfig {

    @Bean
    @ConditionalOnMissingBean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
