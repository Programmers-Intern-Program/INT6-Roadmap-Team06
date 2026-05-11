package com.back.coach.global.security.oauth2;

import com.back.coach.global.security.CookieManager;
import com.back.coach.global.security.JwtProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * STATELESS 환경에서 OAuth2AuthorizationRequest 를 쿠키에 저장한다.
 *
 * <p>형식: {@code base64url(payloadLen | payload | hmacSha256(payload))}.
 * payload 는 Java 직렬화 대신 JSON 으로 저장해 쿠키 크기를 ~400바이트로 유지한다.
 * HMAC 검증 후에만 역직렬화하므로 외부 주입 페이로드로 인한 RCE 위험이 없다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.security.oauth2.client.registration.github", name = "client-id")
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final Logger log = LoggerFactory.getLogger(CookieOAuth2AuthorizationRequestRepository.class);

    static final String COOKIE_NAME = "oauth2_auth_request";
    private static final Duration COOKIE_TTL = Duration.ofMinutes(5);
    private static final String HMAC_ALGO = "HmacSHA256";

    private final CookieManager cookieManager;
    private final SecretKeySpec hmacKey;
    private final ObjectMapper mapper = new ObjectMapper();

    public CookieOAuth2AuthorizationRequestRepository(CookieManager cookieManager, JwtProperties jwt) {
        this.cookieManager = cookieManager;
        this.hmacKey = deriveKey(jwt.secret());
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String value = cookieManager.readValue(request, COOKIE_NAME);
        if (value == null) {
            log.debug("oauth2_auth_request cookie not found in request");
            return null;
        }
        log.debug("oauth2_auth_request cookie found, size={} chars", value.length());
        OAuth2AuthorizationRequest result = tryDeserialize(value);
        if (result == null) {
            log.warn("oauth2_auth_request cookie deserialization failed (HMAC mismatch, truncation, or format error)");
        }
        return result;
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        if (authorizationRequest == null) {
            cookieManager.clear(response, COOKIE_NAME);
            return;
        }
        String serialized = serialize(authorizationRequest);
        log.debug("Saving oauth2_auth_request cookie, size={} chars", serialized.length());
        cookieManager.add(response, COOKIE_NAME, serialized, COOKIE_TTL);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest existing = loadAuthorizationRequest(request);
        cookieManager.clear(response, COOKIE_NAME);
        return existing;
    }

    private String serialize(OAuth2AuthorizationRequest req) {
        try {
            CookiePayload payload = new CookiePayload(
                    req.getAuthorizationUri(),
                    req.getClientId(),
                    req.getRedirectUri(),
                    req.getScopes(),
                    req.getState(),
                    req.getAdditionalParameters(),
                    req.getAttributes(),
                    req.getAuthorizationRequestUri()
            );
            byte[] payloadBytes = mapper.writeValueAsBytes(payload);
            byte[] sig = sign(payloadBytes);
            ByteBuffer buf = ByteBuffer.allocate(4 + payloadBytes.length + sig.length);
            buf.putInt(payloadBytes.length).put(payloadBytes).put(sig);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("OAuth2AuthorizationRequest serialize failed", e);
        }
    }

    private OAuth2AuthorizationRequest tryDeserialize(String cookieValue) {
        byte[] raw;
        try {
            raw = Base64.getUrlDecoder().decode(cookieValue);
        } catch (IllegalArgumentException e) {
            log.warn("oauth2_auth_request base64 decode 실패: {}", e.getMessage());
            return null;
        }
        if (raw.length < 4 + 32) {
            log.warn("oauth2_auth_request size 비정상: {} bytes (minimum {})", raw.length, 4 + 32);
            return null;
        }
        ByteBuffer buf = ByteBuffer.wrap(raw);
        int len = buf.getInt();
        if (len <= 0 || len > raw.length - 4 - 32) {
            log.warn("oauth2_auth_request payloadLen 비정상: declared={} raw={}", len, raw.length);
            return null;
        }
        byte[] payloadBytes = new byte[len];
        buf.get(payloadBytes);
        byte[] expectedSig = new byte[32];
        buf.get(expectedSig);
        byte[] actualSig;
        try {
            actualSig = sign(payloadBytes);
        } catch (Exception e) {
            log.warn("oauth2_auth_request HMAC 계산 실패: {}", e.getMessage());
            return null;
        }
        if (!MessageDigest.isEqual(expectedSig, actualSig)) {
            log.warn("oauth2_auth_request HMAC mismatch — JwtProperties.secret 불일치(재시작·환경 변경) 또는 변조 가능");
            return null;
        }
        CookiePayload p;
        try {
            p = mapper.readValue(payloadBytes, CookiePayload.class);
        } catch (Exception e) {
            log.warn("oauth2_auth_request JSON parse 실패: {}", e.getMessage());
            return null;
        }
        log.debug("oauth2_auth_request 복원 성공 redirectUri={} attrsKeys={}",
                p.redirectUri(),
                p.attributes() == null ? "[]" : p.attributes().keySet());
        try {
            return OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(p.authorizationUri())
                    .clientId(p.clientId())
                    .redirectUri(p.redirectUri())
                    .scopes(p.scopes())
                    .state(p.state())
                    .additionalParameters(p.additionalParameters())
                    .attributes(p.attributes() == null ? Map.of() : p.attributes())
                    .authorizationRequestUri(p.authorizationRequestUri())
                    .build();
        } catch (Exception e) {
            log.warn("oauth2_auth_request 빌더 실패: {}", e.getMessage());
            return null;
        }
    }

    private byte[] sign(byte[] payload) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(hmacKey);
        return mac.doFinal(payload);
    }

    private static SecretKeySpec deriveKey(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, HMAC_ALGO);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot derive HMAC key", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CookiePayload(
            String authorizationUri,
            String clientId,
            String redirectUri,
            Set<String> scopes,
            String state,
            Map<String, Object> additionalParameters,
            Map<String, Object> attributes,
            String authorizationRequestUri
    ) {}
}
