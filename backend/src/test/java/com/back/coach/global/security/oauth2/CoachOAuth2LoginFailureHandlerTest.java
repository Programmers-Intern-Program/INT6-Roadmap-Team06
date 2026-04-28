package com.back.coach.global.security.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CoachOAuth2LoginFailureHandlerTest {

    private final CoachOAuth2LoginFailureHandler handler =
            new CoachOAuth2LoginFailureHandler("https://app.example.com");

    @Test
    @DisplayName("OAuth2 인증 실패 시 ${frontend}/login?error=<message> 로 redirect")
    void onFailure_redirectsToFrontendLoginWithEncodedMessage() throws Exception {
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                new OAuth2Error("access_denied", "사용자가 인증을 취소했습니다", null));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(request, response, exception);

        String expected = "https://app.example.com/login?error=" +
                URLEncoder.encode("사용자가 인증을 취소했습니다", StandardCharsets.UTF_8);
        assertThat(response.getRedirectedUrl()).isEqualTo(expected);
    }

    @Test
    @DisplayName("일반 AuthenticationException 도 메시지를 인코딩해 FE 로 redirect")
    void onFailure_genericException_usesMessage() throws Exception {
        AuthenticationException exception = new AuthenticationException("일반 인증 실패") {};

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("https://app.example.com/login?error=" +
                        URLEncoder.encode("일반 인증 실패", StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("메시지가 없으면 기본 안내 문구로 redirect")
    void onFailure_blankMessage_usesDefault() throws Exception {
        AuthenticationException exception = new AuthenticationException(null) {};

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getRedirectedUrl())
                .startsWith("https://app.example.com/login?error=");
        assertThat(response.getRedirectedUrl()).contains(URLEncoder.encode("인증 중 오류가 발생했습니다", StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("state에 redirectUrl 이 있어도 실패 시엔 항상 FE login 페이지로 보낸다")
    void onFailure_ignoresStateRedirectUrl() throws Exception {
        AuthenticationException exception = new AuthenticationException("err") {};

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("state", OAuth2State.encode("https://app.example.com/dashboard"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getRedirectedUrl()).startsWith("https://app.example.com/login?error=");
    }
}
