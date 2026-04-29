package com.back.coach.domain.diagnosis.dto;

import com.back.coach.global.code.CurrentLevel;
import com.back.coach.global.code.DiagnosisSeverity;

import java.time.Instant;
import java.util.List;

public record DiagnosisDetailResponse(
        String diagnosisId,
        Integer version,
        String profileId,
        String githubAnalysisId,
        String targetRole,
        CurrentLevel currentLevel,
        String summary,
        List<MissingSkillResponse> missingSkills,
        List<String> strengths,
        List<String> recommendations,
        Instant createdAt
) {

    public record MissingSkillResponse(
            String skillName,
            DiagnosisSeverity severity,
            String reason,
            Integer priorityOrder
    ) {

        public static MissingSkillResponse from(DiagnosisPayload.MissingSkill missingSkill) {
            return new MissingSkillResponse(
                    missingSkill.skillName(),
                    missingSkill.severity(),
                    missingSkill.reason(),
                    missingSkill.priorityOrder()
            );
        }
    }
}
