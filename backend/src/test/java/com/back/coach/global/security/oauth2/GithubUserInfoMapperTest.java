package com.back.coach.global.security.oauth2;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.service.auth.GithubUserInfo;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GithubUserInfoMapperTest {

    @Test
    void from_happyPath_extractsIdLoginEmail() {
        OAuth2User user = githubUser(Map.of(
                "id", 12345L,
                "login", "alice",
                "email", "alice@example.com"
        ));

        GithubUserInfo info = GithubUserInfoMapper.from(user);

        assertThat(info.providerUserId()).isEqualTo("12345");
        assertThat(info.login()).isEqualTo("alice");
        assertThat(info.email()).isEqualTo("alice@example.com");
    }

    @Test
    void from_idIsInteger_isStillStringified() {
        OAuth2User user = githubUser(Map.of(
                "id", 99,
                "login", "bob"
        ));

        assertThat(GithubUserInfoMapper.from(user).providerUserId()).isEqualTo("99");
    }

    @Test
    void from_emailMissing_returnsNullEmail() {
        OAuth2User user = githubUser(Map.of(
                "id", 7L,
                "login", "carol"
        ));

        GithubUserInfo info = GithubUserInfoMapper.from(user);

        assertThat(info.email()).isNull();
    }

    @Test
    void from_emailIsBlank_normalisesToNull() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", 7L);
        attrs.put("login", "carol");
        attrs.put("email", "  ");
        OAuth2User user = githubUser(attrs);

        assertThat(GithubUserInfoMapper.from(user).email()).isNull();
    }

    @Test
    void from_idMissing_throwsInvalidInput() {
        OAuth2User user = githubUser(Map.of("login", "dave"));

        assertThatThrownBy(() -> GithubUserInfoMapper.from(user))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void from_loginMissing_throwsInvalidInput() {
        OAuth2User user = githubUser(Map.of("id", 1L));

        assertThatThrownBy(() -> GithubUserInfoMapper.from(user))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private static OAuth2User githubUser(Map<String, Object> attrs) {
        return new OAuth2User() {
            @Override public Map<String, Object> getAttributes() { return attrs; }
            @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
            @Override public String getName() {
                Object id = attrs.get("id");
                return id == null ? "" : String.valueOf(id);
            }
        };
    }
}
