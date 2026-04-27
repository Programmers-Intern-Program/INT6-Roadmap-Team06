package com.back.coach.domain.github.entity;

import com.back.coach.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Entity
@Table(name = "github_projects")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubProject extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "github_connection_id", nullable = false)
    private Long githubConnectionId;

    @Column(name = "repo_node_id", length = 100)
    private String repoNodeId;

    @Column(name = "repo_full_name", nullable = false, length = 200)
    private String repoFullName;

    @Column(name = "repo_url", nullable = false)
    private String repoUrl;

    @Column(name = "primary_language", length = 50)
    private String primaryLanguage;

    @Column(name = "default_branch", length = 100)
    private String defaultBranch;

    @Column(name = "is_selected", nullable = false)
    private Boolean selected;

    @Column(name = "is_core_repo", nullable = false)
    private Boolean coreRepo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_payload", nullable = false, columnDefinition = "jsonb")
    private String metadataPayload;

    @CreatedDate
    @Column(name = "synced_at", nullable = false, updatable = false)
    private Instant syncedAt;
}
