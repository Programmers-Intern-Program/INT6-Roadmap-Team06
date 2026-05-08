package com.back.coach.domain.pattern.entity;

import com.back.coach.global.code.PatternSeverity;
import com.back.coach.global.code.PatternType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Entity
@Table(name = "detected_patterns")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DetectedPattern extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pattern_type", nullable = false, length = 50)
    private PatternType patternType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private PatternSeverity severity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "processed_at")
    private Instant processedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static DetectedPattern create(
            Long userId,
            PatternType patternType,
            PatternSeverity severity,
            String metadata
    ) {
        DetectedPattern pattern = new DetectedPattern();
        pattern.userId = userId;
        pattern.patternType = patternType;
        pattern.severity = severity;
        pattern.metadata = metadata;
        return pattern;
    }

    public void markProcessed(Instant processedAt) {
        if (this.processedAt != null) {
            return;
        }
        this.processedAt = processedAt;
    }
}