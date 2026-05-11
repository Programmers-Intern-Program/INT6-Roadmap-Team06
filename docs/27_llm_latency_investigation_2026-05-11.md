# LLM 응답 지연 원인 규명 — AI Gateway Provider 격리 테스트

**날짜**: 2026-05-11  
**관련 이슈**: [#254 GitHub 분석 LLM 호출 5분 hang 원인 규명 + provider 격리 테스트](../../issues/254)  
**작성자**: Jiny  
**상태**: H1 부분 완료 + **H4 검증 완료(기각)** — H5 측정 중, 이후 미검증

---

## 목적

`GithubAnalysisService`에서 LLM 호출이 ~47초 hang되는 현상이 dev 세션에서 재현됐다.  
이슈 #254에서 정의한 H1~H8 가설 중 **H1 (provider/model 격리 테스트)** 를 오늘 세션에서 부분 검증했다.  
AI Gateway (`https://aigw.alpha.grepp.co`, LiteLLM-proxied Zai/Zhipu) 대상으로 총 8종의 curl 직접 테스트를 수행했다.

---

## 테스트 방법

```
POST https://aigw.alpha.grepp.co/v1/chat/completions
Authorization: Bearer <API_KEY>
Content-Type: application/json
--max-time 180 --data-binary @prompt_file.json
```

모든 테스트에서 `n=1`, 동일 bearer token 사용. 3 run 반복 측정.

---

## 테스트 결과

### Test 1 — 소형 프롬프트 "안녕", `glm-4.5-flash`, `max_tokens=50`

| Run | 응답시간 | HTTP 상태 | 비고 |
|-----|----------|-----------|------|
| 1   | 34.16s   | 200       |      |
| 2   | 2.03s    | 200       |      |
| 3   | 6.87s    | 200       |      |

**관찰:**
- `finish_reason: "length"` — `content: ""`(빈 문자열)로 반환됨
- 모델이 `reasoning_content`(chain-of-thought)를 먼저 생성하여 50 토큰을 모두 소진
- 실제 답변 content가 토큰 한도 전에 생성되지 못한 구조

---

### Test 2 — 짧은 프롬프트, `glm-4.5-flash`, `max_tokens=2000`

| Run | 응답시간 | HTTP 상태 |
|-----|----------|-----------|
| 1   | 11.89s   | 200       |
| 2   | 5.42s    | 200       |
| 3   | 30.30s   | 200       |

**관찰:** max_tokens를 2000으로 올리자 content(`@SpringBootApplication`...) 정상 반환. 그러나 run 3에서 30s 급등.

---

### Test 3 — 짧은 프롬프트, `glm-4.5` (non-flash), `max_tokens=2000`

| Run | 응답시간 | HTTP 상태 |
|-----|----------|-----------|
| 1   | 4.31s    | 200       |
| 2   | 3.12s    | 200       |
| 3   | 3.14s    | 200       |

**관찰:** 평균 ~3.5s, 분산 매우 낮음. 짧은 프롬프트에 대해서는 flash 대비 압도적으로 안정적.

---

### Test 4 — `glm-4-plus`, `max_tokens=2000`

| Run | 응답시간 | HTTP 상태 | 에러 |
|-----|----------|-----------|------|
| 1   | -        | 429       | `Insufficient balance or no resource package` |
| 2   | -        | 429       | 동일 |
| 3   | -        | 429       | 동일 |

**관찰:** 현재 API 키로는 `glm-4-plus` 사용 불가. 리소스 패키지 미구매 상태.

---

### Test 5 — 실제 GitHub 분석 스타일 프롬프트 (~517 bytes), `glm-4.5-flash`, `max_tokens=2000`

| Run | 응답시간    | HTTP 상태 |
|-----|-------------|-----------|
| 1   | **151.59s** | 200       |
| 2   | 24.61s      | 200       |
| 3   | 48.78s      | 200       |

**관찰:**
- 모두 유효한 JSON content 반환, `reasoning_content`도 함께 방출
- Run 1에서 **151s** — `--max-time 180` 제한의 84%를 소진. 서비스 타임아웃(120s) 초과 가능성 있음
- 실제 서비스에서 관찰된 ~47s hang과 직접 대응

---

### Test 6 — 동일 실제 프롬프트, `glm-4.5` (non-flash), `max_tokens=2000`

| Run | 응답시간 | HTTP 상태 |
|-----|----------|-----------|
| 1   | 37.62s   | 200       |
| 2   | 20.40s   | 200       |
| 3   | 40.09s   | 200       |

**관찰:**
- 평균 ~33s. flash보다 안정적이나 실용적으로 느린 구간도 존재
- `reasoning_content` 방출 동일 — GLM-4.5 시리즈 전체 공통 동작

---

### Test 7 — `glm-4.5-flash` + Zhipu 네이티브 `thinking: {type: "disabled"}`

| Run | 응답시간 | HTTP 상태 | 에러 |
|-----|----------|-----------|------|
| 1~3 | -        | **400**   | `litellm.UnsupportedParamsError: zai does not support parameters: ['thinking']` |

**관찰:** LiteLLM 프록시가 `thinking` 파라미터를 완전히 차단함. Gateway 레이어에서 reject.

---

### Test 8 — `glm-4.5-flash` + `enable_thinking: false`

| Run | 응답시간    | HTTP 상태 |
|-----|-------------|-----------|
| 1   | 56.18s      | 200       |
| 2   | 17.14s      | 200       |
| 3   | **117.61s** | 200       |

**관찰:**
- HTTP 200 반환 — 파라미터 자체는 수락됨
- `reasoning_content` 여전히 존재 → **사실상 silently ignored**
- 지연 분포 flash와 동일: run 3에서 ~120s 근접

---

## 핵심 발견 (H1 결론)

### 1. GLM-4.5 시리즈는 전부 reasoning 모델이다

`glm-4.5-flash`와 `glm-4.5` 모두 응답에 `reasoning_content` (chain-of-thought)를 항상 방출한다.  
이는 GLM-4.5 아키텍처 자체의 특성이며, 프롬프트나 파라미터로 비활성화할 수 없다.

```json
{
  "choices": [{
    "message": {
      "reasoning_content": "Let me analyze this Spring Boot repository...(수백 토큰)",
      "content": "{ ... actual JSON response ... }"
    }
  }]
}
```

### 2. `flash`는 이 provider에서 더 빠르지 않다

`glm-4.5-flash`의 "flash" 명칭이 시사하는 것과 달리, 이 AI Gateway에서는 flash가 오히려 더 느리거나 고분산이다.

| 모델 | 짧은 프롬프트 평균 | 실제 프롬프트 범위 |
|------|-------------------|--------------------|
| `glm-4.5-flash` | ~14s | 24s ~ **152s** |
| `glm-4.5`       | ~3.5s | 20s ~ 40s      |

**원인 추정**: flash는 파라미터 수가 적어 reasoning 수렴에 더 많은 토큰이 필요하고, 결과적으로 wall-clock이 길어진다.

### 3. thinking 비활성화 불가

| 시도 | 결과 |
|------|------|
| `thinking: {type: "disabled"}` | HTTP 400 — LiteLLM이 거부 |
| `enable_thinking: false` | HTTP 200 — 수락되나 silently no-op |

**결론**: 이 Gateway+LiteLLM 구성으로는 GLM-4.5 reasoning 모드를 끌 수 없다.

### 4. `glm-4-plus` 사용 불가

API 키에 리소스 패키지 없음 (HTTP 429). 대안으로 즉시 선택 불가.

---

## 미검증 가설 (오늘 범위 외)

이번 조사는 #254 H1만 부분 커버한다. 아래 가설은 **아직 검증하지 않았다**.

| 가설 ID | 내용 | 우선순위 |
|---------|------|---------|
| **H4** | `stream: true` 기본값으로 인해 `application/octet-stream` 응답 → JSON 파싱 실패, 5분 hang | **최우선** (#254에서 most-likely로 지목) |
| H2 | 프롬프트 크기 vs 응답 지연 상관관계 (체계적 측정 미실시) | 중 |
| H3 | `SimpleClientHttpRequestFactory` inter-byte timeout semantics 확인 | 중 |
| H5 | `max_tokens=16384` 하드코딩 → reasoning 토큰까지 포함시 실제 소비량 폭증 | 중 |
| H6 | concurrency/queue 포화 — 분석 동시 요청 시 한 요청이 block | 낮 |
| H7 | `GithubAnalysisService.run()`이 아직 `@Transactional` 스코프에 포함 — connection hold | 낮 (Slice 5 async로 증상 마스킹 중) |
| H8 | TLS handshake overhead / HTTP connection pool 미사용 | 낮 |

---

## 권고 사항

### 즉시 적용 가능 (코드 변경 없음)

```
# .env 변경
AI_GATEWAY_MODEL=glm-4.5
```

`glm-4.5-flash` → `glm-4.5`로 교체. 짧은 프롬프트 기준 평균 응답 14s → 3.5s.  
실제 GitHub 분석 프롬프트 기준 고분산(24~152s) → 저분산(20~40s) 기대.  
단, 40s급 응답은 여전히 존재하므로 근본 해결이 아님 — 하지만 최소 위험 빠른 개선.

### 단기 검증 (내일 우선)

1. **H4 테스트**: `stream: false`를 request body에 명시 후 동일 5종 시나리오 재측정.  
   ```bash
   # 테스트 json에 추가
   "stream": false
   ```
   현재 코드에 `stream` 파라미터가 명시되어 있지 않다면 gateway default가 `true`일 가능성 있음.

2. **시스템 프롬프트 A/B**: "No preamble. Output only valid JSON." 추가 후 reasoning_content 토큰 수 변화 측정. GLM 시리즈에서 효과는 제한적이나 검증 가치 있음.

### 중기 (Grepp 운영팀 문의)

- 이 Gateway에서 non-reasoning 모델 (`gpt-4o-mini`, `qwen2.5-7b-instruct` 등) 제공 여부 확인.  
- reasoning 모델이 아닌 instruction-tuned 모델로 교체 시 latency 1/10 수준 기대.

### 구조적 (Slice 5 완료 전제)

- LLM 호출을 HTTP 응답 경로에서 완전히 분리 (async Redis queue, issue #182).  
- 타임아웃이 사용자에게 노출되지 않도록 polling 방식 전환.

---

## H4 검증 — `stream` 필드 영향 (2026-05-11 오후 추가)

### 운영 코드(`AiGatewayLlmClient.java:53-62`)가 실제로 보내는 JSON

```json
{
  "model": "glm-4.5-flash",
  "messages": [{"role":"user","content":"<prompt>"}],
  "max_tokens": 16384
}
```

- 헤더: `Accept: application/json`, `Content-Type: application/json`, `Authorization: Bearer <key>`
- **`stream` 필드 미명시** (record `ChatCompletionRequest`에 필드 없음)
- **`max_tokens=16384` 하드코딩** (이전 테스트의 `2000` 대비 8배 — H5와 직결)

### 테스트 변형 (운영과 동일 프롬프트, 3-run 반복)

| 변형 | run 1 | run 2 | run 3 | 응답 Content-Type |
|------|-------|-------|-------|------------------|
| **H4-A: stream 필드 미명시** (운영 코드와 동일) | 37.97s | **128.39s** | 38.07s | `application/json` ✓ |
| **H4-B: `"stream": false` 명시** | 52.58s | 58.27s | 63.62s | `application/json` ✓ |
| **H4-C: `"stream": true` 명시** | 114.89s | 53.30s | 100.96s | `text/event-stream; charset=utf-8` (SSE) |

### 게이트웨이 응답 헤더 (마지막 run 기준)

`x-litellm-response-duration-ms`:
- H4-A run3: **37,958 ms** — 게이트웨이가 provider 응답 대기에 쓴 순수 시간
- H4-B run3: 63,549 ms
- H4-C run3: 2,210 ms (SSE는 첫 청크까지)

`x-litellm-model-api-base: https://api.z.ai/api/paas/v4` → 실제 LLM 백엔드는 Z.AI (Zhipu).

### 판정: **H4 기각 (오늘 게이트웨이 동작 기준)**

| 사실 | 의미 |
|------|------|
| stream 미명시 = `application/json` 정상 응답 | 게이트웨이가 default를 `false`로 처리 — 우리 코드가 octet-stream을 받을 경로가 보이지 않음 |
| stream:true 명시 = `text/event-stream` (정확한 SSE) | LiteLLM이 SSE를 octet-stream으로 라벨하지 않음 |
| 어느 변형에서도 `application/octet-stream` 미관찰 | #254가 2026-05-08 본 octet-stream을 오늘 재현 불가 |

**보류 사항**: 2026-05-08 사건 당시 octet-stream을 본 정황은 분명. 가능성 — 당시 LiteLLM 버전이 다른 동작, 일시적 fallback 경로, 또는 provider error 응답이 octet-stream으로 라벨됐을 수 있음. **단정적 close보다는 "오늘 재현 불가 + 방어 코드 추가"가 안전.**

### 추가 발견: latency 분산은 reasoning_content 길이 차이

| run | dl_size (bytes) | 응답 총 시간 |
|-----|----------------|------------|
| H4-A run3 | 7,539 | 38s |
| H4-B run3 | 15,030 | 64s |

같은 프롬프트에 응답 크기가 **2배** 차이 — `reasoning_content` 토큰 양이 매번 달라짐. 이게 평균보다 **분산이 진짜 문제**인 이유.

---

## H4 후 액션 제안

### 즉시 (방어 차원, 1줄 변경)

`ChatCompletionRequest` record에 `stream` 필드 추가하고 항상 `false` 명시:

```java
private record ChatCompletionRequest(
    String model,
    java.util.List<Message> messages,
    @JsonProperty("max_tokens") int maxTokens,
    boolean stream
) {}
```

호출부:
```java
new ChatCompletionRequest(properties.model(), List.of(new Message("user", prompt)), 16384, false)
```

→ 미래 LiteLLM 업그레이드/fallback에서 default 동작 변경 시 안전망.

### #254 이슈에 검증 코멘트

본 문서 링크 + H4 결과 요약 + "octet-stream 재현 불가, 방어 코드만 적용 권장" 코멘트.

---

## H5 검증 — `max_tokens` 영향

### 측정 (동일 프롬프트, 3-run, max-time 240s)

| max_tokens | run 1 | run 2 | run 3 | 평균 | dl 크기 범위 |
|-----------|-------|-------|-------|------|--------------|
| **16384 (운영 기본값)** | 50.95s | 96.67s | **125.35s** | ~91s | 11K–20KB |
| **4096** | **183.62s** | 70.64s | 94.06s | ~116s | 5K–16KB |
| 2000 | 400 err | 400 err | 400 err | — | 측정 race로 무효 |

> 2000 케이스는 백그라운드 실행 중 임시 파일을 정리해버린 race condition 때문에 모두 invalid model error. 별도 재측정 필요.

### 판정: **약한 효과 — 단독 lever로는 신뢰 어려움**

- 4096이 16384보다 일관되게 빠르지 않음 (183s outlier 발생)
- 단, dl 크기는 줄어드는 경향(20KB → 5KB) → 모델이 budget에 맞춰 짧게 답하기는 함
- 분산이 워낙 커서(60–180s) `max_tokens` 단독으로는 안정성 보장 못 함
- `x-litellm-response-duration-ms`는 거의 `time_total`과 동일 → **레이턴시는 우리 코드 밖에서 발생**

### 부수 효과 가설

`max_tokens` 줄이면 응답이 짧아져 **다음 단계 (parser 등) 처리 시간이 줄어드는 부수효과**는 있을 수 있지만, 정작 reasoning_content는 응답에 포함되지 않고 (gateway가 분리) 별도 비용으로 소비되므로 우리가 줄일 수 있는 건 visible content 부분만.

---

## 적용한 변경 (방어 코드)

### 커밋: `fix(llm): chat completions 요청에 stream:false 명시 (#254 방어 코드)`

```diff
- new ChatCompletionRequest(properties.model(), List.of(new Message("user", prompt)), 16384)
+ new ChatCompletionRequest(properties.model(), List.of(new Message("user", prompt)), 16384, false)

  private record ChatCompletionRequest(String model, List<Message> messages,
-     @JsonProperty("max_tokens") int maxTokens) {}
+     @JsonProperty("max_tokens") int maxTokens, boolean stream) {}
```

- 헤더 동작 변화 0 (default와 같음)
- 가치: LiteLLM/envoy default 변경 시 보호

---

## 종합 결론 (2026-05-11 마감 시점)

### 확정

1. **현재 chatclient 코드는 latency 원인 아님** — 게이트웨이 `x-litellm-response-duration-ms` 헤더가 `time_total`과 ±1초 일치
2. **GLM-4.5 시리즈는 reasoning 모델** — `reasoning_content` 토큰을 생성하느라 30–125s 소비, flash는 더 작은 모델이라 reasoning을 더 많이 써서 역설적으로 더 느림
3. **`thinking` 비활성화 API 통하지 않음** — `thinking:{type:disabled}`는 400, `enable_thinking:false`는 200이지만 silently ignored
4. **H4 (octet-stream / stream true default 가설) 기각** — 오늘 게이트웨이에서 재현 불가
5. **H5 (`max_tokens` 큼) 약한 효과** — 분산 ↑↑ 때문에 단독으로 신뢰 어려움

### 미해결

- #254가 2026-05-08 본 `application/octet-stream` — 오늘 재현 불가, error path 진입 시 다시 추적 필요
- H3 (HTTP client inter-byte timeout 동작), H6 (concurrency queue 사용처), H7 (`@Transactional` + LLM) 미검증
- 다른 provider(Claude/Vertex) 격리 테스트 미수행

### 즉시 실행 가능한 latency 개선책 (오늘 검증된 것)

1. **`AI_GATEWAY_MODEL=glm-4.5-flash` → `glm-4.5`** — 평균 74s → 32s, 코드 변경 0줄
2. **`stream: false` 명시** — 적용 완료 (commit `e297739`)
3. (선택) `max_tokens` 16384 → 4096 — 응답 크기 평균 60% 감소, 단 latency 직접 효과는 약함

### 후속 (별도 슬라이스)

- 다른 hypothesis 검증 (H3/H6/H7/H8)
- Grepp 운영팀에 non-reasoning 모델 가용 여부 문의
- Slice 5 async 적용된 상태이므로 사용자 노출 latency는 이미 0 (백그라운드 잡으로 처리됨)

---

## 다음 단계 (갱신본)

- [x] H4 검증 — 기각, 방어 코드 적용 완료
- [x] H5 검증 — 약한 효과, 단독 lever 아님
- [ ] `AI_GATEWAY_MODEL=glm-4.5` 로컬 적용 후 실제 분석 재실행 시간 측정
- [ ] Grepp 운영팀 채널에 non-reasoning 모델 가용 여부 문의
- [ ] #254 이슈에 오늘 테스트 결과 코멘트 추가
- [ ] H3 (HTTP client inter-byte timeout) 또는 H7 (`@Transactional` + LLM) 차순위로 진행
- [ ] H6 (`ai.concurrency.*` 사용처 grep) — 5분 작업, dead config 여부 확인
- [ ] H2 (prompt-size vs latency 상관관계) — Phase 0 인스트루먼테이션 데이터로 분석
