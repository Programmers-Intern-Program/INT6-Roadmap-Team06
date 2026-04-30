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
        Instant createdAt
) {
    public static GithubAnalysisResponse from(GithubAnalysisService.GithubAnalysisResult result) {
        GithubAnalysisPayload p = result.payload();
        return new GithubAnalysisResponse(
                String.valueOf(result.id()), result.version(),
                p.staticSignals(), p.repoSummaries(), p.techTags(),
                p.depthEstimates(), p.evidences(), p.userCorrections(),
                p.finalTechProfile(), result.createdAt()
        );
    }
}
