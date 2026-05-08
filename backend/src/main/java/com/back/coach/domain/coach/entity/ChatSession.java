package com.back.coach.domain.coach.entity;

import com.back.coach.global.code.ChatSessionStatus;
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
@Table(name = "chat_sessions")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSession extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "profile_version", nullable = false)
    private Integer profileVersion;

    @Column(name = "roadmap_version", nullable = false)
    private Integer roadmapVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChatSessionStatus status;

    @CreatedDate
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    public static ChatSession start(Long userId, Integer profileVersion, Integer roadmapVersion) {
        ChatSession session = new ChatSession();
        session.userId = userId;
        session.profileVersion = profileVersion;
        session.roadmapVersion = roadmapVersion;
        session.status = ChatSessionStatus.ACTIVE;
        return session;
    }

    public void close(Instant endedAt) {
        this.status = ChatSessionStatus.CLOSED;
        this.endedAt = endedAt;
    }

    public boolean isClosed() {
        return this.status == ChatSessionStatus.CLOSED;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }
}
