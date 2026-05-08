package com.back.coach.domain.github.dto;

import com.back.coach.domain.github.service.GithubAnalysisService;

import java.time.Instant;
import java.util.List;

// POST /api/github-analyses 응답. ID는 String (프로젝트 컨벤션).
public record GithubAnalysisResponse(
        String githubAnalysisId,
        Integer version,
        GithubAnalysisPayload.StaticSignals staticSignals,
        List<GithubAnalysisPayload.RepoSummary> repoSummaries,
        List<GithubAnalysisPayload.TechTag> techTags,
        List<GithubAnalysisPayload.DepthEstimate> depthEstimates,
        List<GithubAnalysisPayload.GithubEvidence> evidences,
        List<GithubAnalysisPayload.GithubUserCorrection> userCorrections,
        GithubAnalysisPayload.FinalTechProfile finalTechProfile,
        Instant createdAt,
        AnalysisMetricsResponse metrics
) {
    public static GithubAnalysisResponse from(GithubAnalysisService.GithubAnalysisResult result) {
        GithubAnalysisPayload p = result.payload();
        GithubAnalysisService.AnalysisMetrics m = result.metrics();
        AnalysisMetricsResponse metricsResponse = m != null ? new AnalysisMetricsResponse(
                m.totalElapsedMs(),
                m.repoCount(),
                m.triagePromptBytes(),
                m.triageElapsedMs(),
                m.summaryPromptBytes(),
                m.summaryElapsedMs(),
                m.synthesisPromptBytes(),
                m.synthesisElapsedMs()
        ) : null;
        return new GithubAnalysisResponse(
                String.valueOf(result.id()), result.version(),
                p.staticSignals(), p.repoSummaries(), p.techTags(),
                p.depthEstimates(), p.evidences(), p.userCorrections(),
                p.finalTechProfile(), result.createdAt(), metricsResponse
        );
    }

    public record AnalysisMetricsResponse(
            long totalElapsedMs,
            int repoCount,
            int triagePromptBytes,
            long triageElapsedMs,
            int summaryPromptBytes,
            long summaryElapsedMs,
            int synthesisPromptBytes,
            long synthesisElapsedMs
    ) {}
}
