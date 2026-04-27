package com.back.coach.external.llm;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Team AI Gateway client boundary.
 *
 * <p>Domain services depend on {@link LlmClient} only. The actual Gateway HTTP
 * contract is intentionally not implemented until the team endpoint, request
 * body, and response body are fixed.
 */
@Component
public class AiGatewayLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayLlmClient.class);

    private final AiGatewayProperties properties;

    public AiGatewayLlmClient(AiGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public String complete(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "prompt is empty");
        }
        try {
            // TODO(integration): Wire this to the team AI Gateway once its HTTP contract is fixed.
            throw new UnsupportedOperationException("AI Gateway call not wired yet");
        } catch (UnsupportedOperationException e) {
            log.warn("AI Gateway call not yet implemented (model={}, promptLen={})", properties.model(), prompt.length());
            throw new ServiceException(ErrorCode.ANALYSIS_FAILED, "AI Gateway 호출이 아직 구현되지 않았습니다.");
        } catch (RuntimeException e) {
            ErrorCode mapped = classify(e);
            log.warn("AI Gateway call failed (model={}, code={}, type={})",
                    properties.model(), mapped, e.getClass().getSimpleName());
            throw new ServiceException(mapped, mapped.getDefaultMessage());
        }
    }

    private ErrorCode classify(RuntimeException e) {
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
}
