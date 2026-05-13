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
     * 호출 site별 cap을 명시하지 않을 때 적용되는 기본값.
     * 5개 PromptBuilder(분석/진단/로드맵) 호출 경로는 이 값을 사용.
     */
    int DEFAULT_MAX_TOKENS = 16384;

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

    /**
     * system role + user role 분리 + 호출 site별 `max_tokens` 명시.
     *
     * <p>Coach 채팅처럼 reasoning loop 상한을 죄어 latency 분산을 줄이고 싶은 경로에서 사용.
     * 분석/진단/로드맵 등 출력 크기가 큰 경로는 2-arg 오버로드로 기본 cap을 그대로 쓰면 됨.
     *
     * <p>기본 구현은 maxTokens를 무시하고 2-arg 호출로 위임 — 실제 HTTP 파라미터로 전달하려면
     * 구현체에서 override 해야 함.
     */
    default String complete(String systemPrompt, String userPrompt, int maxTokens) {
        return complete(systemPrompt, userPrompt);
    }
}
