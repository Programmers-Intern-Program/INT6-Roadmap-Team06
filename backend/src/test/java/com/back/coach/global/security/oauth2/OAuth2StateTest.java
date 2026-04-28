package com.back.coach.global.security.oauth2;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuth2StateTest {

    @Test
    void encodeThenDecode_preservesRedirectUrl() {
        String token = OAuth2State.encode("https://app.example.com/dashboard");

        OAuth2State decoded = OAuth2State.decode(token);

        assertThat(decoded.redirectUrl()).isEqualTo("https://app.example.com/dashboard");
    }

    @Test
    void encodeThenDecode_supportsNullRedirectUrl() {
        String token = OAuth2State.encode(null);

        OAuth2State decoded = OAuth2State.decode(token);

        assertThat(decoded.redirectUrl()).isNull();
    }

    @Test
    void encodeThenDecode_supportsBlankRedirectUrl() {
        String token = OAuth2State.encode("");

        OAuth2State decoded = OAuth2State.decode(token);

        assertThat(decoded.redirectUrl()).isEqualTo("");
    }

    @Test
    void encode_producesUrlSafeString() {
        String token = OAuth2State.encode("https://app.example.com/path?x=1&y=2");

        assertThat(token)
                .doesNotContain("+")
                .doesNotContain("/")
                .doesNotContain("=");
    }

    @Test
    void decode_malformedToken_throwsInvalidOauthState() {
        assertThatThrownBy(() -> OAuth2State.decode("not-a-valid-state"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_OAUTH_STATE);
    }

    @Test
    void decode_validBase64ButInvalidJson_throwsInvalidOauthState() {
        String junk = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("not json".getBytes());

        assertThatThrownBy(() -> OAuth2State.decode(junk))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_OAUTH_STATE);
    }

    @Test
    void decode_nullOrBlank_throwsInvalidOauthState() {
        assertThatThrownBy(() -> OAuth2State.decode(null))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_OAUTH_STATE);

        assertThatThrownBy(() -> OAuth2State.decode("  "))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_OAUTH_STATE);
    }
}
