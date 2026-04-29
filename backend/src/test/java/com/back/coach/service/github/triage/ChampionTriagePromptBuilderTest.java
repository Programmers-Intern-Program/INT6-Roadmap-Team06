package com.back.coach.service.github.triage;

import com.back.coach.service.github.RepoMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ChampionTriagePromptBuilderTest {

    private final ChampionTriagePromptBuilder builder = new ChampionTriagePromptBuilder();

    @Test
    @DisplayName("repo 이름/URL과 README 발췌(1KB cap)를 header에 포함하고 candidate 섹션에 commit/PR/issue를 모두 표현한다")
    void build_includesHeaderAndAllCandidateKinds() {
        RepoMetadata metadata = new RepoMetadata(
                "# my-cool-app\nA Spring Boot service for cool things.",
                Map.of(),
                List.of(),
                List.of(commit("abc123", "feat: add OAuth", List.of("src/auth/X.java"), 100, 5)),
                List.of(pr(42, "Add OAuth", "MERGED", 200, 10)),
                List.of(issue(7, "Login broken", "CLOSED", List.of("comment1")))
        );

        String prompt = builder.build("user/cool-app", "https://github.com/user/cool-app", metadata);

        assertThat(prompt).contains("user/cool-app");
        assertThat(prompt).contains("https://github.com/user/cool-app");
        assertThat(prompt).contains("my-cool-app"); // README 발췌
        assertThat(prompt).contains("COMMIT").contains("abc123").contains("feat: add OAuth");
        assertThat(prompt).contains("PR").contains("42").contains("Add OAuth");
        assertThat(prompt).contains("ISSUE").contains("7").contains("Login broken");
    }

    @Test
    @DisplayName("README가 1KB를 초과하면 truncate marker로 잘린다")
    void build_truncatesLongReadme() {
        String longReadme = "X".repeat(5_000);
        RepoMetadata metadata = new RepoMetadata(longReadme, Map.of(), List.of(), List.of(), List.of(), List.of());

        String prompt = builder.build("r", "u", metadata);

        // README 영역이 1KB + marker 정도여야 함
        assertThat(prompt).contains("[…truncated]");
        assertThat(prompt.length()).isLessThan(longReadme.length());
    }

    @Test
    @DisplayName("commit/PR/issue가 너무 많으면 가장 오래된 항목부터 drop해서 20KB 캡 안에 맞춘다 (README는 drop 대상 아님)")
    void build_dropsOldestCandidatesUnderCap() {
        // 각 commit ~100B → 500개면 ~50KB로 cap 초과
        List<RepoMetadata.CommitItem> manyCommits = IntStream.range(0, 500)
                .mapToObj(i -> commit("sha" + i, "subject " + i + " " + "X".repeat(50), List.of("p"), i, i))
                .toList();
        RepoMetadata metadata = new RepoMetadata("readme", Map.of(), List.of(), manyCommits, List.of(), List.of());

        String prompt = builder.build("r", "u", metadata);

        assertThat(prompt.getBytes().length).isLessThanOrEqualTo(ChampionTriagePromptBuilder.MAX_PROMPT_BYTES);
        // 최신(높은 index)이 살아있고 가장 오래된(0)은 drop
        assertThat(prompt).contains("sha499");
        assertThat(prompt).doesNotContain("sha0 ").doesNotContain("\"sha0\"").doesNotContain("subject 0 ");
    }

    @Test
    @DisplayName("MAX_COMMITS/MAX_PRS/MAX_ISSUES를 절대 상한으로 적용한다 (cap 도달 전이라도)")
    void build_appliesAbsoluteKindCaps() {
        // commits 200개 → 가장 최신 100개만 살아남아야 함
        List<RepoMetadata.CommitItem> manyCommits = IntStream.range(0, 200)
                .mapToObj(i -> commit("sha" + i, "s" + i, List.of(), 0, 0))
                .toList();
        RepoMetadata metadata = new RepoMetadata(null, Map.of(), List.of(), manyCommits, List.of(), List.of());

        String prompt = builder.build("r", "u", metadata);

        // 가장 오래된(index 0~99)은 빠짐
        assertThat(prompt).doesNotContain("\"sha0\"").doesNotContain("sha99 ");
        // 최신(100~199)은 살아있음
        assertThat(prompt).contains("sha199").contains("sha100");
    }

    private static RepoMetadata.CommitItem commit(String sha, String subject, List<String> paths, int add, int del) {
        return new RepoMetadata.CommitItem(sha, subject, "", paths, add, del, "");
    }

    private static RepoMetadata.PullRequestItem pr(int number, String title, String state, int add, int del) {
        return new RepoMetadata.PullRequestItem(number, title, "", state, add, del);
    }

    private static RepoMetadata.IssueItem issue(int number, String title, String state, List<String> comments) {
        return new RepoMetadata.IssueItem(number, title, "", state, comments);
    }
}
