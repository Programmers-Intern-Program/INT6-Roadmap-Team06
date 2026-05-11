package com.back.coach.domain.github.dto;

import com.back.coach.domain.github.entity.GithubAnalysis;

import java.time.Instant;

public record GithubAnalysisSummaryResponse(
        String githubAnalysisId,
        Integer version,
        String summary,
        Instant createdAt
) {
    public static GithubAnalysisSummaryResponse from(GithubAnalysis analysis) {
        return new GithubAnalysisSummaryResponse(
                String.valueOf(analysis.getId()),
                analysis.getVersion(),
                analysis.getSummary(),
                analysis.getCreatedAt() == null ? Instant.now() : analysis.getCreatedAt()
        );
    }
}
