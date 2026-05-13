package com.back.coach.domain.github.service.synthesis;

import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.global.code.HighlightStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SynthesisPromptBuilderTest {

    private final SynthesisPromptBuilder builder = new SynthesisPromptBuilder();

    @Test
    @DisplayName("static signals와 per-repo summary들을 모두 embed하고 GithubDepthLevel/GithubEvidenceType enum 값을 제시한다")
    void build_embedsSignalsAndSummariesAndEnumGuidance() {
        GithubAnalysisPayload.StaticSignals signals = new GithubAnalysisPayload.StaticSignals(
                List.of(new GithubAnalysisPayload.PrimaryLanguage("Java", 0.7),
                        new GithubAnalysisPayload.PrimaryLanguage("Python", 0.3)),
                2, "WEEKLY", "CONSISTENT"
        );
        List<GithubAnalysisPayload.RepoSummary> summaries = List.of(
                new GithubAnalysisPayload.RepoSummary("1", "user/a", "Spring Boot 백엔드",
                        List.of(
                                new GithubAnalysisPayload.Highlight("OAuth2", HighlightStatus.ADOPTED),
                                new GithubAnalysisPayload.Highlight("JPA", HighlightStatus.EVOLVED),
                                new GithubAnalysisPayload.Highlight("Flyway", HighlightStatus.REVERSED),
                                new GithubAnalysisPayload.Highlight("Testcontainers", HighlightStatus.ADOPTED)
                        )),
                new GithubAnalysisPayload.RepoSummary("2", "user/b", "Python ETL",
                        List.of(
                                new GithubAnalysisPayload.Highlight("pandas", HighlightStatus.ADOPTED),
                                new GithubAnalysisPayload.Highlight("Airflow", HighlightStatus.ADOPTED)
                        ))
        );

        String prompt = builder.build(signals, summaries);

        assertThat(prompt).contains("Java").contains("0.7").contains("Python");
        assertThat(prompt).contains("user/a").contains("user/b");
        assertThat(prompt).contains("OAuth2").contains("pandas");
        assertThat(prompt).contains("[REVERSED] Flyway");
        assertThat(prompt).doesNotContain("[REVERSED] OAuth2");
        // 출력 형식 / enum 값 안내
        assertThat(prompt).contains("INTRO").contains("APPLIED").contains("PRACTICAL").contains("DEEP");
        assertThat(prompt).contains("README").contains("CODE").contains("CONFIG").contains("REPO_METADATA").contains("COMMIT");
        assertThat(prompt).contains("Write user-visible natural-language values in Korean");
        assertThat(prompt).contains("techTags[].tagReason", "depthEstimates[].reason", "evidences[].summary", "finalTechProfile.focusAreas");
    }

    @Test
    @DisplayName("16KB 캡을 초과하면 각 RepoSummary의 highlights를 앞 3개로 압축한다")
    void build_overflowCompressesHighlights() {
        GithubAnalysisPayload.StaticSignals signals = new GithubAnalysisPayload.StaticSignals(List.of(), 0, "WEEKLY", "CONSISTENT");
        // 각 summary에 큰 highlight 30개 → 압축 안 하면 cap 초과
        List<GithubAnalysisPayload.RepoSummary> bigSummaries = IntStream.range(0, 8)
                .mapToObj(i -> new GithubAnalysisPayload.RepoSummary(
                        String.valueOf(i), "repo" + i, "S".repeat(500),
                        IntStream.range(0, 30).mapToObj(j -> new GithubAnalysisPayload.Highlight(
                                "highlight-" + i + "-" + j + "-" + "X".repeat(80),
                                HighlightStatus.ADOPTED
                        )).toList()
                ))
                .toList();

        String prompt = builder.build(signals, bigSummaries);

        assertThat(prompt.getBytes().length).isLessThanOrEqualTo(SynthesisPromptBuilder.MAX_PROMPT_BYTES);
        // 압축 후에도 처음 3개 highlight는 살아있어야 함 (대표 repo 0번 기준)
        assertThat(prompt).contains("highlight-0-0").contains("highlight-0-1").contains("highlight-0-2");
        // 4번째 이후는 drop
        assertThat(prompt).doesNotContain("highlight-0-29");
    }

    @Test
    @DisplayName("summary가 비어있어도 빌드 성공 (static signals만 포함)")
    void build_emptySummaries() {
        GithubAnalysisPayload.StaticSignals signals = new GithubAnalysisPayload.StaticSignals(
                List.of(new GithubAnalysisPayload.PrimaryLanguage("Java", 1.0)),
                1, "WEEKLY", "CONSISTENT"
        );

        String prompt = builder.build(signals, List.of());

        assertThat(prompt).contains("Java");
        assertThat(prompt.toLowerCase()).contains("json");
    }
}
