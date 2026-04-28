package com.back.coach.global.security.oauth2;

import com.back.coach.global.exception.ServiceException;
import com.back.coach.global.security.CookieManager;
import com.back.coach.global.security.JwtProperties;
import com.back.coach.service.auth.AuthService;
import com.back.coach.service.auth.GithubUserInfo;
import com.back.coach.service.auth.OAuthLoginResult;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

@Component
@ConditionalOnProperty(prefix = "spring.security.oauth2.client.registration.github", name = "client-id")
public class CoachOAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    static final String ACCESS_TOKEN_COOKIE = "accessToken";
    static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final AuthService authService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final JwtProperties jwtProperties;
    private final CookieManager cookieManager;
    private final String defaultRedirectUrl;

    public CoachOAuth2LoginSuccessHandler(
            AuthService authService,
            OAuth2AuthorizedClientService authorizedClientService,
            JwtProperties jwtProperties,
            CookieManager cookieManager,
            @Value("${security.oauth2.frontend-redirect-url}") String defaultRedirectUrl
    ) {
        this.authService = authService;
        this.authorizedClientService = authorizedClientService;
        this.jwtProperties = jwtProperties;
        this.cookieManager = cookieManager;
        this.defaultRedirectUrl = defaultRedirectUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;

        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                token.getAuthorizedClientRegistrationId(), token.getName());
        String githubAccessToken = client.getAccessToken().getTokenValue();

        GithubUserInfo info = GithubUserInfoMapper.from(token.getPrincipal());
        OAuthLoginResult result = authService.loginWithGithub(info, githubAccessToken);

        cookieManager.add(response, ACCESS_TOKEN_COOKIE, result.accessToken(),
                Duration.ofSeconds(jwtProperties.accessTtlSeconds()));
        cookieManager.add(response, REFRESH_TOKEN_COOKIE, result.refreshToken(),
                Duration.ofSeconds(jwtProperties.refreshTtlSeconds()));

        response.sendRedirect(resolveTarget(request.getParameter("state")));
    }

    private String resolveTarget(String state) {
        if (state == null || state.isBlank()) {
            return defaultRedirectUrl;
        }
        try {
            String redirectUrl = OAuth2State.decode(state).redirectUrl();
            return (redirectUrl == null || redirectUrl.isBlank()) ? defaultRedirectUrl : redirectUrl;
        } catch (ServiceException e) {
            return defaultRedirectUrl;
        }
    }
}
