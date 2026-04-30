package com.back.coach.global.security;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final JwtProperties PROPERTIES = new JwtProperties(
            "test-secret-key-for-jwt-provider",
            900,
            86_400
    );

    @Test
    void createAccessToken_andParseUserId() {
        JwtTokenProvider provider = new JwtTokenProvider(PROPERTIES);

        String token = provider.createAccessToken(10L);

        assertThat(provider.parseAccessToken(token).userId()).isEqualTo(10L);
    }

    @Test
    void refreshToken_isNotAcceptedAsAccessToken() {
        JwtTokenProvider provider = new JwtTokenProvider(PROPERTIES);
        String token = provider.createRefreshToken(10L);

        assertThatThrownBy(() -> provider.parseAccessToken(token))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
    }

    @Test
    void expiredAccessToken_throwsExpiredTokenError() {
        Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");
        JwtProperties shortTtl = new JwtProperties("test-secret-key-for-expired-token", 1, 60);
        JwtTokenProvider issuer = new JwtTokenProvider(shortTtl, fixedClock(issuedAt));
        JwtTokenProvider parser = new JwtTokenProvider(shortTtl, fixedClock(issuedAt.plusSeconds(2)));

        String token = issuer.createAccessToken(10L);

        assertThatThrownBy(() -> parser.parseAccessToken(token))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_EXPIRED_TOKEN);
    }

    @Test
    void invalidToken_throwsInvalidTokenError() {
        JwtTokenProvider provider = new JwtTokenProvider(PROPERTIES);

        assertThatThrownBy(() -> provider.parseAccessToken("invalid-token"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
    }

    @Test
    void createRefreshToken_andParseUserId() {
        JwtTokenProvider provider = new JwtTokenProvider(PROPERTIES);

        String token = provider.createRefreshToken(42L);

        assertThat(provider.parseRefreshToken(token).userId()).isEqualTo(42L);
    }

    @Test
    void accessToken_isNotAcceptedAsRefreshToken() {
        JwtTokenProvider provider = new JwtTokenProvider(PROPERTIES);
        String token = provider.createAccessToken(10L);

        assertThatThrownBy(() -> provider.parseRefreshToken(token))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
    }

    @Test
    void expiredRefreshToken_throwsExpiredTokenError() {
        Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");
        JwtProperties shortTtl = new JwtProperties("test-secret-key-for-expired-refresh", 60, 1);
        JwtTokenProvider issuer = new JwtTokenProvider(shortTtl, fixedClock(issuedAt));
        JwtTokenProvider parser = new JwtTokenProvider(shortTtl, fixedClock(issuedAt.plusSeconds(2)));

        String token = issuer.createRefreshToken(10L);

        assertThatThrownBy(() -> parser.parseRefreshToken(token))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_EXPIRED_TOKEN);
    }

    @Test
    void invalidRefreshToken_throwsInvalidTokenError() {
        JwtTokenProvider provider = new JwtTokenProvider(PROPERTIES);

        assertThatThrownBy(() -> provider.parseRefreshToken("not-a-token"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
    }

    private static Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}
