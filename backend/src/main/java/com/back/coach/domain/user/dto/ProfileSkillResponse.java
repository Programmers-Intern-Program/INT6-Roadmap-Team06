package com.back.coach.domain.user.dto;

import com.back.coach.global.code.ProficiencyLevel;
import com.back.coach.global.code.SkillSourceType;

public record ProfileSkillResponse(
        String skillName,
        ProficiencyLevel proficiencyLevel,
        SkillSourceType sourceType
) {
}
