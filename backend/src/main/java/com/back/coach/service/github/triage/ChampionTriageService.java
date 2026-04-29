package com.back.coach.service.github.triage;

import com.back.coach.external.llm.LlmClient;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.service.github.Champion;
import com.back.coach.service.github.RepoMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

// Stage 1 orchestrator. LLM 호출 → schema 검증 → 실패 시 최신 6 commit으로 fallback.
// fallback조차 불가능(commit 0개)할 때만 LLM_TRIAGE_FAILED surface.
@Service
public class ChampionTriageService {

    private static final Logger log = LoggerFactory.getLogger(ChampionTriageService.class);
    private static final int FALLBACK_COMMIT_COUNT = 6;

    private final LlmClient llmClient;
    private final ChampionTriagePromptBuilder promptBuilder;
    private final ChampionTriageResponseParser responseParser;

    public ChampionTriageService(LlmClient llmClient,
                                 ChampionTriagePromptBuilder promptBuilder,
                                 ChampionTriageResponseParser responseParser) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
    }

    public TriageResult triage(String repoName, String repoUrl, RepoMetadata metadata) {
        String prompt = promptBuilder.build(repoName, repoUrl, metadata);
        try {
            String llmResponse = llmClient.complete(prompt);
            List<Champion> champions = responseParser.parse(llmResponse);
            return new TriageResult(champions, false);
        } catch (ServiceException e) {
            log.warn("Triage 실패 ({}), fallback 시도. repo={}", e.getErrorCode(), repoName);
            return fallback(metadata);
        }
    }

    private TriageResult fallback(RepoMetadata metadata) {
        List<RepoMetadata.CommitItem> commits = metadata.commits();
        if (commits == null || commits.isEmpty()) {
            // 의미 있는 분석이 불가능 — 호출자에게 명확하게 알림.
            throw new ServiceException(ErrorCode.LLM_TRIAGE_FAILED);
        }
        // 최신부터 N개 (Step 4와 동일 invariant: list 끝 = 최신).
        int take = Math.min(FALLBACK_COMMIT_COUNT, commits.size());
        List<Champion> fallbackChampions = new ArrayList<>(take);
        for (int i = 0; i < take; i++) {
            RepoMetadata.CommitItem c = commits.get(commits.size() - 1 - i);
            fallbackChampions.add(new Champion(Champion.Kind.COMMIT, c.sha(), "fallback: 최신 활동"));
        }
        return new TriageResult(fallbackChampions, true);
    }

    public record TriageResult(List<Champion> champions, boolean fallback) {}
}
