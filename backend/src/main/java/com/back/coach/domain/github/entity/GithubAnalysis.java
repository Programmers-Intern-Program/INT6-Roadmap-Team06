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
@Table(name = "github_analyses")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubAnalysis extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "github_connection_id", nullable = false)
    private Long githubConnectionId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "summary", nullable = false, columnDefinition = "text")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "analysis_payload", nullable = false, columnDefinition = "jsonb")
    private String analysisPayload;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static GithubAnalysis create(Long userId, Long githubConnectionId, Integer version,
                                        String summary, String analysisPayload) {
        GithubAnalysis entity = new GithubAnalysis();
        entity.userId = userId;
        entity.githubConnectionId = githubConnectionId;
        entity.version = version;
        entity.summary = summary;
        entity.analysisPayload = analysisPayload;
        return entity;
    }
}
