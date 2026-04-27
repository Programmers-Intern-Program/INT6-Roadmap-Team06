package com.back.coach.external.llm;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiGatewayProperties.class)
public class AiGatewayConfig {
}
