package com.back.coach.domain.diagnosis.service;

import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.domain.jobrole.entity.JobRole;
import com.back.coach.domain.jobrole.entity.SkillRequirement;
import com.back.coach.domain.user.entity.UserProfile;
import com.back.coach.domain.user.entity.UserSkill;
import com.back.coach.global.code.CurrentLevel;
import com.back.coach.global.code.ProficiencyLevel;
import com.back.coach.global.code.SkillSourceType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class DiagnosisPromptBuilderTest {

    private final DiagnosisPromptBuilder builder = new DiagnosisPromptBuilder();

    @Test
    void build_includesStrictDiagnosisJsonShapeAndFieldNameRules() {
        String prompt = builder.build(new DiagnosisPromptBuilder.Input(
                jobRole(),
                profile(),
                List.of(UserSkill.create(1L, "Spring Boot", ProficiencyLevel.WORKING, SkillSourceType.USER_INPUT)),
                List.of(skillRequirement("Redis", 5)),
                new GithubAnalysisPayload.FinalTechProfile(List.of("Spring Boot"), List.of("Redis"))
        ));

        assertThat(prompt).contains(
                "\"summary\"",
                "\"missingSkills\"",
                "\"skillName\"",
                "\"severity\"",
                "\"reason\"",
                "\"priorityOrder\"",
                "\"strengths\"",
                "\"recommendations\""
        );
        assertThat(prompt).contains("missingSkills[] must contain only skillName, severity, reason, priorityOrder");
        assertThat(prompt).contains("Do not use alias field names like skill, name, description, priority, rationale");
        assertThat(prompt).contains("missingSkills[].reason must be a non-empty explanation");
        assertThat(prompt).contains("LOW, MEDIUM, HIGH");
        assertThat(prompt).contains("Do not wrap JSON in Markdown code fences.");
    }

    private JobRole jobRole() {
        JobRole jobRole = mock(JobRole.class);
        given(jobRole.getRoleCode()).willReturn("BACKEND_DEVELOPER");
        given(jobRole.getRoleName()).willReturn("백엔드 개발자");
        given(jobRole.getDescription()).willReturn("Java와 Spring Boot 기반 백엔드 직무");
        return jobRole;
    }

    private UserProfile profile() {
        return UserProfile.create(
                1L,
                10L,
                CurrentLevel.JUNIOR,
                8,
                LocalDate.of(2099, 12, 31),
                "[]",
                null,
                null
        );
    }

    private SkillRequirement skillRequirement(String skillName, int importance) {
        SkillRequirement requirement = mock(SkillRequirement.class);
        given(requirement.getSkillName()).willReturn(skillName);
        given(requirement.getCategory()).willReturn("BACKEND");
        given(requirement.getImportance()).willReturn(importance);
        return requirement;
    }
}
