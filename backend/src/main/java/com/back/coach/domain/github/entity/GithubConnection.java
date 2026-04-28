package com.back.coach.domain.github.entity;

import com.back.coach.global.code.GithubAccessType;
import com.back.coach.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Entity
@Table(name = "github_connections")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubConnection extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "github_user_id", nullable = false, length = 100)
    private String githubUserId;

    @Column(name = "github_login", nullable = false, length = 100)
    private String githubLogin;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false, length = 30)
    private GithubAccessType accessType;

    @Column(name = "access_token", length = 500)
    private String accessToken;

    @CreatedDate
    @Column(name = "connected_at", nullable = false, updatable = false)
    private Instant connectedAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    private GithubConnection(Long userId, String githubUserId, String githubLogin,
                             GithubAccessType accessType, String accessToken) {
        this.userId = userId;
        this.githubUserId = githubUserId;
        this.githubLogin = githubLogin;
        this.accessType = accessType;
        this.accessToken = accessToken;
    }

    public static GithubConnection connect(Long userId, String githubUserId, String githubLogin,
                                           GithubAccessType accessType, String accessToken) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (githubUserId == null || githubUserId.isBlank()) {
            throw new IllegalArgumentException("githubUserId must not be blank");
        }
        if (githubLogin == null || githubLogin.isBlank()) {
            throw new IllegalArgumentException("githubLogin must not be blank");
        }
        if (accessType == null) {
            throw new IllegalArgumentException("accessType must not be null");
        }
        return new GithubConnection(userId, githubUserId, githubLogin, accessType, accessToken);
    }

    public void updateAccessToken(String accessToken) {
        this.accessToken = accessToken;
        this.disconnectedAt = null;
    }

    public void updateLogin(String githubLogin) {
        if (githubLogin != null && !githubLogin.isBlank()) {
            this.githubLogin = githubLogin;
        }
    }

    public void disconnect() {
        this.accessToken = null;
        this.disconnectedAt = Instant.now();
    }
}
