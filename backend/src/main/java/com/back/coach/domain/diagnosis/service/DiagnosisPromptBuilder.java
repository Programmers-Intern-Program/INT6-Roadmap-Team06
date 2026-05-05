package com.back.coach.domain.diagnosis.service;

import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.domain.jobrole.entity.JobRole;
import com.back.coach.domain.jobrole.entity.SkillRequirement;
import com.back.coach.domain.user.entity.UserProfile;
import com.back.coach.domain.user.entity.UserSkill;
import com.back.coach.global.code.DiagnosisSeverity;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DiagnosisPromptBuilder {

    private static final String SEVERITY_VALUES = Arrays.stream(DiagnosisSeverity.values())
            .map(Enum::name)
            .collect(Collectors.joining(", "));

    public String build(Input input) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI developer growth diagnosis engine.\n");
        prompt.append("Compare the user's profile and GitHub final tech profile against the target job role requirements.\n");
        prompt.append("Return only one JSON object with summary, missingSkills, strengths, recommendations.\n\n");

        prompt.append("## Target Role\n");
        prompt.append("roleCode: ").append(input.jobRole().getRoleCode()).append("\n");
        prompt.append("roleName: ").append(input.jobRole().getRoleName()).append("\n");
        prompt.append("description: ").append(nullToEmpty(input.jobRole().getDescription())).append("\n\n");

        prompt.append("## User Profile\n");
        prompt.append("currentLevel: ").append(input.profile().getCurrentLevel()).append("\n");
        prompt.append("weeklyStudyHours: ").append(input.profile().getWeeklyStudyHours()).append("\n");
        prompt.append("targetDate: ").append(input.profile().getTargetDate()).append("\n");
        prompt.append("userInputSkills:\n");
        input.userSkills().forEach(skill -> prompt
                .append("  - ")
                .append(skill.getSkillName())
                .append(" | proficiency=")
                .append(skill.getProficiencyLevel())
                .append("\n"));

        prompt.append("\n## GitHub Final Tech Profile\n");
        prompt.append("confirmedSkills:\n");
        input.finalTechProfile().confirmedSkills()
                .forEach(skill -> prompt.append("  - ").append(skill).append("\n"));
        prompt.append("focusAreas:\n");
        input.finalTechProfile().focusAreas()
                .forEach(area -> prompt.append("  - ").append(area).append("\n"));

        prompt.append("\n## Job Skill Requirements\n");
        input.skillRequirements().forEach(requirement -> prompt
                .append("  - ")
                .append(requirement.getSkillName())
                .append(" | category=")
                .append(requirement.getCategory())
                .append(" | importance=")
                .append(requirement.getImportance())
                .append("\n"));

        prompt.append("\n## Output Rules\n");
        prompt.append("missingSkills[].severity must be one of: ").append(SEVERITY_VALUES).append("\n");
        prompt.append("missingSkills[].priorityOrder starts at 1 and must be sorted by learning priority.\n");
        prompt.append("strengths should include skills supported by user input or GitHub confirmed skills.\n");
        prompt.append("recommendations should be concrete next learning priorities.\n");
        prompt.append("Do not wrap JSON in Markdown code fences.\n");
        return prompt.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record Input(
            JobRole jobRole,
            UserProfile profile,
            List<UserSkill> userSkills,
            List<SkillRequirement> skillRequirements,
            GithubAnalysisPayload.FinalTechProfile finalTechProfile
    ) {
    }
}
