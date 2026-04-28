package com.back.coach.global.security.oauth2;

import com.back.coach.global.exception.ServiceException;
import com.back.coach.global.security.JwtProperties;
import com.back.coach.service.auth.AuthService;
import com.back.coach.service.auth.GithubUserInfo;
import com.back.coach.service.auth.OAuthLoginResult;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
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

@Component
@ConditionalOnProperty(prefix = "spring.security.oauth2.client.registration.github", name = "client-id")
public class CoachOAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    static final String ACCESS_TOKEN_COOKIE = "accessToken";
    static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final AuthService authService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final JwtProperties jwtProperties;
    private final String defaultRedirectUrl;
    private final boolean secureCookie;

    public CoachOAuth2LoginSuccessHandler(
            AuthService authService,
            OAuth2AuthorizedClientService authorizedClientService,
            JwtProperties jwtProperties,
            @Value("${security.oauth2.frontend-redirect-url}") String defaultRedirectUrl,
            @Value("${security.cookie.secure:false}") boolean secureCookie
    ) {
        this.authService = authService;
        this.authorizedClientService = authorizedClientService;
        this.jwtProperties = jwtProperties;
        this.defaultRedirectUrl = defaultRedirectUrl;
        this.secureCookie = secureCookie;
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

        response.addCookie(buildCookie(ACCESS_TOKEN_COOKIE, result.accessToken(),
                (int) jwtProperties.accessTtlSeconds()));
        response.addCookie(buildCookie(REFRESH_TOKEN_COOKIE, result.refreshToken(),
                (int) jwtProperties.refreshTtlSeconds()));

        response.sendRedirect(resolveTarget(request.getParameter("state")));
    }

    private Cookie buildCookie(String name, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        return cookie;
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
