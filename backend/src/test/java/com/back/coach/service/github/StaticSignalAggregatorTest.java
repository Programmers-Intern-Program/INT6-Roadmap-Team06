package com.back.coach.service.github;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class StaticSignalAggregatorTest {

    private final StaticSignalAggregator aggregator = new StaticSignalAggregator();

    @Test
    @DisplayName("languageBytes를 모든 repo에 걸쳐 합산해 비율을 계산한다")
    void aggregate_sumsLanguageBytesAcrossRepos() {
        StaticSignalAggregator.RepoSignalInput repo1 = input("Java", Map.of("Java", 7000L, "TypeScript", 3000L));
        StaticSignalAggregator.RepoSignalInput repo2 = input("Java", Map.of("Java", 5000L, "Python", 5000L));

        AnalysisPayload.StaticSignals signals = aggregator.aggregate(List.of(repo1, repo2));

        assertThat(signals.activeRepos()).isEqualTo(2);
        assertThat(signals.primaryLanguages())
                .extracting(AnalysisPayload.PrimaryLanguage::lang)
                .containsExactly("Java", "Python", "TypeScript");
        assertThat(signals.primaryLanguages().get(0).ratio()).isCloseTo(0.6, within(0.001));   // 12000/20000
        assertThat(signals.primaryLanguages().get(1).ratio()).isCloseTo(0.25, within(0.001));  // 5000/20000
        assertThat(signals.primaryLanguages().get(2).ratio()).isCloseTo(0.15, within(0.001));  // 3000/20000
    }

    @Test
    @DisplayName("repo가 1개여도 비율 합은 1.0이다")
    void aggregate_singleRepo() {
        StaticSignalAggregator.RepoSignalInput repo = input("Java", Map.of("Java", 1000L));

        AnalysisPayload.StaticSignals signals = aggregator.aggregate(List.of(repo));

        assertThat(signals.activeRepos()).isEqualTo(1);
        assertThat(signals.primaryLanguages()).hasSize(1);
        assertThat(signals.primaryLanguages().get(0).ratio()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("languageBytes가 모두 비어있으면 primary_language 카운트로 fallback한다")
    void aggregate_emptyLanguageBytes_fallsBackToPrimaryLanguageCount() {
        // Slice 3 fetcher가 listLanguages() 못 부른 경우 (e.g. archived repo, rate limit)
        StaticSignalAggregator.RepoSignalInput repo1 = input("Java", Map.of());
        StaticSignalAggregator.RepoSignalInput repo2 = input("Java", Map.of());
        StaticSignalAggregator.RepoSignalInput repo3 = input("Python", Map.of());

        AnalysisPayload.StaticSignals signals = aggregator.aggregate(List.of(repo1, repo2, repo3));

        assertThat(signals.activeRepos()).isEqualTo(3);
        assertThat(signals.primaryLanguages())
                .extracting(AnalysisPayload.PrimaryLanguage::lang)
                .containsExactly("Java", "Python");
        assertThat(signals.primaryLanguages().get(0).ratio()).isCloseTo(2.0 / 3, within(0.001));
        assertThat(signals.primaryLanguages().get(1).ratio()).isCloseTo(1.0 / 3, within(0.001));
    }

    @Test
    @DisplayName("commitFrequency/contributionPattern은 Slice 2에서 placeholder 값을 반환한다 (TODO slice-3)")
    void aggregate_returnsPlaceholderActivityFields() {
        AnalysisPayload.StaticSignals signals = aggregator.aggregate(
                List.of(input("Java", Map.of("Java", 100L)))
        );

        assertThat(signals.commitFrequency()).isEqualTo("WEEKLY");
        assertThat(signals.contributionPattern()).isEqualTo("CONSISTENT");
    }

    private static StaticSignalAggregator.RepoSignalInput input(String primaryLanguage, Map<String, Long> bytes) {
        RepoMetadata metadata = new RepoMetadata(null, bytes, List.of(), List.of(), List.of(), List.of());
        return new StaticSignalAggregator.RepoSignalInput(primaryLanguage, metadata);
    }
}
