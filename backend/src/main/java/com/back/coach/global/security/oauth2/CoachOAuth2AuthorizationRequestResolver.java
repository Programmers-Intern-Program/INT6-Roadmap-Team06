package com.back.coach.global.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * 프론트엔드가 보낸 {@code ?redirectUrl=...} 을 {@link OAuth2State} 로 인코딩하여
 * OAuth2 표준 {@code state} 파라미터에 실어 보낸다. 콜백 핸들러는 같은 state를
 * 디코딩해 원래 페이지로 리다이렉트할 수 있다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.security.oauth2.client.registration.github", name = "client-id")
public class CoachOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public CoachOAuth2AuthorizationRequestResolver(ClientRegistrationRepository repo) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                repo,
                OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI
        );
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest req = delegate.resolve(request);
        return req == null ? null : withEncodedState(req, request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest req = delegate.resolve(request, clientRegistrationId);
        return req == null ? null : withEncodedState(req, request);
    }

    private OAuth2AuthorizationRequest withEncodedState(OAuth2AuthorizationRequest req, HttpServletRequest request) {
        String redirectUrl = request.getParameter("redirectUrl");
        String encoded = OAuth2State.encode(redirectUrl == null ? "" : redirectUrl);
        return OAuth2AuthorizationRequest.from(req).state(encoded).build();
    }
}
