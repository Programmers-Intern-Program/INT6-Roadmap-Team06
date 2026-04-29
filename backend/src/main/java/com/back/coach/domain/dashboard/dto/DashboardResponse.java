package com.back.coach.domain.dashboard.dto;

import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.global.code.CurrentLevel;

import java.time.Instant;
import java.time.LocalDate;

public record DashboardResponse(
        String userId,
        ProfileSummaryResponse profile,
        GithubAnalysisSummaryResponse githubAnalysis,
        DiagnosisSummaryResponse diagnosis,
        RoadmapSummaryResponse roadmap
) {

    public static DashboardResponse from(DashboardSnapshot snapshot) {
        return new DashboardResponse(
                String.valueOf(snapshot.userId()),
                ProfileSummaryResponse.from(snapshot.profile()),
                GithubAnalysisSummaryResponse.from(snapshot.githubAnalysis()),
                DiagnosisSummaryResponse.from(snapshot.diagnosis()),
                RoadmapSummaryResponse.from(snapshot.roadmap())
        );
    }

    public record ProfileSummaryResponse(
            String profileId,
            String jobRoleId,
            CurrentLevel currentLevel,
            Integer weeklyStudyHours,
            LocalDate targetDate,
            Instant updatedAt
    ) {

        private static ProfileSummaryResponse from(DashboardSnapshot.ProfileSummary summary) {
            if (summary == null) {
                return null;
            }
            return new ProfileSummaryResponse(
                    String.valueOf(summary.profileId()),
                    String.valueOf(summary.jobRoleId()),
                    summary.currentLevel(),
                    summary.weeklyStudyHours(),
                    summary.targetDate(),
                    summary.updatedAt()
            );
        }
    }

    public record GithubAnalysisSummaryResponse(
            String githubAnalysisId,
            Integer version,
            String summary,
            Instant createdAt,
            GithubAnalysisPayload.FinalTechProfile finalTechProfile,
            int userCorrectionCount
    ) {

        private static GithubAnalysisSummaryResponse from(DashboardSnapshot.GithubAnalysisSummary summary) {
            if (summary == null) {
                return null;
            }
            return new GithubAnalysisSummaryResponse(
                    String.valueOf(summary.githubAnalysisId()),
                    summary.version(),
                    summary.summary(),
                    summary.createdAt(),
                    summary.finalTechProfile(),
                    summary.userCorrectionCount()
            );
        }
    }

    public record DiagnosisSummaryResponse(
            String diagnosisId,
            Integer version,
            String summary,
            Instant createdAt
    ) {

        private static DiagnosisSummaryResponse from(DashboardSnapshot.DiagnosisSummary summary) {
            if (summary == null) {
                return null;
            }
            return new DiagnosisSummaryResponse(
                    String.valueOf(summary.diagnosisId()),
                    summary.version(),
                    summary.summary(),
                    summary.createdAt()
            );
        }
    }

    public record RoadmapSummaryResponse(
            String roadmapId,
            Integer version,
            Integer totalWeeks,
            String summary,
            Instant createdAt,
            ProgressSummaryResponse progress
    ) {

        private static RoadmapSummaryResponse from(DashboardSnapshot.RoadmapSummary summary) {
            if (summary == null) {
                return null;
            }
            return new RoadmapSummaryResponse(
                    String.valueOf(summary.roadmapId()),
                    summary.version(),
                    summary.totalWeeks(),
                    summary.summary(),
                    summary.createdAt(),
                    ProgressSummaryResponse.from(summary.progress())
            );
        }
    }

    public record ProgressSummaryResponse(
            int totalWeeks,
            int todoWeeks,
            int inProgressWeeks,
            int doneWeeks,
            int skippedWeeks
    ) {

        private static ProgressSummaryResponse from(DashboardSnapshot.ProgressSummary summary) {
            if (summary == null) {
                return null;
            }
            return new ProgressSummaryResponse(
                    summary.totalWeeks(),
                    summary.todoWeeks(),
                    summary.inProgressWeeks(),
                    summary.doneWeeks(),
                    summary.skippedWeeks()
            );
        }
    }
}
