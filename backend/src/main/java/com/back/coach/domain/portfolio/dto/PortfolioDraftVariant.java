package com.back.coach.domain.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PortfolioDraftVariant(
        @NotBlank String key,
        @NotBlank String label,
        @NotNull String content,
        Boolean generated
) {
    public PortfolioDraftVariant(String key, String label, String content) {
        this(key, label, content, content != null && !content.isBlank());
    }
}
