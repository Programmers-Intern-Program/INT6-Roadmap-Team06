package com.back.coach.domain.github;

import com.back.coach.domain.github.entity.GithubConnection;
import com.back.coach.domain.github.repository.GithubConnectionRepository;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.code.GithubAccessType;
import com.back.coach.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class GithubConnectionRepositoryIntegrationTest {

    @Autowired
    private GithubConnectionRepository githubConnectionRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("GitHub 연결을 저장하고 (userId, githubUserId)로 조회한다")
    void connect_persistsAndIsFindable() {
        User user = userRepository.save(
                User.signupFromOAuth(AuthProvider.GITHUB, "gh-1001", "u1@example.com")
        );

        GithubConnection saved = githubConnectionRepository.save(
                GithubConnection.connect(user.getId(), "gh-1001", "alice", GithubAccessType.OAUTH, "ghp_token_v1")
        );

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(user.getId());
        assertThat(saved.getGithubUserId()).isEqualTo("gh-1001");
        assertThat(saved.getGithubLogin()).isEqualTo("alice");
        assertThat(saved.getAccessType()).isEqualTo(GithubAccessType.OAUTH);
        assertThat(saved.getAccessToken()).isEqualTo("ghp_token_v1");
        assertThat(saved.getConnectedAt()).isNotNull();
        assertThat(saved.getDisconnectedAt()).isNull();

        Optional<GithubConnection> found = githubConnectionRepository
                .findByUserIdAndGithubUserId(user.getId(), "gh-1001");
        assertThat(found).isPresent();
        assertThat(found.get().getAccessToken()).isEqualTo("ghp_token_v1");
    }

    @Test
    @DisplayName("재로그인 시 access token을 갱신할 수 있다")
    void updateAccessToken_replacesStoredToken() {
        User user = userRepository.save(
                User.signupFromOAuth(AuthProvider.GITHUB, "gh-2002", "u2@example.com")
        );
        GithubConnection saved = githubConnectionRepository.save(
                GithubConnection.connect(user.getId(), "gh-2002", "bob", GithubAccessType.OAUTH, "ghp_old")
        );

        saved.updateAccessToken("ghp_new");
        githubConnectionRepository.saveAndFlush(saved);

        GithubConnection reloaded = githubConnectionRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getAccessToken()).isEqualTo("ghp_new");
    }

    @Test
    @DisplayName("(user_id, github_user_id) 중복 연결은 unique 제약으로 거절된다")
    void duplicateConnection_violatesUniqueConstraint() {
        User user = userRepository.save(
                User.signupFromOAuth(AuthProvider.GITHUB, "gh-3003", "u3@example.com")
        );
        githubConnectionRepository.saveAndFlush(
                GithubConnection.connect(user.getId(), "gh-3003", "carol", GithubAccessType.OAUTH, "tok-a")
        );

        assertThatThrownBy(() -> githubConnectionRepository.saveAndFlush(
                GithubConnection.connect(user.getId(), "gh-3003", "carol", GithubAccessType.OAUTH, "tok-b")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
