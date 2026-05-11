package com.back.coach.domain.coach.dto;

import com.back.coach.domain.coach.entity.CoachConversation;
import com.back.coach.global.code.CoachMessageRole;
import com.back.coach.global.code.CoachRoute;

import java.time.Instant;

public record CoachMessageHistoryResponse(
        String messageId,
        CoachMessageRole role,
        String messageText,
        CoachRoute route,
        String detectedIntent,
        Instant createdAt
) {
    public static CoachMessageHistoryResponse from(CoachConversation conversation) {
        return new CoachMessageHistoryResponse(
                String.valueOf(conversation.getId()),
                conversation.getRole(),
                conversation.getMessageText(),
                conversation.getRoute(),
                conversation.getDetectedIntent(),
                conversation.getCreatedAt()
        );
    }
}
