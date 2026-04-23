package com.back.coach.global.response;

import com.back.coach.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카나리: docs/03_api_spec_aligned.md 의 에러 응답 스키마를 어기지 않도록 한다.
 *   { "code": "...", "message": "...", "details": { ... } }
 */
class ApiErrorResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void schemaShape_simple() throws Exception {
        ApiErrorResponse body = ApiErrorResponse.of(ErrorCode.INVALID_INPUT, "bad request");

        JsonNode json = mapper.valueToTree(body);

        assertThat(fieldNames(json)).containsExactlyInAnyOrder("code", "message");
        assertThat(json.get("code").asText()).isEqualTo("INVALID_INPUT");
        assertThat(json.get("message").asText()).isEqualTo("bad request");
        assertThat(json.has("success")).isFalse();
        assertThat(json.has("fieldErrors")).isFalse();
    }

    @Test
    void schemaShape_withFieldErrors_nestsUnderDetails() throws Exception {
        ApiErrorResponse body = ApiErrorResponse.withFieldErrors(
                ErrorCode.INVALID_INPUT,
                "validation failed",
                List.of(new FieldErrorDetail("email", "must not be blank"))
        );

        JsonNode json = mapper.valueToTree(body);

        assertThat(fieldNames(json)).containsExactlyInAnyOrder("code", "message", "details");
        JsonNode details = json.get("details");
        assertThat(details.has("fieldErrors")).isTrue();
        assertThat(details.get("fieldErrors").isArray()).isTrue();
        assertThat(details.get("fieldErrors").get(0).get("field").asText()).isEqualTo("email");
        assertThat(details.get("fieldErrors").get(0).get("reason").asText()).isEqualTo("must not be blank");
    }

    @Test
    void withTraceId_addsTraceIdUnderDetails_preservesExistingDetails() throws Exception {
        ApiErrorResponse base = ApiErrorResponse.withFieldErrors(
                ErrorCode.INVALID_INPUT,
                "x",
                List.of(new FieldErrorDetail("a", "b"))
        );

        ApiErrorResponse withTrace = base.withTraceId("trace-123");
        JsonNode json = mapper.valueToTree(withTrace);

        assertThat(json.get("details").get("traceId").asText()).isEqualTo("trace-123");
        assertThat(json.get("details").has("fieldErrors")).isTrue();
    }

    @Test
    void withTraceId_nullOrBlank_returnsUnchanged() {
        ApiErrorResponse base = ApiErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, "x");
        assertThat(base.withTraceId(null)).isSameAs(base);
        assertThat(base.withTraceId("  ")).isSameAs(base);
    }

    private static java.util.List<String> fieldNames(JsonNode node) {
        java.util.List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
