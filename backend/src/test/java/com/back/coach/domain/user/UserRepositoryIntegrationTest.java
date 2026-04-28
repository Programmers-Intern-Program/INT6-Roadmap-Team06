package com.back.coach.domain.user;

import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("OAuth 신규 가입자를 저장하고 (provider, providerUserId) 로 조회할 수 있다")
    void signupFromOAuth_isPersistedAndFindable() {
        User saved = userRepository.save(
                User.signupFromOAuth(AuthProvider.GITHUB, "gh-12345", "alice@example.com")
        );

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAuthProvider()).isEqualTo(AuthProvider.GITHUB);
        assertThat(saved.getProviderUserId()).isEqualTo("gh-12345");
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getPasswordHash()).isNull();

        Optional<User> found = userRepository.findByAuthProviderAndProviderUserId(
                AuthProvider.GITHUB, "gh-12345"
        );
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("등록되지 않은 (provider, providerUserId) 조합은 빈 결과를 반환한다")
    void findByAuthProviderAndProviderUserId_returnsEmptyWhenMissing() {
        Optional<User> found = userRepository.findByAuthProviderAndProviderUserId(
                AuthProvider.GITHUB, "gh-does-not-exist"
        );
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("이메일 중복 가입은 unique 제약으로 거절된다")
    void duplicateEmail_violatesUniqueConstraint() {
        userRepository.saveAndFlush(
                User.signupFromOAuth(AuthProvider.GITHUB, "gh-aaa", "dup@example.com")
        );

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                User.signupFromOAuth(AuthProvider.GITHUB, "gh-bbb", "dup@example.com")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
