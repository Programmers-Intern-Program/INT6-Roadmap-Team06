package com.back.coach.external.llm;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

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
                new AiGatewayProperties("dummy-key", "http://127.0.0.1:" + wireMock.port(), "test-model", null)
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
        wireMock.stubFor(post("/v1/chat/completions")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}")));

        String result = client.complete("hello");

        assertThat(result).isEqualTo("ok");
        wireMock.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.equalTo("Bearer dummy-key"))
                .withRequestBody(equalToJson("""
                        {
                          "model": "test-model",
                          "messages": [{"role": "user", "content": "hello"}],
                          "max_tokens": 16384,
                          "stream": false
                        }
                        """)));
    }

    @Test
    void rateLimitedResponse_throwsRateLimitedError() {
        wireMock.stubFor(post("/v1/chat/completions")
                .willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> client.complete("hello"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.LLM_RATE_LIMITED);
    }

    @Test
    void gatewayTimeoutResponse_throwsTimeoutError() {
        wireMock.stubFor(post("/v1/chat/completions")
                .willReturn(aResponse().withStatus(504)));

        assertThatThrownBy(() -> client.complete("hello"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.LLM_TIMEOUT);
    }

    @Test
    void blankTextResponse_throwsInvalidResponseError() {
        wireMock.stubFor(post("/v1/chat/completions")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"\"}}]}")));

        assertThatThrownBy(() -> client.complete("hello"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.LLM_INVALID_RESPONSE);
    }

    @Test
    void properties_acceptsBlankApiKeyForLocalSkeleton() {
        AiGatewayProperties properties = new AiGatewayProperties(null, "http://localhost:0", "test-model", null);

        assertThat(properties.apiKey()).isEmpty();
        assertThat(properties.baseUrl()).isEqualTo("http://localhost:0");
        assertThat(properties.model()).isEqualTo("test-model");
    }

    // ---------- Retry whitelist ----------

    private AiGatewayLlmClient clientWithRetry(int maxAttempts) {
        return new AiGatewayLlmClient(new AiGatewayProperties(
                "dummy-key",
                "http://127.0.0.1:" + wireMock.port(),
                "test-model",
                null,
                new AiGatewayProperties.Retry(maxAttempts, Duration.ofMillis(10))
        ));
    }

    private static final String OK_BODY = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}";
    private static final String EMPTY_CONTENT_BODY = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"\"}}]}";

    @Test
    void retry_recoversAfter500() {
        wireMock.stubFor(post("/v1/chat/completions")
                .inScenario("retry-500")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("recovered"));
        wireMock.stubFor(post("/v1/chat/completions")
                .inScenario("retry-500")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(OK_BODY)));

        String result = clientWithRetry(2).complete("hello");

        assertThat(result).isEqualTo("ok");
        wireMock.verify(2, postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }

    @Test
    void retry_recoversAfterEmptyContent() {
        wireMock.stubFor(post("/v1/chat/completions")
                .inScenario("retry-empty")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(EMPTY_CONTENT_BODY))
                .willSetStateTo("recovered"));
        wireMock.stubFor(post("/v1/chat/completions")
                .inScenario("retry-empty")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(OK_BODY)));

        String result = clientWithRetry(2).complete("hello");

        assertThat(result).isEqualTo("ok");
        wireMock.verify(2, postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }

    @Test
    void retry_doesNotRetryOnRateLimit() {
        wireMock.stubFor(post("/v1/chat/completions")
                .willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> clientWithRetry(3).complete("hello"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.LLM_RATE_LIMITED);

        wireMock.verify(1, postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }

    @Test
    void retry_doesNotRetryOnGatewayTimeout() {
        wireMock.stubFor(post("/v1/chat/completions")
                .willReturn(aResponse().withStatus(504)));

        assertThatThrownBy(() -> clientWithRetry(3).complete("hello"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.LLM_TIMEOUT);

        wireMock.verify(1, postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }

    // ---------- Retry edge cases ----------

    @Test
    void retry_exhaustsAttemptsThenThrows() {
        wireMock.stubFor(post("/v1/chat/completions")
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> clientWithRetry(3).complete("hello"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_FAILED);

        wireMock.verify(3, postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }

    @Test
    void retry_recoversThroughMixedRetryableErrors() {
        // attempt 1: 500 (ANALYSIS_FAILED) → attempt 2: empty content (LLM_INVALID_RESPONSE) → attempt 3: ok
        wireMock.stubFor(post("/v1/chat/completions")
                .inScenario("mixed")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("after-500"));
        wireMock.stubFor(post("/v1/chat/completions")
                .inScenario("mixed")
                .whenScenarioStateIs("after-500")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(EMPTY_CONTENT_BODY))
                .willSetStateTo("after-empty"));
        wireMock.stubFor(post("/v1/chat/completions")
                .inScenario("mixed")
                .whenScenarioStateIs("after-empty")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(OK_BODY)));

        String result = clientWithRetry(3).complete("hello");

        assertThat(result).isEqualTo("ok");
        wireMock.verify(3, postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }

    @Test
    void retry_maxAttempts1_meansNoRetryEvenForRetryableCode() {
        wireMock.stubFor(post("/v1/chat/completions")
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> clientWithRetry(1).complete("hello"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_FAILED);

        wireMock.verify(1, postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }

    @Test
    void retry_invalidInputSkipsApiCallEntirely() {
        // 빈 prompt 는 retry 루프 진입 전에 INVALID_INPUT 으로 거부됨 → 게이트웨이 호출 0 회.
        assertThatThrownBy(() -> clientWithRetry(3).complete("  "))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        wireMock.verify(0, postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }

    @Test
    void retry_appliesBackoffBetweenAttempts() {
        // 3 attempts, 80ms backoff → 최소 2 * 80ms = 160ms 대기
        wireMock.stubFor(post("/v1/chat/completions")
                .willReturn(aResponse().withStatus(500)));

        AiGatewayLlmClient slow = new AiGatewayLlmClient(new AiGatewayProperties(
                "k", "http://127.0.0.1:" + wireMock.port(), "test-model", null,
                new AiGatewayProperties.Retry(3, Duration.ofMillis(80))
        ));

        long start = System.nanoTime();
        assertThatThrownBy(() -> slow.complete("hello"))
                .isInstanceOf(ServiceException.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(160);
        wireMock.verify(3, postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }

    @Test
    void retry_appliesToSystemUserOverload() {
        wireMock.stubFor(post("/v1/chat/completions")
                .inScenario("sys-retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("recovered"));
        wireMock.stubFor(post("/v1/chat/completions")
                .inScenario("sys-retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(OK_BODY)));

        String result = clientWithRetry(2).complete("system role here", "user role here");

        assertThat(result).isEqualTo("ok");
        wireMock.verify(2, postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }

    @Test
    void retryRecord_normalizesInvalidDefaults() {
        // maxAttempts < 1 → 1, null backoff → 200ms, negative backoff → 200ms
        AiGatewayProperties.Retry r1 = new AiGatewayProperties.Retry(0, null);
        assertThat(r1.maxAttempts()).isEqualTo(1);
        assertThat(r1.backoff()).isEqualTo(Duration.ofMillis(200));

        AiGatewayProperties.Retry r2 = new AiGatewayProperties.Retry(-5, Duration.ofMillis(-10));
        assertThat(r2.maxAttempts()).isEqualTo(1);
        assertThat(r2.backoff()).isEqualTo(Duration.ofMillis(200));

        AiGatewayProperties.Retry r3 = new AiGatewayProperties.Retry(3, Duration.ofMillis(500));
        assertThat(r3.maxAttempts()).isEqualTo(3);
        assertThat(r3.backoff()).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void properties_rejectsBlankBaseUrlOrModel() {
        assertThatThrownBy(() -> new AiGatewayProperties("key", "", "test-model", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ai.gateway.base-url must not be blank");

        assertThatThrownBy(() -> new AiGatewayProperties("key", "http://localhost:0", "", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ai.gateway.model must not be blank");
    }
}
