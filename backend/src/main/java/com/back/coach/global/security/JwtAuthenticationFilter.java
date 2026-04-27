package com.back.coach.global.security;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorization.startsWith(BEARER_PREFIX)) {
            responseWriter.write(request, response, ErrorCode.AUTH_INVALID_TOKEN);
            return;
        }

        try {
            AuthenticatedUser user = tokenProvider.parseAccessToken(authorization.substring(BEARER_PREFIX.length()).trim());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (ServiceException e) {
            SecurityContextHolder.clearContext();
            responseWriter.write(request, response, e.getErrorCode(), e.getMessage());
        }
    }
}
