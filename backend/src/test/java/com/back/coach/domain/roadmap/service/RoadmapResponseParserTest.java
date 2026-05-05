package com.back.coach.domain.roadmap.service;

import com.back.coach.global.code.MaterialType;
import com.back.coach.global.code.RoadmapTaskType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoadmapResponseParserTest {

    private final RoadmapResponseParser parser = new RoadmapResponseParser();

    @Test
    void parse_whenSchemaIsValid_returnsRoadmapResult() {
        RoadmapResponseParser.RoadmapResult result = parser.parse(validResponse());

        assertThat(result.summary()).isEqualTo("Redis 중심 로드맵");
        assertThat(result.weeks()).hasSize(2);
        assertThat(result.weeks().get(0).weekNumber()).isEqualTo(1);
        assertThat(result.weeks().get(0).tasks().get(0).type()).isEqualTo(RoadmapTaskType.READ_DOCS);
        assertThat(result.weeks().get(0).materials().get(0).type()).isEqualTo(MaterialType.DOCS);
    }

    @Test
    void parse_whenResponseIsFencedJson_returnsRoadmapResult() {
        RoadmapResponseParser.RoadmapResult result = parser.parse(fencedJson(validResponse()));

        assertThat(result.summary()).isEqualTo("Redis 중심 로드맵");
        assertThat(result.weeks()).hasSize(2);
    }

    @Test
    void parse_whenTaskTypeIsInvalid_throwsLlmInvalidResponse() {
        String invalidResponse = validResponse().replace("READ_DOCS", "UNKNOWN_TASK");

        assertInvalidResponse(invalidResponse);
    }

    @Test
    void parse_whenRequiredFieldIsMissing_throwsLlmInvalidResponse() {
        String invalidResponse = """
                {
                  "summary": "잘못된 로드맵",
                  "weeks": [
                    {
                      "weekNumber": 1,
                      "topic": "Redis 기초",
                      "tasks": [{"type": "READ_DOCS", "title": "Redis 문서 읽기"}],
                      "materials": [],
                      "estimatedHours": 8.0
                    }
                  ]
                }
                """;

        assertInvalidResponse(invalidResponse);
    }

    @Test
    void parse_whenWeekNumberIsDuplicated_throwsLlmInvalidResponse() {
        String invalidResponse = validResponse().replace("\"weekNumber\": 2", "\"weekNumber\": 1");

        assertInvalidResponse(invalidResponse);
    }

    private void assertInvalidResponse(String invalidResponse) {
        assertThatThrownBy(() -> parser.parse(invalidResponse))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LLM_INVALID_RESPONSE);
    }

    private String validResponse() {
        return """
                {
                  "summary": "Redis 중심 로드맵",
                  "weeks": [
                    {
                      "weekNumber": 1,
                      "topic": "Redis 기초",
                      "reason": "캐시 설계 역량을 먼저 보완해야 함",
                      "tasks": [
                        {
                          "type": "READ_DOCS",
                          "title": "Redis 공식 문서 읽기"
                        }
                      ],
                      "materials": [
                        {
                          "type": "DOCS",
                          "title": "Redis Documentation",
                          "url": "https://redis.io/docs"
                        }
                      ],
                      "estimatedHours": 8.0
                    },
                    {
                      "weekNumber": 2,
                      "topic": "Redis 적용",
                      "reason": "실제 프로젝트 적용 경험이 필요함",
                      "tasks": [
                        {
                          "type": "BUILD_EXAMPLE",
                          "title": "캐시 예제 구현"
                        }
                      ],
                      "materials": [],
                      "estimatedHours": 6.0
                    }
                  ]
                }
                """;
    }

    private String fencedJson(String json) {
        return "```json\n" + json + "\n```";
    }
}
