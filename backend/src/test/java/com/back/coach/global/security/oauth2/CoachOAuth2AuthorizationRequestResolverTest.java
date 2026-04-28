package com.back.coach.global.security.oauth2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;

class CoachOAuth2AuthorizationRequestResolverTest {

    private CoachOAuth2AuthorizationRequestResolver resolver;

    @BeforeEach
    void setUp() {
        ClientRegistration github = ClientRegistration.withRegistrationId("github")
                .clientId("test-client")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/github")
                .scope("read:user", "user:email")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("id")
                .clientName("GitHub")
                .build();
        ClientRegistrationRepository repo = new InMemoryClientRegistrationRepository(github);
        resolver = new CoachOAuth2AuthorizationRequestResolver(repo);
    }

    @Test
    @DisplayName("?redirectUrl=... 가 state에 OAuth2State JSON 으로 인코딩된다")
    void resolve_encodesRedirectUrlIntoState() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        request.setRequestURI("/oauth2/authorization/github");
        request.setParameter("redirectUrl", "https://app.example.com/dashboard");

        OAuth2AuthorizationRequest req = resolver.resolve(request);

        assertThat(req).isNotNull();
        OAuth2State decoded = OAuth2State.decode(req.getState());
        assertThat(decoded.redirectUrl()).isEqualTo("https://app.example.com/dashboard");
    }

    @Test
    @DisplayName("redirectUrl 이 없으면 빈 문자열로 인코딩된다 (state 자체는 항상 존재)")
    void resolve_blankRedirectUrl_encodesEmpty() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        request.setRequestURI("/oauth2/authorization/github");

        OAuth2AuthorizationRequest req = resolver.resolve(request);

        assertThat(req).isNotNull();
        assertThat(req.getState()).isNotBlank();
        OAuth2State decoded = OAuth2State.decode(req.getState());
        assertThat(decoded.redirectUrl()).isEmpty();
    }

    @Test
    @DisplayName("매칭되지 않는 경로는 null 을 반환한다")
    void resolve_unknownPath_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/random");
        request.setRequestURI("/random");
        assertThat(resolver.resolve(request)).isNull();
    }

    @Test
    @DisplayName("redirectUrl 이 두 번 이상 호출되어도 매번 새로운 state 를 생성하지만 redirectUrl 은 유지")
    void resolve_isIdempotent_perRedirectUrl() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        request.setRequestURI("/oauth2/authorization/github");
        request.setParameter("redirectUrl", "/post-login");

        OAuth2AuthorizationRequest first = resolver.resolve(request);
        OAuth2AuthorizationRequest second = resolver.resolve(request);

        assertThat(OAuth2State.decode(first.getState()).redirectUrl()).isEqualTo("/post-login");
        assertThat(OAuth2State.decode(second.getState()).redirectUrl()).isEqualTo("/post-login");
    }
}
