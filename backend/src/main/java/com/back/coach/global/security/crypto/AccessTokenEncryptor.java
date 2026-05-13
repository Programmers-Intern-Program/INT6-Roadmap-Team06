package com.back.coach.global.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * GitHub OAuth access_token 의 DB 저장 값을 AES-256-GCM 으로 자동 암복호화하는 JPA converter.
 *
 * <p>저장 포맷: {@code enc:{base64(iv)}:{base64(ciphertext+tag)}}.
 * IV 는 12 바이트(96-bit, GCM 권장), tag 는 128-bit.
 *
 * <h3>Legacy plaintext 호환</h3>
 * 마이그레이션 부담 회피를 위해, DB 에 이미 들어있는 plaintext (= "enc:" prefix 없음) 는
 * 그대로 읽어 반환한다. 다음 update 시 자동으로 암호화된 형태로 다시 저장된다.
 *
 * <h3>키 관리</h3>
 * {@code security.access-token-encryption.key} 프로퍼티 (env: {@code ACCESS_TOKEN_ENCRYPTION_KEY})
 * 로 주입한다. Base64 로 인코딩된 정확히 32 바이트(AES-256). 누락/형식 오류 시 ApplicationContext
 * 로딩이 실패한다 (fail-fast).
 *
 * <p>대부분의 호출 코드는 {@code @Convert} 이외에 어떤 변경도 필요하지 않다 —
 * JPA 가 entity read/write 시 본 converter 를 자동 적용한다.
 */
@Component
@Converter
public class AccessTokenEncryptor implements AttributeConverter<String, String> {

    /** "enc:" prefix 로 암호화된 값임을 식별. */
    static final String PREFIX = "enc:";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int AES_KEY_LENGTH_BYTES = 32; // AES-256

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccessTokenEncryptor(@Value("${security.access-token-encryption.key:}") String base64Key) {
        this.secretKey = loadKey(base64Key);
    }

    @Override
    public String convertToDatabaseColumn(String raw) {
        if (raw == null) return null;
        if (raw.isEmpty()) return raw;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8));

            Base64.Encoder enc = Base64.getEncoder().withoutPadding();
            return PREFIX + enc.encodeToString(iv) + ":" + enc.encodeToString(cipherText);
        } catch (GeneralSecurityException secException) {
            throw new IllegalStateException("access_token 암호화 실패", secException);
        }
    }

    @Override
    public String convertToEntityAttribute(String db) {
        if (db == null) return null;
        if (!db.startsWith(PREFIX)) {
            // Legacy plaintext (마이그레이션 전 값). 그대로 반환 → 다음 save 시 자동 암호화.
            return db;
        }
        String payload = db.substring(PREFIX.length());
        int sep = payload.indexOf(':');
        if (sep < 0) {
            throw new IllegalStateException("access_token DB 값 포맷이 잘못됨 (IV separator 누락)");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(payload.substring(0, sep));
            byte[] cipherText = Base64.getDecoder().decode(payload.substring(sep + 1));

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException secException) {
            throw new IllegalStateException("access_token 복호화 실패 (탬퍼링 또는 키 불일치)", secException);
        }
    }

    private static SecretKey loadKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "security.access-token-encryption.key 가 설정되지 않았습니다 (env: ACCESS_TOKEN_ENCRYPTION_KEY)");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "security.access-token-encryption.key 가 Base64 형식이 아닙니다", ex);
        }
        if (keyBytes.length != AES_KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "security.access-token-encryption.key 는 AES-256 용 32 바이트여야 합니다. 현재: "
                            + keyBytes.length + " 바이트");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

}
