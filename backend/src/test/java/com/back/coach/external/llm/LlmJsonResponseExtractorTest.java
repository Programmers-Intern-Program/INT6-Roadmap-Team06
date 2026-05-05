package com.back.coach.external.llm;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmJsonResponseExtractorTest {

    @Test
    void extractJson_whenPureObject_returnsObject() {
        String response = "{\"summary\":\"ok\"}";

        assertThat(LlmJsonResponseExtractor.extractJson(response))
                .isEqualTo("{\"summary\":\"ok\"}");
    }

    @Test
    void extractJson_whenResponseHasWhitespace_returnsTrimmedJson() {
        String response = "  \n {\"summary\":\"ok\"} \n ";

        assertThat(LlmJsonResponseExtractor.extractJson(response))
                .isEqualTo("{\"summary\":\"ok\"}");
    }

    @Test
    void extractJson_whenResponseIsJsonFence_returnsObjectInsideFence() {
        String response = """
                ```json
                {"summary":"ok"}
                ```
                """;

        assertThat(LlmJsonResponseExtractor.extractJson(response))
                .isEqualTo("{\"summary\":\"ok\"}");
    }

    @Test
    void extractJson_whenResponseIsPlainFence_returnsObjectInsideFence() {
        String response = """
                ```
                {"summary":"ok"}
                ```
                """;

        assertThat(LlmJsonResponseExtractor.extractJson(response))
                .isEqualTo("{\"summary\":\"ok\"}");
    }

    @Test
    void extractJson_whenResponseHasTextAroundJson_returnsJsonOnly() {
        String response = """
                Here is the result:
                {"summary":"ok","reason":"uses {cache} safely"}
                Done.
                """;

        assertThat(LlmJsonResponseExtractor.extractJson(response))
                .isEqualTo("{\"summary\":\"ok\",\"reason\":\"uses {cache} safely\"}");
    }

    @Test
    void extractJson_whenResponseHasArray_returnsArray() {
        String response = "result: [{\"name\":\"Redis\"}]";

        assertThat(LlmJsonResponseExtractor.extractJson(response))
                .isEqualTo("[{\"name\":\"Redis\"}]");
    }

    @Test
    void extractJson_whenResponseIsBlank_throwsLlmInvalidResponse() {
        assertInvalidResponse("   ");
    }

    @Test
    void extractJson_whenResponseHasNoJson_throwsLlmInvalidResponse() {
        assertInvalidResponse("JSON 생성에 실패했습니다.");
    }

    @Test
    void extractJson_whenResponseHasIncompleteJson_throwsLlmInvalidResponse() {
        assertInvalidResponse("{\"summary\":\"ok\"");
    }

    private void assertInvalidResponse(String response) {
        assertThatThrownBy(() -> LlmJsonResponseExtractor.extractJson(response))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_INVALID_RESPONSE);
    }
}
