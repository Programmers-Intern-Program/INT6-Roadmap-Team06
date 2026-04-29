package com.back.coach.service.github.synthesis;

import com.back.coach.global.code.GithubDepthLevel;
import com.back.coach.global.code.GithubEvidenceType;
import com.back.coach.service.github.AnalysisPayload;
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

    public String build(AnalysisPayload.StaticSignals signals, List<AnalysisPayload.RepoSummary> summaries) {
        String full = render(signals, summaries, /* compress */ false);
        if (full.getBytes().length <= MAX_PROMPT_BYTES) return full;

        log.warn("Synthesis prompt cap reached, compressing highlights to first {} per summary",
                COMPRESSED_HIGHLIGHTS_PER_SUMMARY);
        return render(signals, summaries, true);
    }

    private String render(AnalysisPayload.StaticSignals signals, List<AnalysisPayload.RepoSummary> summaries, boolean compress) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Static Signals\n");
        sb.append("activeRepos: ").append(signals.activeRepos()).append("\n");
        sb.append("commitFrequency: ").append(signals.commitFrequency()).append("\n");
        sb.append("contributionPattern: ").append(signals.contributionPattern()).append("\n");
        sb.append("primaryLanguages:\n");
        signals.primaryLanguages().forEach(pl ->
                sb.append("  - ").append(pl.lang()).append(": ").append(pl.ratio()).append("\n"));

        sb.append("\n## Per-Repo Summaries\n");
        for (AnalysisPayload.RepoSummary s : summaries) {
            sb.append("\n### ").append(s.repoName()).append(" (id=").append(s.repoId()).append(")\n");
            sb.append(s.summary()).append("\n");
            sb.append("highlights:\n");
            List<String> hl = compress && s.highlights().size() > COMPRESSED_HIGHLIGHTS_PER_SUMMARY
                    ? s.highlights().subList(0, COMPRESSED_HIGHLIGHTS_PER_SUMMARY)
                    : s.highlights();
            hl.forEach(h -> sb.append("  - ").append(h).append("\n"));
        }

        sb.append("\n## Output\n");
        sb.append("Output a single JSON object {techTags, depthEstimates, evidences, finalTechProfile}.\n");
        sb.append("DepthEstimate.level must be one of: ").append(DEPTH_VALUES).append("\n");
        sb.append("Evidence.type must be one of: ").append(EVIDENCE_VALUES).append("\n");
        return sb.toString();
    }
}
