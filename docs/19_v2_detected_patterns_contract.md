# v2 detected_patterns 계약

## 1. 목적

이 문서는 v2 Pattern Detector가 감지한 사용자 행동 패턴을 어떤 원본 구조로 저장하고, Coach가 이를 어떤 읽기 맥락으로 사용하는지 정리한다.

핵심 결정은 다음과 같다.

- 공식 저장 원본은 `detected_patterns`다.
- `user_signals`는 공식 테이블명이 아니라 Coach 대화 맥락에서 해석된 "사용자 신호" 표현으로만 사용한다.
- `CONVERSATION.activeSignals`는 `detected_patterns` 원본 row를 대화에 맞게 요약한 읽기 payload다.
- Pattern Detector는 이벤트를 발행하지 않는다. `pattern.detected` 이벤트는 사용하지 않는다.

## 2. 역할 분리

### Pattern Detector

Pattern Detector는 SQL 배치 기반으로 사용자 학습 행동의 반복 패턴을 감지한다.

예시 패턴:

- 반복 미완료
- 연속 진도 지연
- 특정 기술 학습 반복 실패
- 관심사 또는 목표 변화 후보

Pattern Detector는 LLM을 호출하지 않는다.
정기 배치에서 카운팅, 임계치 검사, 최근 활동 비교를 수행하고 감지 결과를 `detected_patterns`에 저장한다.

### Context Manager

Context Manager는 Coach가 대화에 사용할 수 있도록 미처리 `detected_patterns`를 읽어 `CONVERSATION.activeSignals`로 요약한다.

이 요약은 원본을 대체하지 않는다.
패턴 정확도, 빈도, 재계획 전환 여부 같은 분석 기준은 항상 `detected_patterns` 원본 row를 따른다.

### Coach

Coach는 수치 기반 패턴을 직접 감지하지 않는다.
Coach는 Context Manager가 전달한 `activeSignals`를 사용자 발화와 함께 해석해 다음 중 하나를 수행한다.

- 재계획 제안
- 보충 학습 제안
- 단순 안내
- dismiss 처리

Coach 또는 Context Manager가 해당 패턴을 대화 판단에 반영했거나 dismiss 처리하면 `detected_patterns.processed_at`을 기록한다.

## 3. detected_patterns 최소 계약

`detected_patterns`는 v2 draft 테이블이며 실제 DB migration, Entity, Repository 구현은 이 문서 범위에 포함하지 않는다.

최소 필드:

| 필드 | 설명 |
| --- | --- |
| `id` | 감지 패턴 식별자 |
| `user_id` | 사용자 식별자 |
| `pattern_type` | 감지된 패턴 유형 |
| `severity` | 패턴 심각도 |
| `metadata` | 감지 근거 JSONB |
| `processed_at` | Coach/Context Manager 처리 완료 시각 |
| `created_at` | 감지 시각 |

`processed_at`은 사용자 확인 시각이 아니라 시스템이 해당 패턴을 대화 판단에 반영했거나 dismiss 처리한 시각이다.

## 4. metadata 예시

반복 미완료 패턴 예시:

```json
{
  "count": 3,
  "windowDays": 7,
  "targetType": "roadmap_week",
  "targetId": 12,
  "skill": "Redis",
  "lastDetectedAt": "2026-05-06T00:00:00Z"
}
```

metadata는 감지 원인과 후속 분석에 필요한 근거만 담는다.
Coach 응답 문장이나 LLM 해석 결과를 원본 metadata에 저장하지 않는다.

## 5. activeSignals 요약 계약

`CONVERSATION.activeSignals`는 Coach가 읽는 대화용 요약이다.
원본 `detected_patterns` row에서 필요한 최소 정보만 추려 snapshot payload에 포함한다.

최소 필드:

```json
{
  "sourcePatternId": 31,
  "patternType": "REPEATED_INCOMPLETE_TASK",
  "severity": "MEDIUM",
  "summary": "Redis 2주차 과제가 최근 3회 미완료 상태로 남아 있음",
  "detectedAt": "2026-05-06T00:00:00Z"
}
```

규칙:

- `sourcePatternId`는 원본 `detected_patterns.id`를 참조한다.
- `summary`는 Coach 대화에 필요한 짧은 요약이며 원본 근거는 `metadata`에 남긴다.
- `processed_at IS NULL`인 row만 active signal 후보가 된다.
- active signal로 요약됐다는 이유만으로 원본 row를 수정하지 않는다.

조회 우선순위:

1. Context Manager는 active `CONVERSATION` snapshot의 `payload.activeSignals`를 먼저 읽는다.
2. `CONVERSATION` snapshot이 없거나 `activeSignals`가 비어 있으면 전환기 fallback으로 미처리 `detected_patterns` row를 직접 조회해 대화용 activeSignals 형태로 요약한다.
3. Coach는 Context Manager가 조립한 activeSignals만 읽고, SQL로 `detected_patterns`를 직접 조회하지 않는다.
4. fallback은 snapshot 조립 파이프라인이 완성되기 전까지의 호환 경로이며 공식 대화용 payload 기준은 `CONVERSATION.activeSignals`다.

## 6. Event System 관계

Pattern Detector는 `pattern.detected` 이벤트를 발행하지 않는다.
감지 결과는 `detected_patterns` row로 남기고, Coach가 다음 turn에 Context Manager를 통해 pull 한다.

무거운 재분석 또는 재계획은 사용자의 명시 요청이나 Coach 제안에 대한 사용자 확인 후 `coach.requested_reanalysis`, `coach.requested_replan` 이벤트로 연결한다.

## 7. 제외 범위

- DB migration
- Entity, Repository, API 구현
- Context Manager 구현
- Pattern Detector 임계치 정책 상세
- Coach LLM prompt/schema 변경
