package com.back.coach.domain.github.dto;

import com.back.coach.domain.github.entity.GithubAnalysis;

import java.time.Instant;
import java.util.List;

public record GithubAnalysisSummaryResponse(
        String githubAnalysisId,
        Integer version,
        String summary,
        List<String> repoNames,
        Instant createdAt
) {
    public static GithubAnalysisSummaryResponse from(GithubAnalysis analysis) {
        return from(analysis, List.of());
    }

    public static GithubAnalysisSummaryResponse from(GithubAnalysis analysis, List<String> repoNames) {
        return new GithubAnalysisSummaryResponse(
                String.valueOf(analysis.getId()),
                analysis.getVersion(),
                analysis.getSummary(),
                repoNames == null ? List.of() : repoNames,
                analysis.getCreatedAt() == null ? Instant.now() : analysis.getCreatedAt()
        );
    }
}
