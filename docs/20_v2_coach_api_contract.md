# v2 Coach API 계약

## 1. 목적

이 문서는 v2 Coach의 HTTP API 계약을 정리한다.

Coach는 사용자 발화를 받아 Context Manager에서 조립된 상태를 기반으로 응답을 생성한다.
재분석/재계획은 사용자 동의 이후에만 Analyzer/Planner를 호출한다.

세션 버전 고정 정책은 `docs/18_v2_session_version_policy.md`를 따른다.
Context Manager 조립 기준은 `docs/17_v2_context_tier_assembly.md`를 따른다.
Pattern Detector 감지 원본은 `docs/19_v2_detected_patterns_contract.md`를 따른다.

## 2. 제외 범위

- Context Manager 내부 구현
- Analyzer / Planner 내부 LLM 파이프라인
- Pattern Detector 배치 로직
- DB migration, Entity, Repository 구현
- 스트리밍 구현 세부 (SSE 구현 방식)

---

## 3. Coach 처리 경로 (4-way branch)

Coach는 매 turn에서 사용자 발화 + Context를 분석해 아래 4가지 중 하나로 처리한다.

| 경로 | 조건 | Coach 행동 |
| --- | --- | --- |
| `SIMPLE_GUIDE` | 정보 조회, 현재 주차 확인, 간단한 조언 | 직접 응답 생성 (Analyzer/Planner 미호출) |
| `REPLAN_SUGGEST` | `activeSignals` 누적 + 발화 종합 결과 재계획 필요 판단 | 재계획 제안 메시지 + 사용자 확인 요청 |
| `REPLAN_EXECUTE` | 사용자가 재계획에 명시적으로 동의 | Planner 동기 호출 → 새 roadmap version 생성 |
| `DISMISS` | 신호가 있어도 발화 맥락상 처리 불필요 | 신호 dismiss 마킹 후 일반 응답 |

`REPLAN_SUGGEST`와 `REPLAN_EXECUTE`는 별도 turn으로 분리된다.
Coach는 사용자 동의 없이 Planner를 호출하지 않는다.

---

## 4. API

### 4.1 세션 생성

```
POST /api/coach/sessions
```

요청 body: 없음 (인증 쿠키로 userId 식별)

응답 body
```json
{
  "data": {
    "sessionId": "string",
    "profileVersion": 3,
    "roadmapVersion": 2,
    "startedAt": "2026-05-07T09:00:00Z"
  }
}
```

규칙
- 세션 시작 시점의 active `PROFILE` / `PLAN` snapshot version을 고정한다.
- 세션 중 새 결과가 생성되어도 현재 세션의 기준 버전은 바뀌지 않는다.
- active snapshot이 없으면 `404 SNAPSHOT_NOT_FOUND`를 반환한다.
  사용자에게 프로필 저장 또는 로드맵 생성을 먼저 안내해야 한다.

에러
| 코드 | HTTP | 조건 |
| --- | --- | --- |
| `SNAPSHOT_NOT_FOUND` | 404 | active PROFILE 또는 PLAN snapshot 없음 |
| `UNAUTHORIZED` | 401 | 인증 쿠키 없음 또는 만료 |

---

### 4.2 세션 목록 조회

```
GET /api/coach/sessions
```

응답 body
```json
{
  "data": [
    {
      "sessionId": "string",
      "profileVersion": 3,
      "roadmapVersion": 2,
      "status": "ACTIVE",
      "startedAt": "2026-05-07T09:00:00Z",
      "endedAt": null
    }
  ]
}
```

규칙
- 현재 사용자의 세션만 반환한다.
- `startedAt` 내림차순으로 정렬한다.
- `ACTIVE`, `CLOSED` 세션을 모두 포함한다.
- 세션이 없으면 빈 배열을 반환한다.

---

### 4.3 메시지 전송 (단일 응답)

```
POST /api/coach/sessions/{sessionId}/messages
```

요청 body
```json
{
  "message": "오늘 무엇부터 공부하면 좋을까?"
}
```

응답 body
```json
{
  "data": {
    "messageId": "string",
    "responseText": "이번 주는 Redis TTL과 캐시 무효화 개념부터 정리하는 것이 좋습니다.",
    "route": "SIMPLE_GUIDE",
    "replanProposal": null
  }
}
```

재계획 제안 시 응답 body
```json
{
  "data": {
    "messageId": "string",
    "responseText": "3일간 Redis 학습이 완료되지 않았습니다. 로드맵을 조정해드릴까요?",
    "route": "REPLAN_SUGGEST",
    "replanProposal": {
      "proposalId": "string",
      "reason": "3일 연속 미달성 감지 (Redis 캐시 주차)",
      "expiresAt": "2026-05-08T09:00:00Z"
    }
  }
}
```

규칙
- `sessionId`가 현재 사용자 소유가 아니면 `403 FORBIDDEN`.
- Context Manager는 `COACH_LIGHTWEIGHT` (Tier 1) 또는 `COACH_FULL_CONTEXT` (Tier 3)를 선택한다.
  - 발화에 재계획/재분석 의도가 없으면 `COACH_LIGHTWEIGHT`.
  - `activeSignals`가 있거나 재계획 의도가 감지되면 `COACH_FULL_CONTEXT`.
- 응답에 민감 정보(토큰, 원문 payload)를 포함하지 않는다.

에러
| 코드 | HTTP | 조건 |
| --- | --- | --- |
| `SESSION_NOT_FOUND` | 404 | sessionId 없음 |
| `FORBIDDEN` | 403 | 다른 사용자의 세션 |
| `LLM_TIMEOUT` | 504 | LLM 응답 시간 초과 |
| `LLM_INVALID_RESPONSE` | 502 | LLM 응답 파싱 실패 |

---

### 4.4 메시지 히스토리 조회

```
GET /api/coach/sessions/{sessionId}/messages
```

응답 body
```json
{
  "data": [
    {
      "messageId": "string",
      "role": "USER",
      "messageText": "오늘 무엇부터 공부하면 좋을까?",
      "route": null,
      "detectedIntent": null,
      "createdAt": "2026-05-08T09:00:00Z"
    },
    {
      "messageId": "string",
      "role": "COACH",
      "messageText": "이번 주는 Redis TTL과 캐시 무효화 개념부터 정리하는 것이 좋습니다.",
      "route": "SIMPLE_GUIDE",
      "detectedIntent": "CHECK_TODAY_PLAN",
      "createdAt": "2026-05-08T09:00:03Z"
    }
  ]
}
```

규칙
- `sessionId`가 현재 사용자 소유가 아니면 `403 FORBIDDEN`.
- 종료된 세션도 히스토리 조회는 허용한다.
- 메시지는 `createdAt` 오름차순으로 반환한다.

에러
| 코드 | HTTP | 조건 |
| --- | --- | --- |
| `SESSION_NOT_FOUND` | 404 | sessionId 없음 |
| `FORBIDDEN` | 403 | 다른 사용자의 세션 |

---

### 4.5 재계획 확인 (사용자 동의)

```
POST /api/coach/sessions/{sessionId}/replan
```

요청 body
```json
{
  "proposalId": "string",
  "confirmed": true
}
```

응답 body (confirmed: true)
```json
{
  "data": {
    "newRoadmapId": "string",
    "newRoadmapVersion": 3,
    "message": "새 로드맵이 생성됐습니다. 현재 세션은 기존 버전을 기준으로 유지됩니다."
  }
}
```

응답 body (confirmed: false)
```json
{
  "data": {
    "dismissed": true,
    "message": "재계획을 보류했습니다."
  }
}
```

규칙
- `confirmed: true`이면 Planner를 동기 호출해 새 `learning_roadmaps` version row를 생성한다.
- `confirmed: false`이면 관련 `detected_patterns.processed_at`을 마킹하고 종료한다.
- 현재 세션의 `roadmapVersion`은 변경하지 않는다. 새 버전은 다음 세션에서 반영된다.
- `proposalId`가 만료됐으면 `410 PROPOSAL_EXPIRED`.

에러
| 코드 | HTTP | 조건 |
| --- | --- | --- |
| `PROPOSAL_EXPIRED` | 410 | proposalId 만료 또는 이미 처리됨 |
| `SESSION_NOT_FOUND` | 404 | sessionId 없음 |
| `PLANNER_FAILED` | 502 | Planner LLM 호출 실패 |

---

### 4.6 스트리밍 응답 (선택)

```
GET /api/coach/sessions/{sessionId}/stream
```

응답: `text/event-stream`

```text
event: token
data: {"text":"이번 주는 Redis TTL"}

event: token
data: {"text":"과 캐시 무효화"}

event: done
data: {"messageId":"string","route":"SIMPLE_GUIDE","replanProposal":null}
```

규칙
- 스트리밍은 4.3의 단일 응답과 동일한 처리 경로를 사용한다.
- `done` 이벤트에 최종 `route`와 `replanProposal`을 포함한다.
- 스트리밍 중 오류는 `event: error`로 전달하고 연결을 닫는다.

---

### 4.7 세션 종료

```
DELETE /api/coach/sessions/{sessionId}
```

응답: `204 No Content`

규칙
- 세션 상태를 `CLOSED`로 마킹한다.
- 이미 종료된 세션에 메시지를 보내면 `SESSION_CLOSED` 에러를 반환한다.

---

## 5. Context Manager 호출 기준

| 상황 | 템플릿 | Tier |
| --- | --- | --- |
| 일반 대화 (오늘 할 일, 현재 주차 확인) | `COACH_LIGHTWEIGHT` | Tier 1 |
| 재계획/재분석 의도 감지 또는 activeSignals 존재 | `COACH_FULL_CONTEXT` | Tier 3 |
| Planner 호출 (replan confirmed) | `PLANNER_REPLAN` | Tier 1+2 |

activeSignals는 Context Manager가 `detected_patterns` 미처리 row를 요약해 `CONVERSATION` snapshot에 넣은 값이다.
Coach는 SQL로 직접 `detected_patterns`를 조회하지 않는다.

---

## 6. 에러 코드 목록

| 코드 | HTTP | 설명 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 인증 쿠키 없음 또는 만료 |
| `FORBIDDEN` | 403 | 다른 사용자의 세션 접근 |
| `SESSION_NOT_FOUND` | 404 | sessionId 없음 |
| `SESSION_CLOSED` | 409 | 이미 종료된 세션에 메시지 전송 |
| `SNAPSHOT_NOT_FOUND` | 404 | active PROFILE 또는 PLAN snapshot 없음 |
| `PROPOSAL_EXPIRED` | 410 | replanProposal 만료 또는 이미 처리됨 |
| `LLM_TIMEOUT` | 504 | LLM 응답 시간 초과 |
| `LLM_INVALID_RESPONSE` | 502 | LLM 응답 파싱 실패 |
| `PLANNER_FAILED` | 502 | Planner LLM 호출 실패 |

---

## 7. 관련 문서

- [03_api_spec_aligned.md](03_api_spec_aligned.md) — v2 API 스케치 (4절)
- [05_architecture_aligned.md](05_architecture_aligned.md) — Coach 4경로 분기, Context Manager 템플릿
- [17_v2_context_tier_assembly.md](17_v2_context_tier_assembly.md) — 3-Tier 조립 기준
- [18_v2_session_version_policy.md](18_v2_session_version_policy.md) — 세션 버전 고정 정책
- [19_v2_detected_patterns_contract.md](19_v2_detected_patterns_contract.md) — activeSignals 원본
