package com.back.coach.global.security.oauth2;

import com.back.coach.global.security.CookieManager;
import com.back.coach.global.security.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;

/**
 * STATELESS 환경에서 OAuth2AuthorizationRequest 를 쿠키에 저장한다.
 *
 * <p>형식: {@code base64url(payloadLen | payload | hmacSha256(payload))}.
 * 서버는 자기 서명을 검증한 뒤에만 역직렬화하므로, 외부에서 임의 페이로드를 주입한
 * 쿠키로는 절대 deserialization 이 일어나지 않는다 (RCE 차단).
 */
@Component
@ConditionalOnProperty(prefix = "spring.security.oauth2.client.registration.github", name = "client-id")
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE_NAME = "oauth2_auth_request";
    private static final Duration COOKIE_TTL = Duration.ofMinutes(5);
    private static final String HMAC_ALGO = "HmacSHA256";

    private final CookieManager cookieManager;
    private final SecretKeySpec hmacKey;

    public CookieOAuth2AuthorizationRequestRepository(CookieManager cookieManager, JwtProperties jwt) {
        this.cookieManager = cookieManager;
        this.hmacKey = deriveKey(jwt.secret());
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String value = cookieManager.readValue(request, COOKIE_NAME);
        return value == null ? null : tryDeserialize(value);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        if (authorizationRequest == null) {
            cookieManager.clear(response, COOKIE_NAME);
            return;
        }
        cookieManager.add(response, COOKIE_NAME, serialize(authorizationRequest), COOKIE_TTL);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest existing = loadAuthorizationRequest(request);
        cookieManager.clear(response, COOKIE_NAME);
        return existing;
    }

    private String serialize(OAuth2AuthorizationRequest authRequest) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(authRequest);
            }
            byte[] payload = baos.toByteArray();
            byte[] sig = sign(payload);
            ByteBuffer buf = ByteBuffer.allocate(4 + payload.length + sig.length);
            buf.putInt(payload.length).put(payload).put(sig);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("OAuth2AuthorizationRequest serialize failed", e);
        }
    }

    private OAuth2AuthorizationRequest tryDeserialize(String cookieValue) {
        try {
            byte[] raw = Base64.getUrlDecoder().decode(cookieValue);
            if (raw.length < 4 + 32) return null;
            ByteBuffer buf = ByteBuffer.wrap(raw);
            int len = buf.getInt();
            if (len <= 0 || len > raw.length - 4 - 32) return null;
            byte[] payload = new byte[len];
            buf.get(payload);
            byte[] expectedSig = new byte[32];
            buf.get(expectedSig);
            byte[] actualSig = sign(payload);
            if (!MessageDigest.isEqual(expectedSig, actualSig)) {
                return null;
            }
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(payload))) {
                Object obj = ois.readObject();
                return (obj instanceof OAuth2AuthorizationRequest req) ? req : null;
            }
        } catch (Exception e) {
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
}
