package com.back.coach.external.llm;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * GLM (Zhipu AI) SDK wrapper.
 *
 * 책임:
 *  - SDK 호출
 *  - SDK 예외를 도메인 ErrorCode 로 매핑
 *  - 호출 지점에서 발생할 수 있는 timeout / rate limit / 형식 오류를 분류
 *
 * 미뤄둔 작업:
 *  - TODO(resilience): 첫 사용 사례가 들어오면 메서드에
 *    @Retry(name="llm") @CircuitBreaker(name="llm") @TimeLimiter(name="llm") 를 적용한다.
 *    이때 application.yml 에 resilience4j.* 설정을 추가하고 build.gradle.kts 에
 *    io.github.resilience4j:resilience4j-spring-boot3 의존성을 추가한다.
 *  - TODO(observability): Micrometer (이미 classpath 에 있음) 로
 *    호출 횟수 / 지연 / 실패 분류를 메트릭으로 노출한다.
 *  - TODO(security): prompt 본문은 사용자 이력서/PII 를 포함할 수 있으므로
 *    로그에 절대 그대로 남기지 않는다. 길이/해시/요약만 남기는 헬퍼를 도입한다.
 *  - TODO(integration): 실제 ai.z.openapi.ZaiClient 호출은 첫 사용 사례에서 작성한다.
 *    지금은 시그니처 / 예외 매핑 골격만 있음.
 */
@Component
public class GlmLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GlmLlmClient.class);

    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public GlmLlmClient(
            @Value("${ai.glm.api-key:}") String apiKey,
            @Value("${ai.glm.base-url:https://open.bigmodel.cn/api/paas/v4}") String baseUrl,
            @Value("${ai.glm.model:glm-4-flash}") String model
    ) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public String complete(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "prompt is empty");
        }
        try {
            // TODO(integration): 이 stub을 apiKey, baseUrl, model을 진짜로 넣어서
            //  제대로 된 ai.z.openapi.ZaiClient call로 교체
            throw new UnsupportedOperationException("GLM call not wired yet");
        } catch (UnsupportedOperationException e) {
            // 골격 단계에서 호출되면 명시적으로 분석 실패로 변환한다.
            // TODO(integration): SDK 연결 시 이 catch 는 제거하고
            //                    실제 SDK 예외(timeout / rate limit / parse) 별 catch 로 교체한다.
            log.warn("LLM call not yet implemented (model={}, promptLen={})", model, prompt.length());
            throw new ServiceException(ErrorCode.ANALYSIS_FAILED, "LLM 호출이 아직 구현되지 않았습니다.");
        } catch (RuntimeException e) {
            ErrorCode mapped = classify(e);
            log.warn("LLM call failed (model={}, code={}, type={})", model, mapped, e.getClass().getSimpleName());
            throw new ServiceException(mapped, mapped.getDefaultMessage());
        }
    }

    /**
     * SDK 예외를 ErrorCode 로 매핑. 실제 SDK 예외 타입을 알게 되면 instanceof 로 분기한다.
     * 지금은 메시지 휴리스틱만 둔다 — 첫 사용 사례에서 정밀화한다.
     */
    private ErrorCode classify(RuntimeException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (msg.contains("timeout") || msg.contains("timed out")) return ErrorCode.LLM_TIMEOUT;
        if (msg.contains("rate") && msg.contains("limit")) return ErrorCode.LLM_RATE_LIMITED;
        if (msg.contains("invalid") || msg.contains("schema") || msg.contains("parse")) return ErrorCode.LLM_INVALID_RESPONSE;
        return ErrorCode.ANALYSIS_FAILED;
    }
}
