package com.back.coach.service.github;

import com.back.coach.global.code.GithubDepthLevel;
import com.back.coach.global.code.GithubEvidenceType;

import java.util.List;

public record AnalysisPayload(
        StaticSignals staticSignals,
        List<RepoSummary> repoSummaries,
        List<TechTag> techTags,
        List<DepthEstimate> depthEstimates,
        List<Evidence> evidences,
        List<UserCorrection> userCorrections,
        FinalTechProfile finalTechProfile,
        AnalysisMeta meta
) {
    public record StaticSignals(
            List<PrimaryLanguage> primaryLanguages,
            int activeRepos,
            String commitFrequency,
            String contributionPattern
    ) {}

    public record PrimaryLanguage(String lang, double ratio) {}

    public record RepoSummary(
            String repoId,
            String repoName,
            String summary,
            List<String> highlights
    ) {}

    public record TechTag(String skillName, String tagReason) {}

    public record DepthEstimate(String skillName, GithubDepthLevel level, String reason) {}

    public record Evidence(String repoName, GithubEvidenceType type, String source, String summary) {}

    public record UserCorrection(String skillName, String correction) {}

    public record FinalTechProfile(List<String> confirmedSkills, List<String> focusAreas) {}

    public record AnalysisMeta(boolean triageFallback) {}
}
