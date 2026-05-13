package com.back.coach.domain.portfolio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PortfolioDraftUpdateRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull @Valid PortfolioDraftPayload draftPayload
) {
}
