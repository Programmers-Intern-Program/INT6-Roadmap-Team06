package com.back.coach.domain.coach.entity;

import com.back.coach.global.code.ReplanProposalStatus;
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
@Table(name = "replan_proposals")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplanProposal extends BaseEntity {

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReplanProposalStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static ReplanProposal create(Long sessionId, Long userId, Long messageId,
                                        String reason, Instant expiresAt) {
        ReplanProposal proposal = new ReplanProposal();
        proposal.sessionId = sessionId;
        proposal.userId = userId;
        proposal.messageId = messageId;
        proposal.reason = reason;
        proposal.status = ReplanProposalStatus.PENDING;
        proposal.expiresAt = expiresAt;
        return proposal;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isPending() {
        return status == ReplanProposalStatus.PENDING;
    }

    public void confirm(Instant resolvedAt) {
        this.status = ReplanProposalStatus.CONFIRMED;
        this.resolvedAt = resolvedAt;
    }

    public void dismiss(Instant resolvedAt) {
        this.status = ReplanProposalStatus.DISMISSED;
        this.resolvedAt = resolvedAt;
    }
}
