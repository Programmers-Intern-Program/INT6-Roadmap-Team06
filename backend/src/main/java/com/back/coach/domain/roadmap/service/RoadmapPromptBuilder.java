package com.back.coach.domain.roadmap.service;

import com.back.coach.domain.diagnosis.dto.DiagnosisPayload;
import com.back.coach.domain.diagnosis.entity.CapabilityDiagnosis;
import com.back.coach.domain.jobrole.entity.JobRole;
import com.back.coach.domain.user.entity.UserProfile;

import java.time.LocalDate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RoadmapPromptBuilder {

    public static final String VERSION = "roadmap-v1.0";

    private static final Logger log = LoggerFactory.getLogger(RoadmapPromptBuilder.class);

    private final int maxWeeks;

    public RoadmapPromptBuilder(@Value("${roadmap.max-weeks}") int maxWeeks) {
        this.maxWeeks = maxWeeks;
    }

    public String build(Input input) {
        String prompt = renderPrompt(input);
        log.debug("Roadmap prompt built: builder=RoadmapPromptBuilder, version={}, bytes={}",
                VERSION, prompt.getBytes().length);
        return prompt;
    }

    private String renderPrompt(Input input) {
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
                - Write user-visible natural-language values in Korean: summary, weeks[].topic, weeks[].reason, tasks[].title, materials[].title.
                - Keep technical proper nouns such as Java, Spring Boot, Redis, Docker, and GitHub Actions in their original form.
                - Keep JSON field names and enum values unchanged.
                - The JSON must match this shape:
                  {
                    "summary": "백엔드 취업 준비를 위한 8주 학습 로드맵 요약",
                    "weeks": [
                      {
                        "weekNumber": 1,
                        "topic": "Redis 캐시 기초",
                        "reason": "백엔드 서비스의 성능 개선 역량을 보완하기 위해 필요합니다.",
                        "tasks": [{"type": "READ_DOCS", "title": "Redis 공식 문서 핵심 개념 읽기"}],
                        "materials": [{"type": "DOCS", "title": "Redis Documentation", "url": "https://example.com"}],
                        "estimatedHours": 8.0
                      }
                    ]
                  }
                - weekNumber must start at 1 and increase by 1.
                - tasks[].type must be one of READ_DOCS, BUILD_EXAMPLE, WRITE_NOTE, APPLY_PROJECT, REVIEW.
                - materials[].type must be one of DOCS, ARTICLE, REPOSITORY, VIDEO, TEMPLATE.
                - Do not include progress status.
                - Generate at most %d weeks total.
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
                maxWeeks,
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
