package com.back.coach.global.security.oauth2;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * GitHub OAuth2 로그인 플로우 전용 SecurityFilterChain.
 *
 * <p>{@code /oauth2/**} 와 {@code /login/oauth2/**} 만 매칭하며 기본 JWT 체인보다 먼저 동작한다.
 * STATELESS 세션 정책이므로 인증 요청 객체는 {@link CookieOAuth2AuthorizationRequestRepository}
 * 에 저장된다.
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.security.oauth2.client.registration.github", name = "client-id")
public class OAuth2LoginSecurityConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain oauth2LoginFilterChain(
            HttpSecurity http,
            CoachOAuth2UserService userService,
            CoachOAuth2LoginSuccessHandler successHandler,
            CoachOAuth2LoginFailureHandler failureHandler,
            CoachOAuth2AuthorizationRequestResolver authorizationRequestResolver,
            CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository
    ) throws Exception {
        http
                .securityMatcher("/oauth2/**", "/login/oauth2/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(authorizationRequestResolver)
                                .authorizationRequestRepository(authorizationRequestRepository)
                        )
                        .userInfoEndpoint(userInfo -> userInfo.userService(userService))
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                );
        return http.build();
    }
}
