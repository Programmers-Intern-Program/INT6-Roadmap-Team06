package com.back.coach.service.github.triage;

import com.back.coach.service.github.RepoMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// Stage 1 (Triage) 프롬프트 빌더. 본인 commit/PR/issue의 메타데이터만 + README framing.
// diff 본문은 절대 안 보내고, candidate가 cap 초과하면 가장 오래된 것부터 drop.
@Component
public class ChampionTriagePromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(ChampionTriagePromptBuilder.class);

    public static final int MAX_PROMPT_BYTES = 20 * 1024;
    public static final int MAX_README_BYTES = 1 * 1024;
    public static final int MAX_COMMITS = 100;
    public static final int MAX_PRS = 50;
    public static final int MAX_ISSUES = 50;

    private static final String TRUNCATED_MARKER = "[…truncated]";

    public String build(String repoName, String repoUrl, RepoMetadata metadata) {
        // README는 framing이라 drop 대상 아님 (header에 고정).
        String header = renderHeader(repoName, repoUrl, metadata.readmeExcerpt());

        List<String> candidates = renderCandidates(metadata);
        // 입력 순서대로 들어오므로 list 끝이 최신이라고 가정 — 가장 오래된(앞)을 먼저 drop.
        // 호출자(fetcher/orchestrator)가 시간 역순 보장 안 하면 정렬 책임이 그쪽으로.
        String body = assembleUnderCap(header, candidates);
        return body;
    }

    private String renderHeader(String repoName, String repoUrl, String readmeExcerpt) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Repository\n");
        sb.append("name: ").append(repoName).append("\n");
        sb.append("url: ").append(repoUrl).append("\n");
        if (readmeExcerpt != null && !readmeExcerpt.isBlank()) {
            sb.append("\n## README excerpt\n");
            sb.append(truncateBytes(readmeExcerpt, MAX_README_BYTES)).append("\n");
        }
        sb.append("\n## Candidates (pick 6-9 most technically meaningful)\n");
        return sb.toString();
    }

    private List<String> renderCandidates(RepoMetadata metadata) {
        List<String> lines = new ArrayList<>();
        // Kind 상한 적용: 절대 MAX 이상 안 보냄. 가장 최신만 유지 (list 끝 = 최신 가정).
        addLastN(metadata.commits(), MAX_COMMITS).forEach(c -> lines.add(renderCommit(c)));
        addLastN(metadata.pullRequests(), MAX_PRS).forEach(p -> lines.add(renderPr(p)));
        addLastN(metadata.issues(), MAX_ISSUES).forEach(i -> lines.add(renderIssue(i)));
        return lines;
    }

    private static <T> List<T> addLastN(List<T> source, int n) {
        if (source == null || source.isEmpty()) return List.of();
        return source.size() <= n ? source : source.subList(source.size() - n, source.size());
    }

    private String renderCommit(RepoMetadata.CommitItem c) {
        return "COMMIT \"" + c.sha() + "\" subject=" + c.subject()
                + " | paths=" + truncatePaths(c.paths()) + " | +" + c.additions() + "/-" + c.deletions();
    }

    private String renderPr(RepoMetadata.PullRequestItem p) {
        return "PR " + p.number() + " title=" + p.title() + " | state=" + p.state()
                + " | +" + p.additions() + "/-" + p.deletions();
    }

    private String renderIssue(RepoMetadata.IssueItem i) {
        return "ISSUE " + i.number() + " title=" + i.title() + " | state=" + i.state()
                + " | comments=" + (i.commentExcerpts() == null ? 0 : i.commentExcerpts().size());
    }

    private String truncatePaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) return "[]";
        return paths.size() <= 5 ? paths.toString() : paths.subList(0, 5) + "...";
    }

    // Header + 가능한 만큼의 candidate를 cap 안에 채운다. cap 초과 시 가장 오래된 candidate부터 drop.
    private String assembleUnderCap(String header, List<String> candidates) {
        StringBuilder sb = new StringBuilder(header);
        int dropped = 0;
        // 끝에서부터(최신부터) 추가하다가 cap 닿으면 stop. 결과는 다시 시간순으로 재정렬.
        List<String> kept = new ArrayList<>();
        int currentBytes = sb.toString().getBytes().length;
        for (int i = candidates.size() - 1; i >= 0; i--) {
            String line = candidates.get(i) + "\n";
            int lineBytes = line.getBytes().length;
            if (currentBytes + lineBytes > MAX_PROMPT_BYTES) {
                dropped = i + 1;
                break;
            }
            kept.add(0, line);
            currentBytes += lineBytes;
        }
        kept.forEach(sb::append);
        if (dropped > 0) {
            log.warn("Triage prompt cap reached, dropped {} oldest candidates", dropped);
        }
        return sb.toString();
    }

    private String truncateBytes(String text, int maxBytes) {
        byte[] bytes = text.getBytes();
        if (bytes.length <= maxBytes) return text;
        // UTF-8 multibyte 안전하게: 최대 maxBytes-marker.length까지 자른 뒤 marker 붙임.
        int budget = maxBytes - TRUNCATED_MARKER.getBytes().length;
        if (budget < 0) return TRUNCATED_MARKER;
        return new String(bytes, 0, budget) + TRUNCATED_MARKER;
    }
}
