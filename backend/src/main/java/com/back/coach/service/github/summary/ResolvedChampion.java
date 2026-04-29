package com.back.coach.service.github.summary;

import com.back.coach.service.github.Champion;

// Orchestrator가 Champion (kind+ref) → 실제 본문(diff/PR body/issue thread)을 RepoMetadata에서 lookup해 만든 결과.
// builder는 이 record만 받아 dumb하게 렌더링한다.
public record ResolvedChampion(
        Champion.Kind kind,
        String ref,
        String headline,  // commit subject / PR title / issue title
        String body       // 전처리된 diff / PR body+review / issue thread
) {}
