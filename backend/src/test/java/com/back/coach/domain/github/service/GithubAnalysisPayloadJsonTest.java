package com.back.coach.domain.github.service;

import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.global.code.GithubDepthLevel;
import com.back.coach.global.code.GithubEvidenceType;
import com.back.coach.global.code.HighlightStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GithubAnalysisPayloadJsonTest {

    private final GithubAnalysisPayloadJson json = new GithubAnalysisPayloadJson();

    @Test
    @DisplayName("GithubAnalysisPayload는 직렬화 후 역직렬화하면 동일한 값으로 복원된다")
    void roundTrip_preservesAllFields() {
        GithubAnalysisPayload original = new GithubAnalysisPayload(
                new GithubAnalysisPayload.StaticSignals(
                        List.of(new GithubAnalysisPayload.PrimaryLanguage("Java", 0.7),
                                new GithubAnalysisPayload.PrimaryLanguage("TypeScript", 0.3)),
                        3, "WEEKLY", "CONSISTENT"),
                List.of(new GithubAnalysisPayload.RepoSummary("1", "user/cool-app", "Spring Boot 백엔드",
                        List.of(new GithubAnalysisPayload.Highlight("OAuth2 도입", HighlightStatus.ADOPTED),
                                new GithubAnalysisPayload.Highlight("JPA 마이그레이션", HighlightStatus.EVOLVED)))),
                List.of(new GithubAnalysisPayload.TechTag("Spring Boot", "주요 백엔드 프레임워크")),
                List.of(new GithubAnalysisPayload.DepthEstimate("Spring Boot", GithubDepthLevel.PRACTICAL, "여러 repo에서 일관된 사용")),
                List.of(new GithubAnalysisPayload.GithubEvidence("user/cool-app", GithubEvidenceType.COMMIT, "abc123", "OAuth2 핸들러 추가")),
                List.of(),
                new GithubAnalysisPayload.FinalTechProfile(List.of("Spring Boot", "JPA"), List.of("Kubernetes"))
        );

        String serialized = json.toJson(original);
        GithubAnalysisPayload restored = json.fromJson(serialized);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.analysisTrace()).isNull();
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

        GithubAnalysisPayload restored = json.fromJson(jsonWithExtra);

        assertThat(restored.staticSignals().activeRepos()).isZero();
    }

    @Test
    @DisplayName("analysisTrace가 없는 기존 payload도 역직렬화된다")
    void deserialize_legacyPayloadWithoutTrace() {
        String legacyPayload = """
                {
                  "staticSignals": {"primaryLanguages": [], "activeRepos": 1, "commitFrequency": "WEEKLY", "contributionPattern": "CONSISTENT"},
                  "repoSummaries": [],
                  "techTags": [],
                  "depthEstimates": [],
                  "evidences": [],
                  "userCorrections": [],
                  "finalTechProfile": {"confirmedSkills": ["Java"], "focusAreas": ["Backend"]}
                }
                """;

        GithubAnalysisPayload restored = json.fromJson(legacyPayload);

        assertThat(restored.finalTechProfile().confirmedSkills()).containsExactly("Java");
        assertThat(restored.analysisTrace()).isNull();
    }
}
