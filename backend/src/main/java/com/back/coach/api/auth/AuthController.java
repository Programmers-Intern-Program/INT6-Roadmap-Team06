package com.back.coach.api.auth;

import com.back.coach.api.auth.dto.MeResponse;
import com.back.coach.domain.user.entity.User;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.global.security.AuthenticatedUser;
import com.back.coach.global.security.JwtProperties;
import com.back.coach.global.security.JwtTokenProvider;
import com.back.coach.service.auth.AuthService;
import com.back.coach.service.auth.TokenPair;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    static final String ACCESS_TOKEN_COOKIE = "accessToken";
    static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final boolean secureCookie;

    public AuthController(AuthService authService,
                          JwtTokenProvider jwtTokenProvider,
                          JwtProperties jwtProperties,
                          @Value("${security.cookie.secure:false}") boolean secureCookie) {
        this.authService = authService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.secureCookie = secureCookie;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
        User user = authService.loadUser(principal.userId());
        return new MeResponse(user.getId(), user.getEmail(), user.getAuthProvider());
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            @CookieValue(value = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
        Long userId = jwtTokenProvider.parseRefreshToken(refreshToken).userId();
        TokenPair pair = authService.issueTokens(userId);
        response.addCookie(buildCookie(ACCESS_TOKEN_COOKIE, pair.accessToken(),
                (int) jwtProperties.accessTtlSeconds()));
        response.addCookie(buildCookie(REFRESH_TOKEN_COOKIE, pair.refreshToken(),
                (int) jwtProperties.refreshTtlSeconds()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        response.addCookie(expiredCookie(ACCESS_TOKEN_COOKIE));
        response.addCookie(expiredCookie(REFRESH_TOKEN_COOKIE));
        return ResponseEntity.noContent().build();
    }

    private Cookie buildCookie(String name, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        return cookie;
    }

    private Cookie expiredCookie(String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        return cookie;
    }
}
