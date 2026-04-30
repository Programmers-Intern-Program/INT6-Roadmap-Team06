package com.back.coach.global.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * OAuth2 로그인 실패(취소/연동 실패 등) 시 백엔드의 {@code /login?error} 가 아닌
 * 프론트엔드 로그인 페이지로 리다이렉트한다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.security.oauth2.client.registration.github", name = "client-id")
public class CoachOAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(CoachOAuth2LoginFailureHandler.class);
    private static final String DEFAULT_MESSAGE = "인증 중 오류가 발생했습니다";

    private final String frontendBaseUrl;

    public CoachOAuth2LoginFailureHandler(
            @Value("${security.oauth2.frontend-redirect-url}") String frontendBaseUrl
    ) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("OAuth2 authentication failed: {}", exception.getMessage());
        String message = extractMessage(exception);
        String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8);
        response.sendRedirect(frontendBaseUrl + "/login?error=" + encoded);
    }

    private String extractMessage(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oae && oae.getError() != null) {
            String description = oae.getError().getDescription();
            if (description != null && !description.isBlank()) {
                return description;
            }
        }
        String msg = exception.getMessage();
        return (msg == null || msg.isBlank()) ? DEFAULT_MESSAGE : msg;
    }
}
