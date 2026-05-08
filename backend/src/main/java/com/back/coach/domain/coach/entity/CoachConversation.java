package com.back.coach.domain.coach.entity;

import com.back.coach.global.code.CoachMessageRole;
import com.back.coach.global.code.CoachRoute;
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
@Table(name = "coach_conversations")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoachConversation extends BaseEntity {

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private CoachMessageRole role;

    @Column(name = "message_text", nullable = false, columnDefinition = "text")
    private String messageText;

    @Enumerated(EnumType.STRING)
    @Column(name = "route", length = 30)
    private CoachRoute route;

    @Column(name = "detected_intent", length = 50)
    private String detectedIntent;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static CoachConversation user(Long sessionId, Long userId, String messageText) {
        CoachConversation conversation = new CoachConversation();
        conversation.sessionId = sessionId;
        conversation.userId = userId;
        conversation.role = CoachMessageRole.USER;
        conversation.messageText = messageText;
        return conversation;
    }

    public static CoachConversation coach(
            Long sessionId,
            Long userId,
            String messageText,
            CoachRoute route,
            String detectedIntent
    ) {
        CoachConversation conversation = new CoachConversation();
        conversation.sessionId = sessionId;
        conversation.userId = userId;
        conversation.role = CoachMessageRole.COACH;
        conversation.messageText = messageText;
        conversation.route = route;
        conversation.detectedIntent = detectedIntent;
        return conversation;
    }
}
