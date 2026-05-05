package com.back.coach.domain.diagnosis.service;

import com.back.coach.global.code.DiagnosisSeverity;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnosisResponseParserTest {

    private final DiagnosisResponseParser parser = new DiagnosisResponseParser();

    @Test
    void parse_whenResponseMatchesSchema_returnsDiagnosisResult() {
        DiagnosisResponseParser.DiagnosisResult result = parser.parse(validJson());

        assertThat(result.summary()).isEqualTo("Redis 보완 필요");
        assertThat(result.missingSkills()).hasSize(1);
        assertThat(result.missingSkills().get(0).skillName()).isEqualTo("Redis");
        assertThat(result.missingSkills().get(0).severity()).isEqualTo(DiagnosisSeverity.HIGH);
        assertThat(result.strengths()).containsExactly("Spring Boot");
        assertThat(result.recommendations()).containsExactly("Redis 캐시와 TTL 기반 설계를 먼저 학습");
    }

    @Test
    void parse_whenResponseIsFencedJson_returnsDiagnosisResult() {
        DiagnosisResponseParser.DiagnosisResult result = parser.parse(fencedJson(validJson()));

        assertThat(result.summary()).isEqualTo("Redis 보완 필요");
        assertThat(result.missingSkills()).hasSize(1);
    }

    @Test
    void parse_whenResponseHasTextAroundJson_returnsDiagnosisResult() {
        DiagnosisResponseParser.DiagnosisResult result = parser.parse(textWrappedJson(validJson()));

        assertThat(result.summary()).isEqualTo("Redis 보완 필요");
        assertThat(result.strengths()).containsExactly("Spring Boot");
    }

    @Test
    void parse_whenSeverityIsInvalid_throwsLlmInvalidResponse() {
        String json = validJson().replace("\"HIGH\"", "\"CRITICAL\"");

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LLM_INVALID_RESPONSE);
    }

    @Test
    void parse_whenPriorityOrderIsInvalid_throwsLlmInvalidResponse() {
        String json = validJson().replace("\"priorityOrder\": 1", "\"priorityOrder\": 0");

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LLM_INVALID_RESPONSE);
    }

    @Test
    void parse_whenRequiredFieldIsMissing_throwsLlmInvalidResponse() {
        String json = """
                {
                  "summary": "Redis 보완 필요",
                  "missingSkills": [],
                  "strengths": []
                }
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LLM_INVALID_RESPONSE);
    }

    private String validJson() {
        return """
                {
                  "summary": "Redis 보완 필요",
                  "missingSkills": [
                    {
                      "skillName": "Redis",
                      "severity": "HIGH",
                      "reason": "캐시 설계 경험이 부족함",
                      "priorityOrder": 1
                    }
                  ],
                  "strengths": ["Spring Boot"],
                  "recommendations": ["Redis 캐시와 TTL 기반 설계를 먼저 학습"]
                }
                """;
    }

    private String fencedJson(String json) {
        return "```json\n" + json + "\n```";
    }

    private String textWrappedJson(String json) {
        return "분석 결과입니다.\n" + json + "\n검토해주세요.";
    }
}
