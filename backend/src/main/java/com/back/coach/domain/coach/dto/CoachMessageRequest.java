package com.back.coach.domain.coach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CoachMessageRequest(
        @NotBlank
        @Size(max = 4000)
        String message
) {
}
