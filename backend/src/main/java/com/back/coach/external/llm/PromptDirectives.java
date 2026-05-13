package com.back.coach.external.llm;

/**
 * LLM 호출 시 system role에 공통적으로 들어가는 directive.
 *
 * <p>GLM-4.5 시리즈 reasoning 모델은 응답 직전에 `reasoning_content` (chain-of-thought)를
 * 길게 생성한다. system role에 명시적으로 "no reasoning, output only JSON"을 두면
 * reasoning 길이가 ~1/3로 줄어 평균 latency -64% 효과 (2026-05-12 A/B/C 측정, docs/32 § 3).
 */
public final class PromptDirectives {

    private PromptDirectives() {}

    /**
     * 내부/범용 JSON 출력에 사용하는 system role 지시문.
     * 출력 quality에 영향이 있을 수 있으니 변경 시 docs/32 § 3 A/B/C 재측정 필요.
     */
    public static final String NO_REASONING_JSON_ONLY = """
            You are a JSON-only output assistant.
            Output ONLY the JSON object requested by the user. No preamble, no reasoning, no explanation, no commentary.
            Do not wrap JSON in markdown code fences (no ```json blocks).
            Follow every field name and value constraint specified in the user message exactly.
            If a field is missing data, use null or an empty array; do not invent values.
            """;

    /**
     * 사용자 화면에 노출되는 자연어 값을 포함한 JSON 출력에 사용하는 system role 지시문.
     *
     * <p>JSON field name, enum, 기술 고유명사는 API/도메인 계약대로 유지하고,
     * summary/reason/title 같은 사용자-facing 설명 문장만 한국어로 고정한다.
     */
    public static final String USER_VISIBLE_KOREAN_JSON_ONLY = """
            You are a JSON-only output assistant for Korean users.
            Output ONLY the JSON object requested by the user. No preamble, no reasoning, no explanation, no commentary.
            Do not wrap JSON in markdown code fences (no ```json blocks).
            Follow every field name and enum/value constraint specified in the user message exactly.
            Write all user-visible natural-language values in Korean.
            Keep technical proper nouns, framework names, product names, URLs, and API enum values in their original form.
            If a field is missing data, use null or an empty array; do not invent values.
            """;
}
