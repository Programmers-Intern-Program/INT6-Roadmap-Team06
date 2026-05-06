package com.back.coach.domain.roadmap.service;

import com.back.coach.domain.diagnosis.dto.DiagnosisPayload;
import com.back.coach.domain.diagnosis.entity.CapabilityDiagnosis;
import com.back.coach.domain.jobrole.entity.JobRole;
import com.back.coach.domain.user.entity.UserProfile;

import java.time.LocalDate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class RoadmapPromptBuilder {

    public String build(Input input) {
        Integer weeklyStudyHours = input.weeklyStudyHours() == null
                ? input.profile().getWeeklyStudyHours()
                : input.weeklyStudyHours();
        LocalDate targetDate = input.targetDate() == null
                ? input.profile().getTargetDate()
                : input.targetDate();

        return """
                You are a planner for an AI developer growth coaching service.
                Generate a practical weekly learning roadmap from the user's saved capability diagnosis.

                Rules:
                - Return JSON only.
                - Do not wrap JSON in Markdown code fences.
                - The JSON must match this shape:
                  {
                    "summary": "short roadmap summary",
                    "weeks": [
                      {
                        "weekNumber": 1,
                        "topic": "topic",
                        "reason": "why this week matters",
                        "tasks": [{"type": "READ_DOCS", "title": "task title"}],
                        "materials": [{"type": "DOCS", "title": "material title", "url": "https://example.com"}],
                        "estimatedHours": 8.0
                      }
                    ]
                  }
                - weekNumber must start at 1 and increase by 1.
                - tasks[].type must be one of READ_DOCS, BUILD_EXAMPLE, WRITE_NOTE, APPLY_PROJECT, REVIEW.
                - materials[].type must be one of DOCS, ARTICLE, REPOSITORY, VIDEO, TEMPLATE.
                - Do not include progress status.
                - Generate at most 8 weeks total.
                - Your output budget is 8192 tokens. Complete the entire JSON within this limit — do not truncate mid-object.

                User context:
                - targetRole: %s
                - currentLevel: %s
                - weeklyStudyHours: %s
                - targetDate: %s

                Diagnosis:
                - summary: %s
                - missingSkills: %s
                - strengths: %s
                - recommendations: %s
                """.formatted(
                input.jobRole().getRoleCode(),
                input.diagnosis().getCurrentLevel(),
                valueOrUnknown(weeklyStudyHours),
                valueOrUnknown(targetDate),
                input.diagnosis().getSummary(),
                missingSkills(input.diagnosisPayload()),
                input.diagnosisPayload().strengths(),
                input.diagnosisPayload().recommendations()
        );
    }

    private String missingSkills(DiagnosisPayload diagnosisPayload) {
        return diagnosisPayload.missingSkills().stream()
                .map(skill -> "%s(%s, priority %d): %s".formatted(
                        skill.skillName(),
                        skill.severity(),
                        skill.priorityOrder(),
                        skill.reason()
                ))
                .collect(Collectors.joining("; "));
    }

    private String valueOrUnknown(Object value) {
        return value == null ? "unknown" : String.valueOf(value);
    }

    public record Input(
            JobRole jobRole,
            UserProfile profile,
            CapabilityDiagnosis diagnosis,
            DiagnosisPayload diagnosisPayload,
            Integer weeklyStudyHours,
            LocalDate targetDate
    ) {
    }
}
