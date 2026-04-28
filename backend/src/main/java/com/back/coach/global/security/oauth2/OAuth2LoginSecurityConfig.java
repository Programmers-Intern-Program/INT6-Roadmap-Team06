package com.back.coach.global.security.oauth2;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Wires Spring Security's OAuth2 login flow when GitHub credentials are configured.
 *
 * <p>This filter chain has higher priority than the default JWT chain because it must own
 * {@code /oauth2/**} and {@code /login/oauth2/**} routes.
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.security.oauth2.client.registration.github", name = "client-id")
public class OAuth2LoginSecurityConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain oauth2LoginFilterChain(
            HttpSecurity http,
            CoachOAuth2UserService coachOAuth2UserService,
            CoachOAuth2LoginSuccessHandler successHandler
    ) throws Exception {
        http
                .securityMatcher("/oauth2/**", "/login/oauth2/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(coachOAuth2UserService))
                        .successHandler(successHandler)
                );
        return http.build();
    }
}
