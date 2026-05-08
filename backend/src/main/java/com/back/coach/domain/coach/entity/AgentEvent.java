package com.back.coach.domain.coach.entity;

import com.back.coach.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Entity
@Table(name = "agent_events")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentEvent extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "source_agent", nullable = false, length = 30)
    private String sourceAgent;

    @Column(name = "target_agent", length = 30)
    private String targetAgent;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "event_data", nullable = false, columnDefinition = "jsonb")
    private String eventData;

    @Column(name = "processed_at")
    private Instant processedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AgentEvent replanRequested(Long userId, Long sessionId, String eventData) {
        AgentEvent event = new AgentEvent();
        event.userId = userId;
        event.sessionId = sessionId;
        event.sourceAgent = "COACH";
        event.targetAgent = "PLANNER";
        event.eventType = "REPLAN_REQUESTED";
        event.eventData = eventData;
        return event;
    }
}
