package com.back.coach.domain.user.dto;

import com.back.coach.global.code.CurrentLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record ProfileSaveRequest(
        @NotBlank
        String targetRole,

        @NotNull
        CurrentLevel currentLevel,

        @NotEmpty
        @Size(max = 20)
        List<@Valid ProfileSkillRequest> skills,

        @Size(max = 10)
        List<@Size(max = 50) String> interestAreas,

        @Min(1)
        @Max(40)
        Integer weeklyStudyHours,

        @Future
        LocalDate targetDate,

        Long resumeAssetId,

        Long portfolioAssetId
) {
}
