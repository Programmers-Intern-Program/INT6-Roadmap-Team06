package com.back.coach.domain.diagnosis.dto;

import com.back.coach.global.code.DiagnosisSeverity;

import java.util.List;

public record DiagnosisPayload(
        List<MissingSkill> missingSkills,
        List<String> strengths,
        List<String> recommendations
) {

    public record MissingSkill(
            String skillName,
            DiagnosisSeverity severity,
            String reason,
            Integer priorityOrder
    ) {
    }
}
