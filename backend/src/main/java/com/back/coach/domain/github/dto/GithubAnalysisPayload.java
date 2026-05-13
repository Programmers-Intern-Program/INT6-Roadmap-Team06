package com.back.coach.domain.github.dto;

import com.back.coach.global.code.GithubDepthLevel;
import com.back.coach.global.code.GithubEvidenceType;
import com.back.coach.global.code.HighlightStatus;

import java.util.List;

public record GithubAnalysisPayload(
        StaticSignals staticSignals,
        List<RepoSummary> repoSummaries,
        List<TechTag> techTags,
        List<DepthEstimate> depthEstimates,
        List<GithubEvidence> evidences,
        List<GithubUserCorrection> userCorrections,
        FinalTechProfile finalTechProfile,
        AnalysisTrace analysisTrace
) {

    public GithubAnalysisPayload(
            StaticSignals staticSignals,
            List<RepoSummary> repoSummaries,
            List<TechTag> techTags,
            List<DepthEstimate> depthEstimates,
            List<GithubEvidence> evidences,
            List<GithubUserCorrection> userCorrections,
            FinalTechProfile finalTechProfile
    ) {
        this(staticSignals, repoSummaries, techTags, depthEstimates, evidences,
                userCorrections, finalTechProfile, null);
    }

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
            List<Highlight> highlights
    ) {
    }

    public record Highlight(
            String text,
            HighlightStatus status
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

    public record AnalysisTrace(
            List<RepoMetadataTrace> repositories,
            List<TriageTrace> triage,
            List<RepoSummaryTrace> repoSummaries,
            SynthesisTrace synthesis
    ) {
    }

    public record RepoMetadataTrace(
            String repoId,
            String repoName,
            Boolean core,
            Integer commitCount,
            Integer pullRequestCount,
            Integer issueCount,
            Integer languageCount,
            Integer dependencyFileCount
    ) {
    }

    public record TriageTrace(
            String repoId,
            String repoName,
            String promptVersion,
            Integer promptBytes,
            Long elapsedMs,
            Boolean fallback,
            List<ChampionTrace> champions
    ) {
    }

    public record ChampionTrace(
            String kind,
            String ref,
            String reason
    ) {
    }

    public record RepoSummaryTrace(
            String repoId,
            String repoName,
            String promptVersion,
            Integer promptBytes,
            Long elapsedMs,
            Integer highlightCount,
            Integer summaryLength
    ) {
    }

    public record SynthesisTrace(
            String promptVersion,
            Integer promptBytes,
            Long elapsedMs,
            Integer techTagCount,
            Integer depthEstimateCount,
            Integer evidenceCount,
            Integer confirmedSkillCount
    ) {
    }
}
