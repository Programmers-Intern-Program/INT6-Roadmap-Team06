package com.back.coach.domain.github.service.summary;

import com.back.coach.domain.github.service.Champion;

import java.util.List;

// Orchestrator가 Champion (kind+ref) → 실제 본문(diff/PR body/issue thread)을 RepoMetadata에서 lookup해 만든 결과.
// subsequentCommitSubjects: 이 champion 이후에 온 커밋 제목들 (최신순 최대 5개). LLM이 reversal 감지에 사용.
public record ResolvedChampion(
        Champion.Kind kind,
        String ref,
        String headline,  // commit subject / PR title / issue title
        String body,      // 전처리된 diff / PR body+review / issue thread
        List<String> subsequentCommitSubjects
) {
    public ResolvedChampion(Champion.Kind kind, String ref, String headline, String body) {
        this(kind, ref, headline, body, List.of());
    }
}
