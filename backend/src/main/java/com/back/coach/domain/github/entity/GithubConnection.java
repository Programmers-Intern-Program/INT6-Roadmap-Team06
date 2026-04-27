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

    @CreatedDate
    @Column(name = "connected_at", nullable = false, updatable = false)
    private Instant connectedAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;
}
