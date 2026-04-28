package com.back.coach.global.security.oauth2;

import com.back.coach.global.security.CookieManager;
import com.back.coach.global.security.JwtProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CookieOAuth2AuthorizationRequestRepositoryTest {

    private CookieOAuth2AuthorizationRequestRepository repo;

    @BeforeEach
    void setUp() {
        JwtProperties jwt = new JwtProperties("secret-secret-secret-secret-secret-secret-secret", 900, 86400);
        CookieManager cookieManager = new CookieManager(false, "");
        repo = new CookieOAuth2AuthorizationRequestRepository(cookieManager, jwt);
    }

    @Test
    @DisplayName("save → load 라운드트립: 같은 OAuth2AuthorizationRequest를 복원한다")
    void saveThenLoad_roundTrip() {
        OAuth2AuthorizationRequest original = sampleRequest("state-123");

        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        MockHttpServletRequest saveRequest = new MockHttpServletRequest();
        repo.saveAuthorizationRequest(original, saveRequest, saveResponse);

        String cookieHeader = saveResponse.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookieHeader)
                .startsWith("oauth2_auth_request=")
                .contains("HttpOnly")
                .contains("SameSite=Lax");

        String value = extractCookieValue(cookieHeader);
        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setCookies(new Cookie("oauth2_auth_request", value));

        OAuth2AuthorizationRequest restored = repo.loadAuthorizationRequest(loadRequest);
        assertThat(restored).isNotNull();
        assertThat(restored.getState()).isEqualTo("state-123");
        assertThat(restored.getClientId()).isEqualTo("test-client");
        assertThat(restored.getAuthorizationUri()).isEqualTo("https://github.com/login/oauth/authorize");
    }

    @Test
    @DisplayName("쿠키 없으면 null")
    void load_returnsNull_whenNoCookie() {
        assertThat(repo.loadAuthorizationRequest(new MockHttpServletRequest())).isNull();
    }

    @Test
    @DisplayName("HMAC가 일치하지 않게 변조된 쿠키는 null (예외 없이 거부)")
    void load_returnsNull_whenTampered() {
        OAuth2AuthorizationRequest original = sampleRequest("state-x");
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repo.saveAuthorizationRequest(original, new MockHttpServletRequest(), saveResponse);

        String cookieValue = extractCookieValue(saveResponse.getHeader(HttpHeaders.SET_COOKIE));
        // 마지막 한 글자 변조 → 서명 mismatch
        String tampered = cookieValue.substring(0, cookieValue.length() - 1)
                + (cookieValue.charAt(cookieValue.length() - 1) == 'A' ? 'B' : 'A');

        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setCookies(new Cookie("oauth2_auth_request", tampered));

        assertThat(repo.loadAuthorizationRequest(loadRequest)).isNull();
    }

    @Test
    @DisplayName("remove: 저장된 요청을 반환하고 쿠키를 만료시킨다")
    void remove_returnsSavedAndClearsCookie() {
        OAuth2AuthorizationRequest original = sampleRequest("state-r");
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repo.saveAuthorizationRequest(original, new MockHttpServletRequest(), saveResponse);
        String value = extractCookieValue(saveResponse.getHeader(HttpHeaders.SET_COOKIE));

        MockHttpServletRequest removeRequest = new MockHttpServletRequest();
        removeRequest.setCookies(new Cookie("oauth2_auth_request", value));
        MockHttpServletResponse removeResponse = new MockHttpServletResponse();

        OAuth2AuthorizationRequest removed = repo.removeAuthorizationRequest(removeRequest, removeResponse);
        assertThat(removed).isNotNull();
        assertThat(removed.getState()).isEqualTo("state-r");
        assertThat(removeResponse.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    @Test
    @DisplayName("save with null request: 쿠키를 만료시킨다")
    void save_nullRequest_clearsCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        repo.saveAuthorizationRequest(null, new MockHttpServletRequest(), response);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    private static OAuth2AuthorizationRequest sampleRequest(String state) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://github.com/login/oauth/authorize")
                .clientId("test-client")
                .redirectUri("http://localhost:8080/login/oauth2/code/github")
                .scopes(java.util.Set.of("read:user", "user:email"))
                .state(state)
                .additionalParameters(Map.of())
                .attributes(Map.of("registration_id", "github"))
                .authorizationRequestUri("https://github.com/login/oauth/authorize?state=" + state)
                .build();
    }

    @SuppressWarnings("unused")
    private static AuthorizationGrantType _force_import = AuthorizationGrantType.AUTHORIZATION_CODE;

    private static String extractCookieValue(String setCookieHeader) {
        int eq = setCookieHeader.indexOf('=');
        int semi = setCookieHeader.indexOf(';');
        return setCookieHeader.substring(eq + 1, semi);
    }
}
