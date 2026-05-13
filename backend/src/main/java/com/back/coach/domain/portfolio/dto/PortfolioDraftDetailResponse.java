package com.back.coach.domain.portfolio.dto;

import com.back.coach.domain.portfolio.entity.PortfolioDraft;

import java.time.Instant;
import java.util.Map;

public record PortfolioDraftDetailResponse(
        String draftId,
        String title,
        PortfolioDraftPayload draftPayload,
        Map<String, Object> sourceRefs,
        Instant createdAt,
        Instant updatedAt
) {
    public static PortfolioDraftDetailResponse of(
            PortfolioDraft draft,
            PortfolioDraftPayload draftPayload,
            Map<String, Object> sourceRefs
    ) {
        return new PortfolioDraftDetailResponse(
                String.valueOf(draft.getId()),
                draft.getTitle(),
                draftPayload,
                sourceRefs,
                draft.getCreatedAt(),
                draft.getUpdatedAt()
        );
    }
}
