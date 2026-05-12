package com.back.coach.external.llm;

/**
 * LLM 호출의 단일 진입점. 도메인 서비스는 이 인터페이스에만 의존함.
 *
 * <p>이 인터페이스를 만들어 놓으면:
 * <ul>
 *   <li>실제 SDK 교체/추가 시 도메인 코드 영향 없음</li>
 *   <li>Resilience4j (@Retry, @CircuitBreaker, @TimeLimiter) 적용 지점</li>
 *   <li>테스트에서 Mockito 로 손쉽게 대체</li>
 * </ul>
 */
public interface LlmClient {

    /**
     * user role 단일 메시지로 동기 completion 호출. (legacy)
     *
     * @throws com.back.coach.global.exception.ServiceException
     *         LLM 호출 실패 시 (timeout, rate limit, schema 위반 등)
     */
    String complete(String prompt);

    /**
     * system role + user role을 분리해서 동기 completion 호출.
     *
     * <p>GLM-4.5 시리즈 reasoning 모델에서 system role 분리는 chain-of-thought 길이를 크게 줄여
     * 평균 -64% latency 효과 관찰됨 (2026-05-12 A/B/C 측정, docs/32 § 3).
     *
     * <p>기본 구현은 system+user를 결합해 단일 user 메시지로 보냄. 실제 message-list 분리를
     * 지원하는 클라이언트는 이 메서드를 override.
     *
     * @throws com.back.coach.global.exception.ServiceException
     *         LLM 호출 실패 시 (timeout, rate limit, schema 위반 등)
     */
    default String complete(String systemPrompt, String userPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return complete(userPrompt);
        }
        return complete(systemPrompt + "\n\n" + userPrompt);
    }
}
