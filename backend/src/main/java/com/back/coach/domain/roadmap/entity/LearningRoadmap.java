package com.back.coach.domain.roadmap.entity;

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
@Table(name = "learning_roadmaps")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningRoadmap extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "diagnosis_id", nullable = false)
    private Long diagnosisId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "total_weeks", nullable = false)
    private Integer totalWeeks;

    @Column(name = "summary", nullable = false, columnDefinition = "text")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "roadmap_payload", nullable = false, columnDefinition = "jsonb")
    private String roadmapPayload;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
