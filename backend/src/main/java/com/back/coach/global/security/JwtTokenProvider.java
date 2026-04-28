package com.back.coach.global.security;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey key;

    @Autowired
    public JwtTokenProvider(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtTokenProvider(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = createKey(properties.secret());
    }

    public String createAccessToken(Long userId) {
        return createToken(userId, ACCESS_TOKEN_TYPE, properties.accessTtlSeconds());
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, REFRESH_TOKEN_TYPE, properties.refreshTtlSeconds());
    }

    public AuthenticatedUser parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        if (!ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new ServiceException(ErrorCode.AUTH_INVALID_TOKEN, ErrorCode.AUTH_INVALID_TOKEN.getDefaultMessage());
        }
        return new AuthenticatedUser(parseUserId(claims.getSubject()));
    }

    public AuthenticatedUser parseRefreshToken(String token) {
        Claims claims = parseClaims(token);
        if (!REFRESH_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new ServiceException(ErrorCode.AUTH_INVALID_TOKEN, ErrorCode.AUTH_INVALID_TOKEN.getDefaultMessage());
        }
        return new AuthenticatedUser(parseUserId(claims.getSubject()));
    }

    private String createToken(Long userId, String tokenType, long ttlSeconds) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plusSeconds(ttlSeconds);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private Claims parseClaims(String token) {
        if (token == null || token.isBlank()) {
            throw new ServiceException(ErrorCode.AUTH_INVALID_TOKEN, ErrorCode.AUTH_INVALID_TOKEN.getDefaultMessage());
        }
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(Instant.now(clock)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new ServiceException(ErrorCode.AUTH_EXPIRED_TOKEN, ErrorCode.AUTH_EXPIRED_TOKEN.getDefaultMessage());
        } catch (JwtException | IllegalArgumentException e) {
            throw new ServiceException(ErrorCode.AUTH_INVALID_TOKEN, ErrorCode.AUTH_INVALID_TOKEN.getDefaultMessage());
        }
    }

    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            throw new ServiceException(ErrorCode.AUTH_INVALID_TOKEN, ErrorCode.AUTH_INVALID_TOKEN.getDefaultMessage());
        }
    }

    private SecretKey createKey(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is required", e);
        }
    }
}
