package com.back.coach.service.github;

import com.back.coach.global.code.GithubDepthLevel;
import com.back.coach.global.code.GithubEvidenceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisPayloadJsonTest {

    private final AnalysisPayloadJson json = new AnalysisPayloadJson();

    @Test
    @DisplayName("AnalysisPayload는 직렬화 후 역직렬화하면 동일한 값으로 복원된다")
    void roundTrip_preservesAllFields() {
        AnalysisPayload original = new AnalysisPayload(
                new AnalysisPayload.StaticSignals(
                        List.of(new AnalysisPayload.PrimaryLanguage("Java", 0.7),
                                new AnalysisPayload.PrimaryLanguage("TypeScript", 0.3)),
                        3,
                        "WEEKLY",
                        "CONSISTENT"
                ),
                List.of(new AnalysisPayload.RepoSummary(
                        "1", "user/cool-app",
                        "Spring Boot 백엔드",
                        List.of("OAuth2 도입", "JPA 마이그레이션")
                )),
                List.of(new AnalysisPayload.TechTag("Spring Boot", "주요 백엔드 프레임워크")),
                List.of(new AnalysisPayload.DepthEstimate("Spring Boot", GithubDepthLevel.PRACTICAL, "여러 repo에서 일관된 사용")),
                List.of(new AnalysisPayload.Evidence("user/cool-app", GithubEvidenceType.COMMIT, "abc123", "OAuth2 핸들러 추가")),
                List.of(),
                new AnalysisPayload.FinalTechProfile(List.of("Spring Boot", "JPA"), List.of("Kubernetes")),
                new AnalysisPayload.AnalysisMeta(false)
        );

        String serialized = json.toJson(original);
        AnalysisPayload restored = json.fromJson(serialized);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("null 필드는 직렬화에서 생략된다 (NON_NULL inclusion)")
    void serialize_omitsNullFields() {
        AnalysisPayload payload = new AnalysisPayload(
                new AnalysisPayload.StaticSignals(List.of(), 0, "WEEKLY", "CONSISTENT"),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                new AnalysisPayload.FinalTechProfile(List.of(), List.of()),
                null
        );

        String serialized = json.toJson(payload);

        assertThat(serialized).doesNotContain("\"meta\"");
    }

    @Test
    @DisplayName("알 수 없는 필드가 들어와도 역직렬화는 성공한다 (FAIL_ON_UNKNOWN_PROPERTIES=false)")
    void deserialize_tolerantToUnknownFields() {
        String jsonWithExtra = """
                {
                  "staticSignals": {"primaryLanguages": [], "activeRepos": 0, "commitFrequency": "WEEKLY", "contributionPattern": "CONSISTENT"},
                  "repoSummaries": [],
                  "techTags": [],
                  "depthEstimates": [],
                  "evidences": [],
                  "userCorrections": [],
                  "finalTechProfile": {"confirmedSkills": [], "focusAreas": []},
                  "futureFieldFromV2": "ignored"
                }
                """;

        AnalysisPayload restored = json.fromJson(jsonWithExtra);

        assertThat(restored.staticSignals().activeRepos()).isZero();
    }
}
