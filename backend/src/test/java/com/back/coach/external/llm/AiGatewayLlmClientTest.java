package com.back.coach.external.llm;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiGatewayLlmClientTest {

    private final AiGatewayLlmClient client = new AiGatewayLlmClient(
            new AiGatewayProperties("dummy", "http://localhost:0", "test-model")
    );

    @Test
    void blankPrompt_throwsInvalidInput() {
        assertThatThrownBy(() -> client.complete(""))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThatThrownBy(() -> client.complete(null))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void unwiredGatewayCall_throwsAnalysisFailed() {
        assertThatThrownBy(() -> client.complete("hello"))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> {
                    ServiceException se = (ServiceException) e;
                    assertThat(se.getErrorCode()).isEqualTo(ErrorCode.ANALYSIS_FAILED);
                    assertThat(se.getMessage()).isEqualTo("AI Gateway 호출이 아직 구현되지 않았습니다.");
                });
    }

    @Test
    void properties_acceptsBlankApiKeyForLocalSkeleton() {
        AiGatewayProperties properties = new AiGatewayProperties(null, "http://localhost:0", "test-model");

        assertThat(properties.apiKey()).isEmpty();
        assertThat(properties.baseUrl()).isEqualTo("http://localhost:0");
        assertThat(properties.model()).isEqualTo("test-model");
    }

    @Test
    void properties_rejectsBlankBaseUrlOrModel() {
        assertThatThrownBy(() -> new AiGatewayProperties("key", "", "test-model"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ai.gateway.base-url must not be blank");

        assertThatThrownBy(() -> new AiGatewayProperties("key", "http://localhost:0", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ai.gateway.model must not be blank");
    }
}
