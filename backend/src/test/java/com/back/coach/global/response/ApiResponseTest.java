package com.back.coach.global.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카나리: docs/03_api_spec_aligned.md 의 성공 응답 스키마를 어기지 않도록 한다.
 *   { "data": ..., "meta": { ... } }
 */
class ApiResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void success_defaultMeta_serializesDataAndEmptyMetaOnly() {
        ApiResponse<Map<String, String>> body = ApiResponse.success(Map.of("message", "ok"));

        JsonNode json = mapper.valueToTree(body);

        assertThat(fieldNames(json)).containsExactlyInAnyOrder("data", "meta");
        assertThat(json.has("success")).isFalse();
        assertThat(json.get("data").get("message").asText()).isEqualTo("ok");
        assertThat(json.get("meta").isObject()).isTrue();
        assertThat(json.get("meta").isEmpty()).isTrue();
    }

    @Test
    void success_customMeta_preservesMetaValues() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("latestVersion", 3);
        meta.put("traceId", "trace-123");

        ApiResponse<Map<String, String>> body = ApiResponse.success(Map.of("message", "ok"), meta);

        JsonNode json = mapper.valueToTree(body);

        assertThat(json.get("meta").get("latestVersion").asInt()).isEqualTo(3);
        assertThat(json.get("meta").get("traceId").asText()).isEqualTo("trace-123");
    }

    @Test
    void constructor_nullMeta_normalizesToEmptyObject() {
        ApiResponse<String> body = new ApiResponse<>("ok", null);

        JsonNode json = mapper.valueToTree(body);

        assertThat(json.get("meta").isObject()).isTrue();
        assertThat(json.get("meta").isEmpty()).isTrue();
    }

    private static java.util.List<String> fieldNames(JsonNode node) {
        java.util.List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
