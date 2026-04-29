package com.back.coach.service.github.synthesis;

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
    @DisplayName("Evidence.type이 enum 외 값이면 LLM_INVALID_RESPONSE")
    void parse_invalidEvidenceType_throws() {
        String json = """
                {
                  "techTags": [],
                  "depthEstimates": [],
                  "evidences": [{"repoName": "r", "type": "VIDEO", "source": "s", "summary": "x"}],
                  "finalTechProfile": {"confirmedSkills": [], "focusAreas": []}
                }
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_INVALID_RESPONSE);
    }
}
