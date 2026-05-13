package com.back.coach.domain.portfolio.entity;

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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Entity
@Table(name = "portfolio_drafts")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioDraft extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_payload", nullable = false, columnDefinition = "jsonb")
    private String draftPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_refs", nullable = false, columnDefinition = "jsonb")
    private String sourceRefs;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PortfolioDraft create(Long userId, String title, String draftPayload, String sourceRefs) {
        PortfolioDraft draft = new PortfolioDraft();
        draft.userId = userId;
        draft.title = title;
        draft.draftPayload = draftPayload;
        draft.sourceRefs = sourceRefs;
        return draft;
    }

    public void update(String title, String draftPayload) {
        this.title = title;
        this.draftPayload = draftPayload;
    }
}
