package com.back.coach.global.security.oauth2;

import com.back.coach.global.security.CookieManager;
import com.back.coach.global.security.JwtProperties;
import com.back.coach.service.auth.AuthService;
import com.back.coach.service.auth.GithubUserInfo;
import com.back.coach.service.auth.OAuthLoginResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CoachOAuth2LoginSuccessHandlerTest {

    @Mock AuthService authService;
    @Mock OAuth2AuthorizedClientService authorizedClientService;

    CoachOAuth2LoginSuccessHandler handler;

    JwtProperties jwt = new JwtProperties("secret-secret-secret-secret-secret-secret-secret", 900, 86400);
    CookieManager cookieManager = new CookieManager(false, "");

    @BeforeEach
    void setUp() {
        handler = new CoachOAuth2LoginSuccessHandler(
                authService, authorizedClientService, jwt, cookieManager,
                "https://app.example.com"
        );
    }

    @Test
    @DisplayName("성공 시 access/refresh Set-Cookie 헤더를 SameSite=Lax로 발급하고 기본 URL로 redirect")
    void onSuccess_setsCookiesAndRedirectsToDefault() throws Exception {
        OAuth2AuthenticationToken token = githubToken(Map.of(
                "id", 12345L, "login", "alice", "email", "alice@example.com"
        ));
        givenGithubAccessToken(token, "ghp_token_xyz");
        given(authService.loginWithGithub(any(GithubUserInfo.class), eq("ghp_token_xyz")))
                .willReturn(new OAuthLoginResult(7L, "access-jwt", "refresh-jwt"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, token);

        assertThat(response.getRedirectedUrl()).isEqualTo("https://app.example.com");

        List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(2);
        String access = findCookie(cookies, "accessToken");
        assertThat(access)
                .contains("accessToken=access-jwt")
                .contains("Max-Age=900")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
        String refresh = findCookie(cookies, "refreshToken");
        assertThat(refresh)
                .contains("refreshToken=refresh-jwt")
                .contains("Max-Age=86400")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }

    @Test
    @DisplayName("state로 전달된 redirectUrl이 있으면 그 URL로 redirect한다")
    void onSuccess_redirectsToStateRedirectUrl() throws Exception {
        OAuth2AuthenticationToken token = githubToken(Map.of("id", 1L, "login", "u"));
        givenGithubAccessToken(token, "tok");
        given(authService.loginWithGithub(any(GithubUserInfo.class), eq("tok")))
                .willReturn(new OAuthLoginResult(1L, "a", "r"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("state", OAuth2State.encode("https://app.example.com/dashboard?from=login"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, token);

        assertThat(response.getRedirectedUrl()).isEqualTo("https://app.example.com/dashboard?from=login");
    }

    @Test
    @DisplayName("state가 비어있거나 손상되었으면 기본 URL로 fallback")
    void onSuccess_blankOrMalformedState_fallsBackToDefault() throws Exception {
        OAuth2AuthenticationToken token = githubToken(Map.of("id", 2L, "login", "v"));
        givenGithubAccessToken(token, "t");
        given(authService.loginWithGithub(any(GithubUserInfo.class), eq("t")))
                .willReturn(new OAuthLoginResult(2L, "a", "r"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("state", "this-is-not-a-valid-state");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, token);

        assertThat(response.getRedirectedUrl()).isEqualTo("https://app.example.com");
    }

    @Test
    @DisplayName("AuthService에 GitHub 사용자 정보와 access token을 전달한다")
    void onSuccess_passesGithubUserInfoAndAccessTokenToAuthService() throws Exception {
        OAuth2AuthenticationToken token = githubToken(Map.of(
                "id", 999L, "login", "zoe", "email", "zoe@example.com"
        ));
        givenGithubAccessToken(token, "ghp_abc");
        given(authService.loginWithGithub(any(GithubUserInfo.class), eq("ghp_abc")))
                .willReturn(new OAuthLoginResult(11L, "a", "r"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, token);

        ArgumentCaptor<GithubUserInfo> captor = ArgumentCaptor.forClass(GithubUserInfo.class);
        org.mockito.Mockito.verify(authService).loginWithGithub(captor.capture(), eq("ghp_abc"));
        assertThat(captor.getValue().providerUserId()).isEqualTo("999");
        assertThat(captor.getValue().login()).isEqualTo("zoe");
        assertThat(captor.getValue().email()).isEqualTo("zoe@example.com");
    }

    private void givenGithubAccessToken(OAuth2AuthenticationToken token, String tokenValue) {
        OAuth2AccessToken at = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                tokenValue,
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
        OAuth2AuthorizedClient authorizedClient = org.mockito.Mockito.mock(OAuth2AuthorizedClient.class);
        given(authorizedClient.getAccessToken()).willReturn(at);
        given(authorizedClientService.loadAuthorizedClient(
                token.getAuthorizedClientRegistrationId(),
                token.getName()
        )).willReturn(authorizedClient);
    }

    private static OAuth2AuthenticationToken githubToken(Map<String, Object> attrs) {
        OAuth2User principal = new OAuth2User() {
            @Override public Map<String, Object> getAttributes() { return attrs; }
            @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
            @Override public String getName() { return String.valueOf(attrs.get("id")); }
        };
        return new OAuth2AuthenticationToken(principal, List.of(), "github");
    }

    private static String findCookie(List<String> headers, String name) {
        return headers.stream()
                .filter(h -> h.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("cookie '" + name + "' not in Set-Cookie headers"));
    }

    private static <T> T any(Class<T> type) {
        return org.mockito.ArgumentMatchers.any(type);
    }
}
