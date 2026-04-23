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
     * 단일 프롬프트로 동기 completion 호출.
     *
     * @throws com.back.coach.global.exception.ServiceException
     *         LLM 호출 실패 시 (timeout, rate limit, schema 위반 등)
     */
    String complete(String prompt);
}
