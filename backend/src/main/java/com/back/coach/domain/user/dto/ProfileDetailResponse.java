package com.back.coach.domain.user.dto;

import com.back.coach.global.code.CurrentLevel;

import java.time.LocalDate;
import java.util.List;

public record ProfileDetailResponse(
        String profileId,
        String targetRole,
        CurrentLevel currentLevel,
        List<ProfileSkillResponse> skills,
        List<String> interestAreas,
        Integer weeklyStudyHours,
        LocalDate targetDate,
        String resumeAssetId,
        String portfolioAssetId
) {
}
