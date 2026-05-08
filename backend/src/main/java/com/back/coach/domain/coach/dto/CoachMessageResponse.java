package com.back.coach.domain.coach.dto;

import com.back.coach.domain.coach.entity.CoachConversation;
import com.back.coach.domain.coach.entity.ReplanProposal;
import com.back.coach.domain.coach.service.CoachMessageService;
import com.back.coach.global.code.CoachRoute;

import java.time.Instant;

public record CoachMessageResponse(
        String messageId,
        String responseText,
        CoachRoute route,
        ReplanProposalDto replanProposal
) {
    public record ReplanProposalDto(String proposalId, String reason, Instant expiresAt) {}

    public static CoachMessageResponse from(CoachMessageService.MessageResult result) {
        CoachConversation msg = result.coachMessage();
        ReplanProposal proposal = result.proposal();

        ReplanProposalDto proposalDto = proposal == null ? null
                : new ReplanProposalDto(
                        String.valueOf(proposal.getId()),
                        proposal.getReason(),
                        proposal.getExpiresAt()
                  );

        return new CoachMessageResponse(
                String.valueOf(msg.getId()),
                msg.getMessageText(),
                msg.getRoute(),
                proposalDto
        );
    }
}
