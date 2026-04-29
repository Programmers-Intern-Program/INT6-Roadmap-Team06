package com.back.coach.service.github.triage;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.service.github.Champion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChampionTriageResponseParserTest {

    private final ChampionTriageResponseParser parser = new ChampionTriageResponseParser();

    @Test
    @DisplayName("스키마를 만족하는 champion JSON을 파싱한다")
    void parse_validResponse() {
        String json = """
                {
                  "champions": [
                    {"kind": "COMMIT", "ref": "abc123", "reason": "OAuth 도입"},
                    {"kind": "PR", "ref": "42", "reason": "라우팅 설계"},
                    {"kind": "ISSUE", "ref": "7", "reason": "디자인 결정 토론"}
                  ]
                }
                """;

        List<Champion> champions = parser.parse(json);

        assertThat(champions).hasSize(3);
        assertThat(champions.get(0).kind()).isEqualTo(Champion.Kind.COMMIT);
        assertThat(champions.get(0).ref()).isEqualTo("abc123");
        assertThat(champions.get(1).kind()).isEqualTo(Champion.Kind.PR);
        assertThat(champions.get(2).kind()).isEqualTo(Champion.Kind.ISSUE);
    }

    @Test
    @DisplayName("champion이 0개면 LLM_INVALID_RESPONSE를 던진다 (minItems=1)")
    void parse_emptyChampions_throws() {
        String json = "{\"champions\": []}";

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("champion이 9개를 넘으면 LLM_INVALID_RESPONSE를 던진다 (maxItems=9)")
    void parse_tooManyChampions_throws() {
        StringBuilder sb = new StringBuilder("{\"champions\":[");
        for (int i = 0; i < 10; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"kind\":\"COMMIT\",\"ref\":\"sha").append(i).append("\",\"reason\":\"r\"}");
        }
        sb.append("]}");

        assertThatThrownBy(() -> parser.parse(sb.toString()))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("kind 값이 enum 외 값이면 LLM_INVALID_RESPONSE를 던진다")
    void parse_invalidKind_throws() {
        String json = """
                {"champions": [{"kind": "DISCUSSION", "ref": "x", "reason": "r"}]}
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("필수 필드 ref가 누락되면 LLM_INVALID_RESPONSE를 던진다")
    void parse_missingRequiredField_throws() {
        String json = """
                {"champions": [{"kind": "COMMIT", "reason": "r"}]}
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_INVALID_RESPONSE);
    }
}
