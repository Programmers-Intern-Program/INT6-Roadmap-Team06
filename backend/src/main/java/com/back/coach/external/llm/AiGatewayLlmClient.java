package com.back.coach.external.llm;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

// Grepp AI Gateway — OpenAI-compatible chat completions endpoint.
// POST {base-url}/v1/chat/completions
// request:  { "model": "...", "messages": [{"role": "user", "content": "..."}] }
// response: { "choices": [{ "message": { "content": "..." } }] }
@Component
public class AiGatewayLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayLlmClient.class);

    private final AiGatewayProperties properties;
    private final RestClient restClient;

    @Autowired
    public AiGatewayLlmClient(AiGatewayProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.timeout().connect().toMillis());
        factory.setReadTimeout((int) properties.timeout().read().toMillis());
        this.restClient = restClientBuilder
                .requestFactory(factory)
                .baseUrl(properties.baseUrl())
                .build();
    }

    AiGatewayLlmClient(AiGatewayProperties properties) {
        this(properties, RestClient.builder());
    }

    @Override
    public String complete(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "prompt is empty");
        }
        return callApi(java.util.List.of(new Message("user", prompt)), prompt.getBytes().length, DEFAULT_MAX_TOKENS);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, userPrompt, DEFAULT_MAX_TOKENS);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, int maxTokens) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "user prompt is empty");
        }
        if (maxTokens <= 0) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "maxTokens must be positive");
        }
        java.util.List<Message> messages;
        int promptBytes;
        if (systemPrompt == null || systemPrompt.isBlank()) {
            messages = java.util.List.of(new Message("user", userPrompt));
            promptBytes = userPrompt.getBytes().length;
        } else {
            messages = java.util.List.of(
                    new Message("system", systemPrompt),
                    new Message("user", userPrompt)
            );
            promptBytes = systemPrompt.getBytes().length + userPrompt.getBytes().length;
        }
        return callApi(messages, promptBytes, maxTokens);
    }

    // LLM은 stochastic → 같은 prompt에도 일회성으로 헛소리/빈 응답이 나올 수 있다.
    // 에러코드 중 화이트리스트에 한해서만 재시도. 그 외 (rate limit / timeout / invalid input) 는 즉시 throw.
    private static final java.util.Set<ErrorCode> RETRYABLE = java.util.Set.of(
            ErrorCode.ANALYSIS_FAILED,
            ErrorCode.LLM_INVALID_RESPONSE
    );

    private String callApi(java.util.List<Message> messages, int promptBytes, int maxTokens) {
        int maxAttempts = Math.max(1, properties.retry().maxAttempts());
        long backoffMs = properties.retry().backoff().toMillis();
        ServiceException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return callApiOnce(messages, promptBytes, maxTokens);
            } catch (ServiceException e) {
                last = e;
                if (attempt < maxAttempts && RETRYABLE.contains(e.getErrorCode())) {
                    log.warn("LLM retry scheduled: attempt={}/{}, code={}, backoffMs={}",
                            attempt, maxAttempts, e.getErrorCode(), backoffMs);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                    continue;
                }
                throw e;
            }
        }
        throw last; // 도달 불가 — 루프는 return 또는 throw 로만 빠져나감.
    }

    private String callApiOnce(java.util.List<Message> messages, int promptBytes, int maxTokens) {
        long startNs = System.nanoTime();
        log.debug("LLM request starting: model={}, promptBytes={}, messages={}, maxTokens={}",
                properties.model(), promptBytes, messages.size(), maxTokens);
        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> setAuthorization(headers, properties.apiKey()))
                    .body(new ChatCompletionRequest(
                            properties.model(),
                            messages,
                            maxTokens,
                            false
                    ))
                    .retrieve()
                    .onStatus(status -> status.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
                            (request, responseEntity) -> {
                                throw new ServiceException(ErrorCode.LLM_RATE_LIMITED);
                            })
                    .onStatus(status -> status.value() == HttpStatus.GATEWAY_TIMEOUT.value(),
                            (request, responseEntity) -> {
                                throw new ServiceException(ErrorCode.LLM_TIMEOUT);
                            })
                    .onStatus(status -> status.isError(),
                            (request, responseEntity) -> {
                                throw new ServiceException(ErrorCode.ANALYSIS_FAILED);
                            })
                    .body(ChatCompletionResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
            }
            String content = response.choices().get(0).message().content();
            if (content == null || content.isBlank()) {
                throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
            }
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            int responseBytes = content.getBytes().length;
            log.debug("LLM request completed: model={}, elapsedMs={}, responseBytes={}",
                    properties.model(), elapsedMs, responseBytes);
            return content;
        } catch (ServiceException e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            log.warn("LLM request failed: model={}, code={}, elapsedMs={}",
                    properties.model(), e.getErrorCode(), elapsedMs);
            throw e;
        } catch (RuntimeException e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            ErrorCode mapped = classify(e);
            log.warn("AI Gateway call failed: model={}, code={}, type={}, elapsedMs={}, message={}",
                    properties.model(), mapped, e.getClass().getSimpleName(), elapsedMs, e.getMessage());
            throw new ServiceException(mapped, mapped.getDefaultMessage());
        }
    }

    private void setAuthorization(HttpHeaders headers, String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }
    }

    private ErrorCode classify(RuntimeException e) {
        if (e instanceof ResourceAccessException) return ErrorCode.LLM_TIMEOUT;
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (msg.contains("timeout") || msg.contains("timed out")) return ErrorCode.LLM_TIMEOUT;
        if (msg.contains("rate") && msg.contains("limit")) return ErrorCode.LLM_RATE_LIMITED;
        if (msg.contains("invalid") || msg.contains("schema") || msg.contains("parse")) return ErrorCode.LLM_INVALID_RESPONSE;
        return ErrorCode.ANALYSIS_FAILED;
    }

    private record Message(String role, String content) {}

    private record ChatCompletionRequest(String model, java.util.List<Message> messages,
                                         @com.fasterxml.jackson.annotation.JsonProperty("max_tokens") int maxTokens,
                                         boolean stream) {}

    private record ChatCompletionResponse(java.util.List<Choice> choices) {}

    private record Choice(Message message) {}
}
