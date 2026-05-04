package com.back.coach.support;

import com.back.coach.global.security.JwtTokenProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public final class JwtAuthHelper {

    private JwtAuthHelper() {}

    public static String bearerToken(JwtTokenProvider provider, Long userId) {
        return "Bearer " + provider.createAccessToken(userId);
    }

    public static MockHttpServletRequestBuilder withAuth(
            MockHttpServletRequestBuilder builder,
            JwtTokenProvider provider,
            Long userId) {
        return builder.header(HttpHeaders.AUTHORIZATION, bearerToken(provider, userId));
    }
}
