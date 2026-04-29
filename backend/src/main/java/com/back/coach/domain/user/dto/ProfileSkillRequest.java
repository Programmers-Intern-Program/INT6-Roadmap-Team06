package com.back.coach.domain.user.dto;

import com.back.coach.global.code.ProficiencyLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileSkillRequest(
        @NotBlank
        @Size(max = 100)
        String skillName,

        ProficiencyLevel proficiencyLevel
) {
}
