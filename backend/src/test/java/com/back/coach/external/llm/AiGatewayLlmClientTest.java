package com.back.coach.external.llm;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiGatewayLlmClientTest {

    private WireMockServer wireMock;
    private AiGatewayLlmClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        client = new AiGatewayLlmClient(
                new AiGatewayProperties("dummy-key", "http://127.0.0.1:" + wireMock.port(), "test-model")
        );
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

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
    void complete_callsGatewayAndReturnsText() {
        wireMock.stubFor(post("/v1/completions")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"text\":\"ok\"}")));

        String result = client.complete("hello");

        assertThat(result).isEqualTo("ok");
        wireMock.verify(postRequestedFor(urlEqualTo("/v1/completions"))
                .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.equalTo("Bearer dummy-key"))
                .withRequestBody(equalToJson("""
                        {
                          "model": "test-model",
                          "prompt": "hello"
                        }
                        """)));
    }

    @Test
    void rateLimitedResponse_throwsRateLimitedError() {
        wireMock.stubFor(post("/v1/completions")
                .willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> client.complete("hello"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.LLM_RATE_LIMITED);
    }

    @Test
    void gatewayTimeoutResponse_throwsTimeoutError() {
        wireMock.stubFor(post("/v1/completions")
                .willReturn(aResponse().withStatus(504)));

        assertThatThrownBy(() -> client.complete("hello"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.LLM_TIMEOUT);
    }

    @Test
    void blankTextResponse_throwsInvalidResponseError() {
        wireMock.stubFor(post("/v1/completions")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"text\":\"\"}")));

        assertThatThrownBy(() -> client.complete("hello"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.LLM_INVALID_RESPONSE);
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
