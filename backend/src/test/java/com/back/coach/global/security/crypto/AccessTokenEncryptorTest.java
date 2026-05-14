package com.back.coach.global.security.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenEncryptorTest {

    /** 정확히 32 바이트 짜리 Base64 키 (테스트 전용). */
    private static final String TEST_KEY_BASE64 =
            Base64.getEncoder().encodeToString("unit-test-32byte-aes-key-paddin0".getBytes());

    private final AccessTokenEncryptor sut = new AccessTokenEncryptor(TEST_KEY_BASE64);

    @Test
    void plain_to_db_to_plain_round_trips() {
        String plain = "ghp_1234567890abcdefABCDEF1234567890";

        String db = sut.convertToDatabaseColumn(plain);
        String back = sut.convertToEntityAttribute(db);

        assertThat(db).startsWith(AccessTokenEncryptor.PREFIX);
        assertThat(db).isNotEqualTo(plain);
        assertThat(back).isEqualTo(plain);
    }

    @Test
    void each_encryption_uses_new_iv_so_outputs_differ() {
        String plain = "ghp_same_token_value";
        String db1 = sut.convertToDatabaseColumn(plain);
        String db2 = sut.convertToDatabaseColumn(plain);

        assertThat(db1).isNotEqualTo(db2); // GCM IV 가 매번 새로 생성되므로 ciphertext 가 다름
        assertThat(sut.convertToEntityAttribute(db1)).isEqualTo(plain);
        assertThat(sut.convertToEntityAttribute(db2)).isEqualTo(plain);
    }

    @Test
    void null_in_yields_null_out() {
        assertThat(sut.convertToDatabaseColumn(null)).isNull();
        assertThat(sut.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void empty_string_in_yields_empty_string_out() {
        assertThat(sut.convertToDatabaseColumn("")).isEmpty();
    }

    @Test
    void legacy_plaintext_without_prefix_is_returned_as_is() {
        // 마이그레이션 전 plaintext 데이터 호환 시나리오
        String legacy = "ghp_legacy_plaintext_before_migration";

        String result = sut.convertToEntityAttribute(legacy);

        assertThat(result).isEqualTo(legacy);
    }

    @Test
    void tampered_ciphertext_throws() {
        String plain = "ghp_token";
        String db = sut.convertToDatabaseColumn(plain);
        // ciphertext base64 의 첫 글자(패딩 아님)를 변조 → GCM auth tag 검증 실패.
        // 마지막 글자를 변조하면 base64 padding 위치에 걸려 디코더가 silent truncation 할 수 있음.
        int ctStart = db.indexOf(':', AccessTokenEncryptor.PREFIX.length()) + 1;
        char orig = db.charAt(ctStart);
        String tampered = db.substring(0, ctStart) + (orig == 'A' ? 'B' : 'A') + db.substring(ctStart + 1);

        assertThatThrownBy(() -> sut.convertToEntityAttribute(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("복호화 실패");
    }

    @Test
    void malformed_db_value_missing_iv_separator_throws() {
        String malformed = AccessTokenEncryptor.PREFIX + "nocolon";

        assertThatThrownBy(() -> sut.convertToEntityAttribute(malformed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IV separator");
    }

    @Test
    void missing_key_property_fails_fast_on_construction() {
        assertThatThrownBy(() -> new AccessTokenEncryptor(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("설정되지 않았습니다");

        assertThatThrownBy(() -> new AccessTokenEncryptor(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("설정되지 않았습니다");
    }

    @Test
    void non_base64_key_fails_fast() {
        assertThatThrownBy(() -> new AccessTokenEncryptor("!!! not base64 !!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void wrong_length_key_fails_fast() {
        // 16 바이트 → AES-128, 본 컨버터는 32 바이트만 허용
        String short16 = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new AccessTokenEncryptor(short16))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 바이트");
    }

    @Test
    void encrypted_value_fits_in_500_char_db_column() {
        // GithubConnection.access_token 컬럼 length=500. GitHub PAT 최대 ~93자 까지 검증.
        String longishToken = "ghp_" + "x".repeat(89); // 93 chars
        String db = sut.convertToDatabaseColumn(longishToken);
        assertThat(db.length()).isLessThanOrEqualTo(500);
    }
}
