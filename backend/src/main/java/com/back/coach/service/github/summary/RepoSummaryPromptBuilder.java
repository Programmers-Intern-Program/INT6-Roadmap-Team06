package com.back.coach.service.github.summary;

import com.back.coach.service.github.Champion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// Stage 2 프롬프트 빌더. 입력 = ResolvedChampion (orchestrator가 RepoMetadata에서 본문 매칭한 결과).
// 항목 본문이 cap 초과하면 truncate, 전체 cap 초과하면 우선순위 낮은(뒤) champion부터 drop.
// champion 우선순위 = triage가 반환한 순서 (앞이 더 중요).
@Component
public class RepoSummaryPromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(RepoSummaryPromptBuilder.class);

    public static final int MAX_ITEM_BYTES = 8 * 1024;
    public static final int MAX_PROMPT_BYTES = 24 * 1024;

    private static final String TRUNCATED_MARKER = "[…truncated]";

    private static final Map<Champion.Kind, String> SECTION_TITLE = Map.of(
            Champion.Kind.COMMIT, "## Commits",
            Champion.Kind.PR, "## Pull Requests",
            Champion.Kind.ISSUE, "## Issues"
    );

    public String build(String repoId, String repoName, String primaryLanguage, List<ResolvedChampion> champions) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Repository\n");
        sb.append("repoId: ").append(repoId).append("\n");
        sb.append("repoName: ").append(repoName).append("\n");
        sb.append("primaryLanguage: ").append(primaryLanguage == null ? "unknown" : primaryLanguage).append("\n\n");
        sb.append("Below are the user's most technically meaningful contributions. ");
        sb.append("Output a single JSON object {repoId, repoName, summary, highlights[]}.\n\n");

        int dropped = 0;
        Champion.Kind currentSection = null;
        for (int i = 0; i < champions.size(); i++) {
            ResolvedChampion c = champions.get(i);
            if (c.kind() != currentSection) {
                String header = "\n" + SECTION_TITLE.getOrDefault(c.kind(), "## Other") + "\n";
                if (sb.length() + header.length() > MAX_PROMPT_BYTES) {
                    dropped = champions.size() - i;
                    break;
                }
                sb.append(header);
                currentSection = c.kind();
            }
            String item = renderItem(c);
            if (sb.length() + item.length() > MAX_PROMPT_BYTES) {
                dropped = champions.size() - i;
                break;
            }
            sb.append(item);
        }
        if (dropped > 0) {
            log.warn("RepoSummary prompt cap reached, dropped {} lower-priority champions", dropped);
        }
        return sb.toString();
    }

    private String renderItem(ResolvedChampion c) {
        StringBuilder item = new StringBuilder();
        item.append(c.kind()).append(" ").append(c.ref()).append(": ").append(c.headline()).append("\n");
        item.append(truncateBytes(c.body() == null ? "" : c.body(), MAX_ITEM_BYTES)).append("\n");
        return item.toString();
    }

    private String truncateBytes(String text, int maxBytes) {
        byte[] bytes = text.getBytes();
        if (bytes.length <= maxBytes) return text;
        int budget = maxBytes - TRUNCATED_MARKER.getBytes().length;
        if (budget < 0) return TRUNCATED_MARKER;
        return new String(bytes, 0, budget) + TRUNCATED_MARKER;
    }
}
