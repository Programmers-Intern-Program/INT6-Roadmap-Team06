package com.back.coach.global.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 보안 쿠키 발급/삭제 헬퍼.
 *
 * <p>{@link Cookie} API 는 {@code SameSite} 속성을 지원하지 않아 직접 {@link ResponseCookie}
 * 를 만들어 {@code Set-Cookie} 헤더로 추가한다. 모든 쿠키는
 * {@code HttpOnly; Path=/; SameSite=Lax} 속성을 갖는다.
 */
@Component
public class CookieManager {

    private final boolean secure;
    private final String domain;

    public CookieManager(
            @Value("${security.cookie.secure:false}") boolean secure,
            @Value("${security.cookie.domain:}") String domain
    ) {
        this.secure = secure;
        this.domain = domain;
    }

    public void add(HttpServletResponse response, String name, String value, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(name, value, maxAge).toString());
    }

    public void clear(HttpServletResponse response, String name) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(name, "", Duration.ZERO).toString());
    }

    public String readValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie build(String name, String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("Lax")
                .maxAge(maxAge);
        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }
        return builder.build();
    }
}
