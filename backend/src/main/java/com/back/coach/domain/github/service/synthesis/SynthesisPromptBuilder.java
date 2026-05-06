package com.back.coach.domain.github.service.synthesis;

import com.back.coach.global.code.GithubDepthLevel;
import com.back.coach.global.code.GithubEvidenceType;
import com.back.coach.global.code.HighlightStatus;
import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// Stage 3 (Synthesis) 프롬프트. static signals + 모든 per-repo summary 묶어 1회 호출.
// 캡 초과 시 각 summary의 highlights를 앞 3개로 줄여 재시도.
@Component
public class SynthesisPromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(SynthesisPromptBuilder.class);

    public static final int MAX_PROMPT_BYTES = 16 * 1024;
    public static final int COMPRESSED_HIGHLIGHTS_PER_SUMMARY = 3;

    private static final String DEPTH_VALUES = Arrays.stream(GithubDepthLevel.values())
            .map(Enum::name).collect(Collectors.joining(", "));
    private static final String EVIDENCE_VALUES = Arrays.stream(GithubEvidenceType.values())
            .map(Enum::name).collect(Collectors.joining(", "));

    public String build(GithubAnalysisPayload.StaticSignals signals, List<GithubAnalysisPayload.RepoSummary> summaries) {
        String full = render(signals, summaries, /* compress */ false);
        if (full.getBytes().length <= MAX_PROMPT_BYTES) return full;

        log.warn("Synthesis prompt cap reached, compressing highlights to first {} per summary",
                COMPRESSED_HIGHLIGHTS_PER_SUMMARY);
        return render(signals, summaries, true);
    }

    private String render(GithubAnalysisPayload.StaticSignals signals, List<GithubAnalysisPayload.RepoSummary> summaries, boolean compress) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Static Signals\n");
        sb.append("activeRepos: ").append(signals.activeRepos()).append("\n");
        sb.append("commitFrequency: ").append(signals.commitFrequency()).append("\n");
        sb.append("contributionPattern: ").append(signals.contributionPattern()).append("\n");
        sb.append("primaryLanguages:\n");
        signals.primaryLanguages().forEach(pl ->
                sb.append("  - ").append(pl.lang()).append(": ").append(pl.ratio()).append("\n"));

        sb.append("\n## Per-Repo Summaries\n");
        for (GithubAnalysisPayload.RepoSummary s : summaries) {
            sb.append("\n### ").append(s.repoName()).append(" (id=").append(s.repoId()).append(")\n");
            sb.append(s.summary()).append("\n");
            sb.append("highlights:\n");
            List<GithubAnalysisPayload.Highlight> hl = compress && s.highlights().size() > COMPRESSED_HIGHLIGHTS_PER_SUMMARY
                    ? s.highlights().subList(0, COMPRESSED_HIGHLIGHTS_PER_SUMMARY)
                    : s.highlights();
            hl.forEach(h -> {
                String prefix = h.status() == HighlightStatus.REVERSED ? "[REVERSED] " : "";
                sb.append("  - ").append(prefix).append(h.text()).append("\n");
            });
        }

        sb.append("\n## Output\n");
        sb.append("Output ONLY a single JSON object with exactly these fields and structures:\n");
        sb.append("{\n");
        sb.append("  \"techTags\": [ { \"skillName\": string, \"tagReason\": string } ],\n");
        sb.append("  \"depthEstimates\": [ { \"skillName\": string, \"level\": \"").append(DEPTH_VALUES).append("\", \"reason\": string } ],\n");
        sb.append("  \"evidences\": [ { \"repoName\": string, \"type\": \"").append(EVIDENCE_VALUES).append("\", \"source\": string, \"summary\": string } ],\n");
        sb.append("  \"finalTechProfile\": { \"confirmedSkills\": [ string ], \"focusAreas\": [ string ] }\n");
        sb.append("}\n");
        sb.append("Do NOT rename fields. Do NOT add extra fields.\n");
        return sb.toString();
    }
}
