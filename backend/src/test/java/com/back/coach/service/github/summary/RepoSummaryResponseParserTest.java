package com.back.coach.service.github.summary;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.service.github.AnalysisPayload;
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
                  "highlights": ["OAuth2 핸들러", "JPA 마이그레이션"]
                }
                """;

        AnalysisPayload.RepoSummary summary = parser.parse(json);

        assertThat(summary.repoId()).isEqualTo("1");
        assertThat(summary.repoName()).isEqualTo("user/cool-app");
        assertThat(summary.highlights()).hasSize(2);
    }

    @Test
    @DisplayName("required 필드 summary가 누락되면 LLM_INVALID_RESPONSE")
    void parse_missingSummary_throws() {
        String json = """
                {
                  "repoId": "1",
                  "repoName": "user/x",
                  "highlights": ["a"]
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
    @DisplayName("타입 불일치(highlights가 string)이면 LLM_INVALID_RESPONSE")
    void parse_wrongType_throws() {
        String json = """
                {
                  "repoId": "1",
                  "repoName": "user/x",
                  "summary": "s",
                  "highlights": "not an array"
                }
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_INVALID_RESPONSE);
    }
}
