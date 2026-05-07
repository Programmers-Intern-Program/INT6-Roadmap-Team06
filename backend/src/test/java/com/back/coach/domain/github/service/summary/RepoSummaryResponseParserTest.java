package com.back.coach.domain.github.service.summary;

import com.back.coach.global.code.HighlightStatus;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepoSummaryResponseParserTest {

    private final RepoSummaryResponseParser parser = new RepoSummaryResponseParser();

    @Test
    @DisplayName("required 필드를 모두 포함하면 RepoSummary로 파싱한다")
    void parse_validResponse() {
        String json = """
                {
                  "repoId": "1",
                  "repoName": "user/cool-app",
                  "summary": "Spring Boot 백엔드 + OAuth 도입",
                  "highlights": [
                    {"text": "OAuth2 핸들러", "status": "ADOPTED"},
                    {"text": "JPA 마이그레이션", "status": "EVOLVED"}
                  ]
                }
                """;

        GithubAnalysisPayload.RepoSummary summary = parser.parse(json);

        assertThat(summary.repoId()).isEqualTo("1");
        assertThat(summary.repoName()).isEqualTo("user/cool-app");
        assertThat(summary.highlights()).hasSize(2);
        assertThat(summary.highlights().get(0).text()).isEqualTo("OAuth2 핸들러");
        assertThat(summary.highlights().get(0).status()).isEqualTo(HighlightStatus.ADOPTED);
        assertThat(summary.highlights().get(1).status()).isEqualTo(HighlightStatus.EVOLVED);
    }

    @Test
    @DisplayName("fenced JSON 응답도 RepoSummary로 파싱한다")
    void parse_fencedJsonResponse() {
        String json = """
                ```json
                {
                  "repoId": "1",
                  "repoName": "user/cool-app",
                  "summary": "Spring Boot 백엔드 + OAuth 도입",
                  "highlights": [
                    {"text": "OAuth2 핸들러", "status": "ADOPTED"}
                  ]
                }
                ```
                """;

        GithubAnalysisPayload.RepoSummary summary = parser.parse(json);

        assertThat(summary.repoName()).isEqualTo("user/cool-app");
        assertThat(summary.highlights().get(0).status()).isEqualTo(HighlightStatus.ADOPTED);
    }

    @Test
    @DisplayName("REVERSED 상태의 highlight도 정상 파싱한다")
    void parse_reversedHighlight() {
        String json = """
                {
                  "repoId": "2",
                  "repoName": "user/repo",
                  "summary": "실험적 기능 도입 후 롤백",
                  "highlights": [
                    {"text": "GraphQL 도입 시도", "status": "REVERSED"}
                  ]
                }
                """;

        GithubAnalysisPayload.RepoSummary summary = parser.parse(json);

        assertThat(summary.highlights()).hasSize(1);
        assertThat(summary.highlights().get(0).status()).isEqualTo(HighlightStatus.REVERSED);
    }

    @Test
    @DisplayName("required 필드 summary가 누락되면 LLM_INVALID_RESPONSE")
    void parse_missingSummary_throws() {
        String json = """
                {
                  "repoId": "1",
                  "repoName": "user/x",
                  "highlights": [{"text": "a", "status": "ADOPTED"}]
                }
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("highlights가 빈 배열이면 LLM_INVALID_RESPONSE (minItems=1)")
    void parse_emptyHighlights_throws() {
        String json = """
                {
                  "repoId": "1",
                  "repoName": "user/x",
                  "summary": "s",
                  "highlights": []
                }
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("highlight status가 허용되지 않은 값이면 LLM_INVALID_RESPONSE")
    void parse_invalidStatus_throws() {
        String json = """
                {
                  "repoId": "1",
                  "repoName": "user/x",
                  "summary": "s",
                  "highlights": [{"text": "a", "status": "UNKNOWN"}]
                }
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("highlights가 string 배열이면 LLM_INVALID_RESPONSE (schema 위반)")
    void parse_wrongType_throws() {
        String json = """
                {
                  "repoId": "1",
                  "repoName": "user/x",
                  "summary": "s",
                  "highlights": ["not an object"]
                }
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("repoId가 정수로 반환돼도 파싱에 성공한다 (smoke 회귀: LLM이 정수 반환)")
    void parse_repoIdAsInteger_succeeds() {
        String json = """
                {
                  "repoId": 12345,
                  "repoName": "user/repo",
                  "summary": "정수형 repoId 반환 케이스",
                  "highlights": [{"text": "기능 추가", "status": "ADOPTED"}]
                }
                """;

        GithubAnalysisPayload.RepoSummary summary = parser.parse(json);

        assertThat(summary.repoName()).isEqualTo("user/repo");
        assertThat(summary.highlights()).hasSize(1);
    }

    @Test
    @DisplayName("highlight 객체에 status 필드가 없으면 LLM_INVALID_RESPONSE")
    void parse_highlightMissingStatus_throws() {
        String json = """
                {
                  "repoId": "1",
                  "repoName": "user/x",
                  "summary": "s",
                  "highlights": [{"text": "status 없음"}]
                }
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_INVALID_RESPONSE);
    }
}
