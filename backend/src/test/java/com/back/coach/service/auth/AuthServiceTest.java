package com.back.coach.service.auth;

import com.back.coach.domain.github.entity.GithubConnection;
import com.back.coach.domain.github.repository.GithubConnectionRepository;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.code.GithubAccessType;
import com.back.coach.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    GithubConnectionRepository githubConnectionRepository;

    @Mock
    JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    AuthService authService;

    @Test
    @DisplayName("최초 GitHub OAuth 로그인 시 User와 GithubConnection이 새로 생성되고 JWT 쌍을 반환한다")
    void firstTimeSignup_createsUserAndConnection() {
        GithubUserInfo info = new GithubUserInfo("gh-1001", "alice", "alice@example.com");
        given(userRepository.findByAuthProviderAndProviderUserId(AuthProvider.GITHUB, "gh-1001"))
                .willReturn(Optional.empty());
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> withId(invocation.getArgument(0), 7L));
        given(githubConnectionRepository.findByUserIdAndGithubUserId(7L, "gh-1001"))
                .willReturn(Optional.empty());
        given(githubConnectionRepository.save(any(GithubConnection.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(jwtTokenProvider.createAccessToken(7L)).willReturn("access-jwt");
        given(jwtTokenProvider.createRefreshToken(7L)).willReturn("refresh-jwt");

        OAuthLoginResult result = authService.loginWithGithub(info, "ghp_token_first");

        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.accessToken()).isEqualTo("access-jwt");
        assertThat(result.refreshToken()).isEqualTo("refresh-jwt");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getAuthProvider()).isEqualTo(AuthProvider.GITHUB);
        assertThat(userCaptor.getValue().getProviderUserId()).isEqualTo("gh-1001");
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("alice@example.com");

        ArgumentCaptor<GithubConnection> connCaptor = ArgumentCaptor.forClass(GithubConnection.class);
        verify(githubConnectionRepository).save(connCaptor.capture());
        assertThat(connCaptor.getValue().getUserId()).isEqualTo(7L);
        assertThat(connCaptor.getValue().getGithubUserId()).isEqualTo("gh-1001");
        assertThat(connCaptor.getValue().getGithubLogin()).isEqualTo("alice");
        assertThat(connCaptor.getValue().getAccessType()).isEqualTo(GithubAccessType.OAUTH);
        assertThat(connCaptor.getValue().getAccessToken()).isEqualTo("ghp_token_first");
    }

    @Test
    @DisplayName("재로그인 시 기존 GithubConnection의 access token만 갱신하고 User를 새로 만들지 않는다")
    void returningUser_refreshesAccessToken() {
        GithubUserInfo info = new GithubUserInfo("gh-2002", "bob", "bob@example.com");
        User existingUser = withId(
                User.signupFromOAuth(AuthProvider.GITHUB, "gh-2002", "bob@example.com"),
                42L);
        GithubConnection existingConn = GithubConnection.connect(
                42L, "gh-2002", "bob", GithubAccessType.OAUTH, "ghp_old");

        given(userRepository.findByAuthProviderAndProviderUserId(AuthProvider.GITHUB, "gh-2002"))
                .willReturn(Optional.of(existingUser));
        given(githubConnectionRepository.findByUserIdAndGithubUserId(42L, "gh-2002"))
                .willReturn(Optional.of(existingConn));
        given(githubConnectionRepository.save(any(GithubConnection.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(jwtTokenProvider.createAccessToken(42L)).willReturn("acc");
        given(jwtTokenProvider.createRefreshToken(42L)).willReturn("ref");

        OAuthLoginResult result = authService.loginWithGithub(info, "ghp_new");

        assertThat(result.userId()).isEqualTo(42L);
        verify(userRepository, never()).save(any());
        assertThat(existingConn.getAccessToken()).isEqualTo("ghp_new");
    }

    @Test
    @DisplayName("기존 User에 GithubConnection이 없으면 새 연결을 생성한다")
    void returningUser_withoutConnection_createsNewConnection() {
        GithubUserInfo info = new GithubUserInfo("gh-3003", "carol", "carol@example.com");
        User existingUser = withId(
                User.signupFromOAuth(AuthProvider.GITHUB, "gh-3003", "carol@example.com"),
                99L);

        given(userRepository.findByAuthProviderAndProviderUserId(AuthProvider.GITHUB, "gh-3003"))
                .willReturn(Optional.of(existingUser));
        given(githubConnectionRepository.findByUserIdAndGithubUserId(99L, "gh-3003"))
                .willReturn(Optional.empty());
        given(githubConnectionRepository.save(any(GithubConnection.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(jwtTokenProvider.createAccessToken(99L)).willReturn("acc");
        given(jwtTokenProvider.createRefreshToken(99L)).willReturn("ref");

        authService.loginWithGithub(info, "ghp_token");

        verify(userRepository, never()).save(any());
        ArgumentCaptor<GithubConnection> connCaptor = ArgumentCaptor.forClass(GithubConnection.class);
        verify(githubConnectionRepository).save(connCaptor.capture());
        assertThat(connCaptor.getValue().getUserId()).isEqualTo(99L);
        assertThat(connCaptor.getValue().getAccessToken()).isEqualTo("ghp_token");
    }

    @Test
    @DisplayName("GitHub가 이메일을 제공하지 않으면 placeholder를 합성해 저장한다")
    void missingEmail_synthesizesPlaceholder() {
        GithubUserInfo info = new GithubUserInfo("gh-4004", "dave", null);
        given(userRepository.findByAuthProviderAndProviderUserId(AuthProvider.GITHUB, "gh-4004"))
                .willReturn(Optional.empty());
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> withId(invocation.getArgument(0), 5L));
        given(githubConnectionRepository.findByUserIdAndGithubUserId(5L, "gh-4004"))
                .willReturn(Optional.empty());
        given(githubConnectionRepository.save(any(GithubConnection.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(jwtTokenProvider.createAccessToken(5L)).willReturn("acc");
        given(jwtTokenProvider.createRefreshToken(5L)).willReturn("ref");

        authService.loginWithGithub(info, "ghp_token");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail())
                .isEqualTo("github-gh-4004@coach.local");
    }

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
