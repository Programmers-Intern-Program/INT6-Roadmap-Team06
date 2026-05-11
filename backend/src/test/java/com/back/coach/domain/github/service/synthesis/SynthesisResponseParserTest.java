package com.back.coach.domain.github.service.synthesis;

import com.back.coach.global.code.GithubDepthLevel;
import com.back.coach.global.code.GithubEvidenceType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SynthesisResponseParserTest {

    private final SynthesisResponseParser parser = new SynthesisResponseParser();

    @Test
    @DisplayName("스키마를 만족하는 응답을 SynthesisResult로 파싱한다")
    void parse_validResponse() {
        String json = """
                {
                  "techTags": [{"skillName": "Spring Boot", "tagReason": "주요 백엔드"}],
                  "depthEstimates": [{"skillName": "Spring Boot", "level": "PRACTICAL", "reason": "여러 repo"}],
                  "evidences": [{"repoName": "user/a", "type": "COMMIT", "source": "abc123", "summary": "OAuth 핸들러"}],
                  "finalTechProfile": {"confirmedSkills": ["Spring Boot"], "focusAreas": ["Kubernetes"]}
                }
                """;

        SynthesisResponseParser.SynthesisResult result = parser.parse(json);

        assertThat(result.techTags()).hasSize(1);
        assertThat(result.depthEstimates().get(0).level()).isEqualTo(GithubDepthLevel.PRACTICAL);
        assertThat(result.evidences().get(0).type()).isEqualTo(GithubEvidenceType.COMMIT);
        assertThat(result.finalTechProfile().confirmedSkills()).contains("Spring Boot");
    }

    @Test
    @DisplayName("fenced JSON 응답도 SynthesisResult로 파싱한다")
    void parse_fencedJsonResponse() {
        String json = """
                ```json
                {
                  "techTags": [{"skillName": "Spring Boot", "tagReason": "주요 백엔드"}],
                  "depthEstimates": [{"skillName": "Spring Boot", "level": "PRACTICAL", "reason": "여러 repo"}],
                  "evidences": [{"repoName": "user/a", "type": "COMMIT", "source": "abc123", "summary": "OAuth 핸들러"}],
                  "finalTechProfile": {"confirmedSkills": ["Spring Boot"], "focusAreas": ["Kubernetes"]}
                }
                ```
                """;

        SynthesisResponseParser.SynthesisResult result = parser.parse(json);

        assertThat(result.techTags()).hasSize(1);
        assertThat(result.finalTechProfile().focusAreas()).contains("Kubernetes");
    }

    @Test
    @DisplayName("DepthEstimate.level이 enum 외 값이면 LLM_INVALID_RESPONSE")
    void parse_invalidLevel_throws() {
        String json = """
                {
                  "techTags": [],
                  "depthEstimates": [{"skillName": "X", "level": "GURU", "reason": "r"}],
                  "evidences": [],
                  "finalTechProfile": {"confirmedSkills": [], "focusAreas": []}
                }
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("필수 필드 finalTechProfile 누락 시 LLM_INVALID_RESPONSE")
    void parse_missingFinalTechProfile_throws() {
        String json = """
                {
                  "techTags": [],
                  "depthEstimates": [],
                  "evidences": []
                }
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("Evidence.type이 enum 외 값이면 REPO_METADATA로 보정한다")
    void parse_invalidEvidenceType_fallsBackToRepoMetadata() {
        String json = """
                {
                  "techTags": [],
                  "depthEstimates": [],
                  "evidences": [{"repoName": "r", "type": "VIDEO", "source": "s", "summary": "x"}],
                  "finalTechProfile": {"confirmedSkills": [], "focusAreas": []}
                }
                """;

        SynthesisResponseParser.SynthesisResult result = parser.parse(json);

        assertThat(result.evidences().get(0).type()).isEqualTo(GithubEvidenceType.REPO_METADATA);
    }

    @Test
    @DisplayName("Evidence.type이 소문자면 enum 대문자 값으로 보정한다")
    void parse_lowercaseEvidenceType_normalizesToEnum() {
        String json = """
                {
                  "techTags": [],
                  "depthEstimates": [],
                  "evidences": [{"repoName": "r", "type": "commit", "source": "s", "summary": "x"}],
                  "finalTechProfile": {"confirmedSkills": [], "focusAreas": []}
                }
                """;

        SynthesisResponseParser.SynthesisResult result = parser.parse(json);

        assertThat(result.evidences().get(0).type()).isEqualTo(GithubEvidenceType.COMMIT);
    }

    @Test
    @DisplayName("Evidence.type이 빈 문자열이면 REPO_METADATA로 보정한다")
    void parse_blankEvidenceType_fallsBackToRepoMetadata() {
        String json = """
                {
                  "techTags": [],
                  "depthEstimates": [],
                  "evidences": [{"repoName": "r", "type": " ", "source": "s", "summary": "x"}],
                  "finalTechProfile": {"confirmedSkills": [], "focusAreas": []}
                }
                """;

        SynthesisResponseParser.SynthesisResult result = parser.parse(json);

        assertThat(result.evidences().get(0).type()).isEqualTo(GithubEvidenceType.REPO_METADATA);
    }

    @Test
    @DisplayName("techTags에 skillName 대신 tech 필드를 쓰면 LLM_INVALID_RESPONSE (smoke 회귀)")
    void parse_techTagsWithTechFieldInsteadOfSkillName_throws() {
        String json = """
                {
                  "techTags": [{"tech": "Java", "tagReason": "주요 언어"}],
                  "depthEstimates": [],
                  "evidences": [],
                  "finalTechProfile": {"confirmedSkills": [], "focusAreas": []}
                }
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("evidences에 summary 대신 description 필드를 쓰면 LLM_INVALID_RESPONSE (smoke 회귀)")
    void parse_evidencesWithDescriptionInsteadOfSummary_throws() {
        String json = """
                {
                  "techTags": [],
                  "depthEstimates": [],
                  "evidences": [{"repoName": "r", "type": "CODE", "source": "s", "description": "d"}],
                  "finalTechProfile": {"confirmedSkills": [], "focusAreas": []}
                }
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("depthEstimates에 skillName 대신 skill 필드를 쓰면 LLM_INVALID_RESPONSE (smoke 회귀)")
    void parse_depthEstimatesWithSkillFieldInsteadOfSkillName_throws() {
        String json = """
                {
                  "techTags": [],
                  "depthEstimates": [{"skill": "Java", "level": "PRACTICAL", "reason": "r"}],
                  "evidences": [],
                  "finalTechProfile": {"confirmedSkills": [], "focusAreas": []}
                }
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_INVALID_RESPONSE);
    }
}
