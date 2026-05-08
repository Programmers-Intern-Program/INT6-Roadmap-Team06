package com.back.coach.domain.coach.dto;

import jakarta.validation.constraints.NotNull;

public record ReplanRequest(
        @NotNull Long proposalId,
        @NotNull Boolean confirmed
) {
}
