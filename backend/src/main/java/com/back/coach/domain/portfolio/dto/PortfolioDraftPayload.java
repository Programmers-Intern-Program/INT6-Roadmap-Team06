package com.back.coach.domain.portfolio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PortfolioDraftPayload(
        @NotBlank String format,
        @NotEmpty List<@Valid PortfolioDraftVariant> variants
) {
}
