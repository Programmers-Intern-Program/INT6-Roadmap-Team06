package com.back.coach.external.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "ai.gateway")
public record AiGatewayProperties(
        String apiKey,
        String baseUrl,
        String model,
        Timeout timeout,
        Retry retry
) {

    public record Timeout(
            java.time.Duration connect,
            java.time.Duration read
    ) {
        public Timeout {
            if (connect == null) connect = java.time.Duration.ofSeconds(5);
            if (read == null) read = java.time.Duration.ofSeconds(120);
        }
    }

    /**
     * 호출 실패 시 재시도 정책.
     *
     * <p>화이트리스트(ANALYSIS_FAILED / LLM_INVALID_RESPONSE) 에 한해 적용. 자세한 정책은
     * {@link AiGatewayLlmClient} 참고.
     *
     * <p>기본값은 maxAttempts=1 (재시도 0회) — 옵트인 방식.
     */
    public record Retry(
            int maxAttempts,
            java.time.Duration backoff
    ) {
        public Retry {
            if (maxAttempts < 1) maxAttempts = 1;
            if (backoff == null || backoff.isNegative()) backoff = java.time.Duration.ofMillis(200);
        }
    }

    /** 기존 호출부 호환용 — retry 미지정시 기본값(1회 시도, retry 없음) 적용. */
    public AiGatewayProperties(String apiKey, String baseUrl, String model, Timeout timeout) {
        this(apiKey, baseUrl, model, timeout, null);
    }

    @ConstructorBinding
    public AiGatewayProperties {
        apiKey = apiKey == null ? "" : apiKey;
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("ai.gateway.base-url must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("ai.gateway.model must not be blank");
        }
        if (timeout == null) timeout = new Timeout(null, null);
        if (retry == null) retry = new Retry(1, null);
    }
}
