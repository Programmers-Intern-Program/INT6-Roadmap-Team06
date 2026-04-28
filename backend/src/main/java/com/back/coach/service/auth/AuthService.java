package com.back.coach.service.auth;

import com.back.coach.domain.github.entity.GithubConnection;
import com.back.coach.domain.github.repository.GithubConnectionRepository;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.code.GithubAccessType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.global.security.JwtTokenProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final GithubConnectionRepository githubConnectionRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository,
                       GithubConnectionRepository githubConnectionRepository,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.githubConnectionRepository = githubConnectionRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public OAuthLoginResult loginWithGithub(GithubUserInfo info, String githubAccessToken) {
        User user = userRepository
                .findByAuthProviderAndProviderUserId(AuthProvider.GITHUB, info.providerUserId())
                .orElseGet(() -> userRepository.save(
                        User.signupFromOAuth(AuthProvider.GITHUB, info.providerUserId(), resolveEmail(info))
                ));

        GithubConnection connection = githubConnectionRepository
                .findByUserIdAndGithubUserId(user.getId(), info.providerUserId())
                .map(existing -> {
                    existing.updateAccessToken(githubAccessToken);
                    existing.updateLogin(info.login());
                    return existing;
                })
                .orElseGet(() -> GithubConnection.connect(
                        user.getId(), info.providerUserId(), info.login(),
                        GithubAccessType.OAUTH, githubAccessToken
                ));
        githubConnectionRepository.save(connection);

        return new OAuthLoginResult(
                user.getId(),
                jwtTokenProvider.createAccessToken(user.getId()),
                jwtTokenProvider.createRefreshToken(user.getId())
        );
    }

    private String resolveEmail(GithubUserInfo info) {
        if (info.email() != null && !info.email().isBlank()) {
            return info.email();
        }
        return "github-" + info.providerUserId() + "@coach.local";
    }

    @Transactional(readOnly = true)
    public User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.USER_NOT_FOUND));
    }

    public TokenPair issueTokens(Long userId) {
        return new TokenPair(
                jwtTokenProvider.createAccessToken(userId),
                jwtTokenProvider.createRefreshToken(userId)
        );
    }
}
