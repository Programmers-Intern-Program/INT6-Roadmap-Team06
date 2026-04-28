package com.back.coach.domain.dashboard;

import com.back.coach.global.code.CurrentLevel;

import java.time.Instant;
import java.time.LocalDate;

public record DashboardSnapshot(
        Long userId,
        ProfileSummary profile,
        GithubAnalysisSummary githubAnalysis,
        DiagnosisSummary diagnosis,
        RoadmapSummary roadmap
) {

    public record ProfileSummary(
            Long profileId,
            Long jobRoleId,
            CurrentLevel currentLevel,
            Integer weeklyStudyHours,
            LocalDate targetDate,
            Instant updatedAt
    ) {
    }

    public record GithubAnalysisSummary(
            Long githubAnalysisId,
            Integer version,
            String summary,
            Instant createdAt
    ) {
    }

    public record DiagnosisSummary(
            Long diagnosisId,
            Integer version,
            String summary,
            Instant createdAt
    ) {
    }

    public record RoadmapSummary(
            Long roadmapId,
            Integer version,
            Integer totalWeeks,
            String summary,
            Instant createdAt,
            ProgressSummary progress
    ) {
    }

    public record ProgressSummary(
            int totalWeeks,
            int todoWeeks,
            int inProgressWeeks,
            int doneWeeks,
            int skippedWeeks
    ) {
    }
}
