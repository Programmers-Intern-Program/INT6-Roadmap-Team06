package com.back.coach.domain.portfolio.dto;

import com.back.coach.domain.portfolio.entity.PortfolioDraft;

import java.time.Instant;

public record PortfolioDraftSummaryResponse(
        String draftId,
        String title,
        Instant createdAt,
        Instant updatedAt
) {
    public static PortfolioDraftSummaryResponse from(PortfolioDraft draft) {
        return new PortfolioDraftSummaryResponse(
                String.valueOf(draft.getId()),
                draft.getTitle(),
                draft.getCreatedAt(),
                draft.getUpdatedAt()
        );
    }
}
