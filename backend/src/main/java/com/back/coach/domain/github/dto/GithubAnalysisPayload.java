package com.back.coach.domain.github.dto;

import com.back.coach.global.code.GithubDepthLevel;
import com.back.coach.global.code.GithubEvidenceType;

import java.util.List;

public record GithubAnalysisPayload(
        StaticSignals staticSignals,
        List<RepoSummary> repoSummaries,
        List<TechTag> techTags,
        List<DepthEstimate> depthEstimates,
        List<GithubEvidence> evidences,
        List<GithubUserCorrection> userCorrections,
        FinalTechProfile finalTechProfile
) {

    public record StaticSignals(
            List<PrimaryLanguage> primaryLanguages,
            Integer activeRepos,
            String commitFrequency,
            String contributionPattern
    ) {
    }

    public record PrimaryLanguage(
            String lang,
            Double ratio
    ) {
    }

    public record RepoSummary(
            String repoId,
            String repoName,
            String summary,
            List<String> highlights
    ) {
    }

    public record TechTag(
            String skillName,
            String tagReason
    ) {
    }

    public record DepthEstimate(
            String skillName,
            GithubDepthLevel level,
            String reason
    ) {
    }

    public record GithubEvidence(
            String repoName,
            GithubEvidenceType type,
            String source,
            String summary
    ) {
    }

    public record GithubUserCorrection(
            String skillName,
            String correction
    ) {
    }

    public record FinalTechProfile(
            List<String> confirmedSkills,
            List<String> focusAreas
    ) {
    }
}
