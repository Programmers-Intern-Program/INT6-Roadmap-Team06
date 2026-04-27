package com.back.coach.external.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AiGatewayProperties.class)
public class AiGatewayConfig {

    @Bean
    @ConditionalOnMissingBean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
