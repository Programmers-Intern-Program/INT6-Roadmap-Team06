package com.back.coach.global.security.oauth2;

import com.back.coach.domain.github.entity.GithubConnection;
import com.back.coach.domain.github.repository.GithubConnectionRepository;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@IntegrationTest
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.github.client-id=test-client-id",
        "spring.security.oauth2.client.registration.github.client-secret=test-secret",
        "spring.security.oauth2.client.registration.github.scope=read:user,user:email",
        "security.oauth2.frontend-redirect-url=https://app.test.local"
})
class AuthFlowIntegrationTest {

    @Autowired
    CoachOAuth2LoginSuccessHandler successHandler;

    @Autowired
    UserRepository userRepository;

    @Autowired
    GithubConnectionRepository githubConnectionRepository;

    @MockitoBean
    OAuth2AuthorizedClientService authorizedClientService;

    @Test
    @DisplayName("최초 GitHub OAuth 콜백: User+GithubConnection이 생성되고 access/refresh 쿠키가 설정된다")
    void firstTimeOAuthCallback_persistsAndSetsCookies() throws Exception {
        OAuth2AuthenticationToken token = githubAuth(Map.of(
                "id", 88001L,
                "login", "newbie",
                "email", "newbie@example.com"
        ));
        givenGithubAccessTokenIs(token, "ghp_first_login");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(request, response, token);

        Optional<User> persistedUser = userRepository
                .findByAuthProviderAndProviderUserId(AuthProvider.GITHUB, "88001");
        assertThat(persistedUser).isPresent();
        assertThat(persistedUser.get().getEmail()).isEqualTo("newbie@example.com");

        Optional<GithubConnection> connection = githubConnectionRepository
                .findByUserIdAndGithubUserId(persistedUser.get().getId(), "88001");
        assertThat(connection).isPresent();
        assertThat(connection.get().getGithubLogin()).isEqualTo("newbie");
        assertThat(connection.get().getAccessToken()).isEqualTo("ghp_first_login");

        assertThat(cookieValue(response, "accessToken")).isNotBlank();
        assertThat(cookieValue(response, "refreshToken")).isNotBlank();
        assertThat(response.getRedirectedUrl()).isEqualTo("https://app.test.local");

        // SameSite/HttpOnly 검증
        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).anyMatch(h -> h.startsWith("accessToken=") && h.contains("HttpOnly") && h.contains("SameSite=Lax"));
        assertThat(setCookies).anyMatch(h -> h.startsWith("refreshToken=") && h.contains("HttpOnly") && h.contains("SameSite=Lax"));
    }

    @Test
    @DisplayName("재로그인: 기존 User가 그대로 유지되고 GithubConnection의 access token만 갱신된다")
    void returningOAuthCallback_reusesUser_andRefreshesToken() throws Exception {
        User existing = userRepository.save(
                User.signupFromOAuth(AuthProvider.GITHUB, "88002", "returning@example.com")
        );
        githubConnectionRepository.save(
                GithubConnection.connect(existing.getId(), "88002", "veteran",
                        com.back.coach.global.code.GithubAccessType.OAUTH, "ghp_old_token")
        );

        OAuth2AuthenticationToken token = githubAuth(Map.of(
                "id", 88002L,
                "login", "veteran",
                "email", "returning@example.com"
        ));
        givenGithubAccessTokenIs(token, "ghp_new_token");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("state", OAuth2State.encode("https://app.test.local/dashboard"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(request, response, token);

        long userCount = userRepository.findByAuthProviderAndProviderUserId(AuthProvider.GITHUB, "88002")
                .stream().count();
        assertThat(userCount).isEqualTo(1);

        GithubConnection updated = githubConnectionRepository
                .findByUserIdAndGithubUserId(existing.getId(), "88002")
                .orElseThrow();
        assertThat(updated.getAccessToken()).isEqualTo("ghp_new_token");

        assertThat(response.getRedirectedUrl()).isEqualTo("https://app.test.local/dashboard");
    }

    private void givenGithubAccessTokenIs(OAuth2AuthenticationToken token, String value) {
        OAuth2AccessToken at = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                value,
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
        OAuth2AuthorizedClient authorizedClient = mock(OAuth2AuthorizedClient.class);
        given(authorizedClient.getAccessToken()).willReturn(at);
        given(authorizedClientService.loadAuthorizedClient(
                token.getAuthorizedClientRegistrationId(),
                token.getName()
        )).willReturn(authorizedClient);
    }

    private static OAuth2AuthenticationToken githubAuth(Map<String, Object> attrs) {
        OAuth2User principal = new OAuth2User() {
            @Override public Map<String, Object> getAttributes() { return attrs; }
            @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
            @Override public String getName() { return String.valueOf(attrs.get("id")); }
        };
        return new OAuth2AuthenticationToken(principal, List.of(), "github");
    }

    private static String cookieValue(MockHttpServletResponse response, String name) {
        List<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
        for (String header : headers) {
            if (header.startsWith(name + "=")) {
                int start = name.length() + 1;
                int end = header.indexOf(';', start);
                return end < 0 ? header.substring(start) : header.substring(start, end);
            }
        }
        throw new AssertionError("cookie '" + name + "' not set");
    }
}
