package com.back.coach.domain.coach.dto;

import com.back.coach.domain.coach.entity.CoachConversation;
import com.back.coach.global.code.CoachRoute;

public record CoachMessageResponse(
        String messageId,
        String responseText,
        CoachRoute route,
        Object replanProposal
) {
    public static CoachMessageResponse from(CoachConversation coachMessage) {
        return new CoachMessageResponse(
                String.valueOf(coachMessage.getId()),
                coachMessage.getMessageText(),
                coachMessage.getRoute(),
                null
        );
    }
}
