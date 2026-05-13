package com.back.coach.domain.portfolio.dto;

import jakarta.validation.constraints.NotBlank;

public record PortfolioDraftVariant(
        @NotBlank String key,
        @NotBlank String label,
        @NotBlank String content
) {
}
