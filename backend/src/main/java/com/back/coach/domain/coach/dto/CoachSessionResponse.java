package com.back.coach.domain.coach.dto;

import com.back.coach.domain.coach.entity.ChatSession;

import java.time.Instant;

public record CoachSessionResponse(
        String sessionId,
        Integer profileVersion,
        Integer roadmapVersion,
        Instant startedAt
) {
    public static CoachSessionResponse from(ChatSession session) {
        return new CoachSessionResponse(
                String.valueOf(session.getId()),
                session.getProfileVersion(),
                session.getRoadmapVersion(),
                session.getStartedAt()
        );
    }
}
