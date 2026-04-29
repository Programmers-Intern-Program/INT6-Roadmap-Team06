package com.back.coach.service.github;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StaticSignalAggregator {

    // TODO(slice-3): commit timeline 기반으로 실제 빈도/패턴 계산. 지금은 honest stub.
    private static final String PLACEHOLDER_COMMIT_FREQUENCY = "WEEKLY";
    private static final String PLACEHOLDER_CONTRIBUTION_PATTERN = "CONSISTENT";

    public AnalysisPayload.StaticSignals aggregate(List<RepoSignalInput> inputs) {
        List<AnalysisPayload.PrimaryLanguage> languages = computeLanguageRatios(inputs);
        return new AnalysisPayload.StaticSignals(
                languages,
                inputs.size(),
                PLACEHOLDER_COMMIT_FREQUENCY,
                PLACEHOLDER_CONTRIBUTION_PATTERN
        );
    }

    private List<AnalysisPayload.PrimaryLanguage> computeLanguageRatios(List<RepoSignalInput> inputs) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (RepoSignalInput input : inputs) {
            Map<String, Long> bytes = input.metadata().languageBytes();
            if (bytes != null) {
                bytes.forEach((lang, count) -> totals.merge(lang, count, Long::sum));
            }
        }

        // listLanguages() fallback: 실제 byte 데이터가 없으면 primary_language 카운트로 계산.
        if (totals.isEmpty()) {
            for (RepoSignalInput input : inputs) {
                if (input.primaryLanguage() != null) {
                    totals.merge(input.primaryLanguage(), 1L, Long::sum);
                }
            }
        }

        long sum = totals.values().stream().mapToLong(Long::longValue).sum();
        if (sum == 0) {
            return List.of();
        }

        return totals.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new AnalysisPayload.PrimaryLanguage(e.getKey(), (double) e.getValue() / sum))
                .toList();
    }

    // 집계기는 entity 없이 동작 — 호출자가 (primary_language, RepoMetadata)만 추려 넘긴다.
    public record RepoSignalInput(String primaryLanguage, RepoMetadata metadata) {}
}
