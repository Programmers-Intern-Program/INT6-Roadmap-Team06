package com.back.coach.service.github.summary;

import com.back.coach.service.github.Champion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RepoSummaryPromptBuilderTest {

    private final RepoSummaryPromptBuilder builder = new RepoSummaryPromptBuilder();

    @Test
    @DisplayName("repo 식별자/언어와 kind별 섹션(commit/PR/issue)을 모두 렌더링한다")
    void build_includesRepoMetaAndAllKindSections() {
        List<ResolvedChampion> champions = List.of(
                new ResolvedChampion(Champion.Kind.COMMIT, "abc", "feat: OAuth", "diff body here"),
                new ResolvedChampion(Champion.Kind.PR, "42", "Add OAuth", "PR body discussion"),
                new ResolvedChampion(Champion.Kind.ISSUE, "7", "Login broken", "issue thread")
        );

        String prompt = builder.build("1", "user/cool-app", "Java", champions);

        assertThat(prompt).contains("user/cool-app").contains("Java");
        assertThat(prompt).contains("COMMIT").contains("abc").contains("feat: OAuth").contains("diff body here");
        assertThat(prompt).contains("PR").contains("42").contains("Add OAuth").contains("PR body discussion");
        assertThat(prompt).contains("ISSUE").contains("7").contains("Login broken").contains("issue thread");
        // RepoSummary JSON 응답 형식을 LLM에 알려주는 instruction marker가 있어야 함
        assertThat(prompt.toLowerCase()).contains("json");
    }

    @Test
    @DisplayName("항목 본문이 8KB를 초과하면 truncate marker가 붙는다")
    void build_truncatesItemBodyOver8KB() {
        String hugeBody = "X".repeat(20_000);
        List<ResolvedChampion> champions = List.of(
                new ResolvedChampion(Champion.Kind.COMMIT, "abc", "feat", hugeBody)
        );

        String prompt = builder.build("1", "r", "Java", champions);

        assertThat(prompt).contains("[…truncated]");
        // 한 항목 본문은 cap + marker 정도여야 함
        assertThat(prompt.length()).isLessThan(hugeBody.length());
    }

    @Test
    @DisplayName("총 24KB 캡을 초과하면 가장 뒤 champion부터 drop한다 (앞쪽 = 우선순위 높음)")
    void build_dropsLowerPriorityChampionsOverTotalCap() {
        // 각 6KB body × 5개 = 30KB → 일부 drop 발생
        List<ResolvedChampion> champions = IntStream.range(0, 5)
                .mapToObj(i -> new ResolvedChampion(Champion.Kind.COMMIT, "sha" + i, "s" + i, "Y".repeat(6_000)))
                .toList();

        String prompt = builder.build("1", "r", "Java", champions);

        assertThat(prompt.getBytes().length).isLessThanOrEqualTo(RepoSummaryPromptBuilder.MAX_PROMPT_BYTES);
        // sha0 (우선순위 최상)은 살아있고 sha4 (최하)는 drop
        assertThat(prompt).contains("sha0");
        assertThat(prompt).doesNotContain("sha4");
    }

    @Test
    @DisplayName("champion이 비어있어도 빌드 성공 — repo header만 포함")
    void build_emptyChampions() {
        String prompt = builder.build("1", "user/r", "Java", List.of());

        assertThat(prompt).contains("user/r");
        assertThat(prompt.toLowerCase()).contains("json"); // 출력 형식 안내는 항상 포함
    }
}
