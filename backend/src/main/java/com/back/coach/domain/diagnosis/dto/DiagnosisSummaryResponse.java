package com.back.coach.domain.diagnosis.dto;

import com.back.coach.domain.diagnosis.entity.CapabilityDiagnosis;
import com.back.coach.global.code.CurrentLevel;

import java.time.Instant;

public record DiagnosisSummaryResponse(
        String diagnosisId,
        Integer version,
        CurrentLevel currentLevel,
        String summary,
        String githubAnalysisId,
        Instant createdAt
) {
    public static DiagnosisSummaryResponse from(CapabilityDiagnosis diagnosis) {
        return new DiagnosisSummaryResponse(
                String.valueOf(diagnosis.getId()),
                diagnosis.getVersion(),
                diagnosis.getCurrentLevel(),
                diagnosis.getSummary(),
                String.valueOf(diagnosis.getGithubAnalysisId()),
                diagnosis.getCreatedAt() == null ? Instant.now() : diagnosis.getCreatedAt()
        );
    }
}
