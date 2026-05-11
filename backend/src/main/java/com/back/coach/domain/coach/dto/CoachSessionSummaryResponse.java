package com.back.coach.domain.coach.dto;

import com.back.coach.domain.coach.entity.ChatSession;
import com.back.coach.global.code.ChatSessionStatus;

import java.time.Instant;

public record CoachSessionSummaryResponse(
        String sessionId,
        Integer profileVersion,
        Integer roadmapVersion,
        ChatSessionStatus status,
        Instant startedAt,
        Instant endedAt
) {
    public static CoachSessionSummaryResponse from(ChatSession session) {
        return new CoachSessionSummaryResponse(
                String.valueOf(session.getId()),
                session.getProfileVersion(),
                session.getRoadmapVersion(),
                session.getStatus(),
                session.getStartedAt(),
                session.getEndedAt()
        );
    }
}
