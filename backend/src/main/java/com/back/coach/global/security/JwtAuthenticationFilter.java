package com.back.coach.global.security;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    static final String ACCESS_TOKEN_COOKIE = "accessToken";

    private final JwtTokenProvider tokenProvider;
    private final SecurityErrorResponseWriter responseWriter;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, SecurityErrorResponseWriter responseWriter) {
        this.tokenProvider = tokenProvider;
        this.responseWriter = responseWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String accessToken;
        try {
            accessToken = extractAccessToken(request);
        } catch (ServiceException e) {
            SecurityContextHolder.clearContext();
            responseWriter.write(request, response, e.getErrorCode(), e.getMessage());
            return;
        }
        if (accessToken == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            AuthenticatedUser user = tokenProvider.parseAccessToken(accessToken);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (ServiceException e) {
            SecurityContextHolder.clearContext();
            responseWriter.write(request, response, e.getErrorCode(), e.getMessage());
        }
    }

    private String extractAccessToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && !authorization.isBlank()) {
            if (!authorization.startsWith(BEARER_PREFIX)) {
                throw new ServiceException(ErrorCode.AUTH_INVALID_TOKEN);
            }
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                String value = cookie.getValue();
                return (value == null || value.isBlank()) ? null : value;
            }
        }
        return null;
    }
}
