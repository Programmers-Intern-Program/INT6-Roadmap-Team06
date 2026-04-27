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

/**
 * Team AI Gateway client boundary.
 *
 * <p>Minimum HTTP contract:
 * <ul>
 *   <li>POST /v1/completions</li>
 *   <li>request: { "model": "...", "prompt": "..." }</li>
 *   <li>response: { "text": "..." }</li>
 * </ul>
 */
@Component
public class AiGatewayLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayLlmClient.class);

    private final AiGatewayProperties properties;
    private final RestClient restClient;

    @Autowired
    public AiGatewayLlmClient(AiGatewayProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .requestFactory(new SimpleClientHttpRequestFactory())
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
        try {
            CompletionResponse response = restClient.post()
                    .uri("/v1/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> setAuthorization(headers, properties.apiKey()))
                    .body(new CompletionRequest(properties.model(), prompt))
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
                    .body(CompletionResponse.class);

            if (response == null || response.text() == null || response.text().isBlank()) {
                throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
            }
            return response.text();
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            ErrorCode mapped = classify(e);
            log.warn("AI Gateway call failed (model={}, code={}, type={}, message={})",
                    properties.model(), mapped, e.getClass().getSimpleName(), e.getMessage());
            throw new ServiceException(mapped, mapped.getDefaultMessage());
        }
    }

    private void setAuthorization(HttpHeaders headers, String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }
    }

    private ErrorCode classify(RuntimeException e) {
        if (e instanceof ResourceAccessException) {
            return ErrorCode.LLM_TIMEOUT;
        }
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (msg.contains("timeout") || msg.contains("timed out")) {
            return ErrorCode.LLM_TIMEOUT;
        }
        if (msg.contains("rate") && msg.contains("limit")) {
            return ErrorCode.LLM_RATE_LIMITED;
        }
        if (msg.contains("invalid") || msg.contains("schema") || msg.contains("parse")) {
            return ErrorCode.LLM_INVALID_RESPONSE;
        }
        return ErrorCode.ANALYSIS_FAILED;
    }

    private record CompletionRequest(String model, String prompt) {
    }

    private record CompletionResponse(String text) {
    }
}
