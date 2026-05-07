# v2 세션 버전 고정 정책

## 1. 목적

이 문서는 v2 Coach 세션이 어떤 Context Snapshot version을 읽는지, 세션 중 새 snapshot이 생겼을 때 어떻게 반영할지 정리한다.

핵심 목적은 대화 중 기준 데이터가 자동으로 바뀌어 Coach 응답이 흔들리는 일을 막는 것이다.

## 2. 적용 범위

적용 대상
- Coach 세션 생성
- Coach 대화 중 Context Manager 조회
- 세션 중 새 `PROFILE`, `PLAN` snapshot이 생성된 경우

제외 범위
- DB migration, Entity, Repository, API 구현
- Context Manager 구현
- Coach UI 알림 문구 세부안
- Analyzer, Planner 재실행 정책

## 3. 필드 해석

`chat_sessions`의 version 필드는 원본 결과 row version이 아니라 세션이 읽는 Context Snapshot version이다.

| 컬럼 | 의미 |
| --- | --- |
| `profile_version` | 세션 시작 시 active였던 `PROFILE` snapshot version |
| `roadmap_version` | 세션 시작 시 active였던 `PLAN` snapshot version |

규칙
- 원본 `github_analyses.version`, `capability_diagnoses.version`, `learning_roadmaps.version`은 snapshot payload의 `sourceRefs`에서 추적한다.
- `roadmap_version` 컬럼명은 DB 계약을 유지하되, v2 세션 정책에서는 `PLAN` snapshot version으로 해석한다.
- `CONVERSATION` snapshot은 세션 대화 요약과 활성 신호 요약을 위한 별도 snapshot이며, 세션의 기준 버전 필드로 저장하지 않는다.

## 4. 세션 시작 규칙

Coach 세션을 만들 때 Context Manager는 현재 active snapshot을 조회한다.

필수 고정 값
- active `PROFILE` snapshot version
- active `PLAN` snapshot version

세션 생성 결과
```json
{
  "sessionId": "10",
  "profileVersion": 4,
  "roadmapVersion": 2,
  "startedAt": "2026-05-06T09:00:00Z"
}
```

규칙
- active `PROFILE` snapshot이 없으면 세션을 만들 수 없다.
- active `PLAN` snapshot이 없으면 빈 `PLAN` snapshot을 먼저 생성하고 그 version을 고정한다.
- 세션 생성 후에는 같은 세션의 모든 Coach 응답이 고정된 `profile_version`, `roadmap_version`을 우선 사용한다.

## 5. 세션 중 조회 규칙

Coach가 Context Manager에 컨텍스트를 요청할 때는 세션에 저장된 version을 함께 전달한다.

```text
sessionId=10
profileVersion=4
roadmapVersion=2
template=COACH_PROGRESS_CHECK
```

규칙
- Context Manager는 latest active snapshot보다 세션 고정 version을 우선한다.
- 캐시된 Tier 결과가 있더라도 세션 version과 다르면 재사용하지 않는다.
- 세션 중 새 `PROFILE` 또는 `PLAN` snapshot이 생성되어도 현재 세션의 읽기 기준은 자동으로 바뀌지 않는다.

## 6. 새 snapshot 반영 정책

기본 정책
- 다음 대화 진입 시 새 snapshot 존재를 알리고 적용 여부를 안내한다.
- 현재 진행 중인 대화는 기존 고정 version을 유지한다.

새 snapshot 감지 기준
- active `PROFILE` snapshot version이 세션의 `profile_version`보다 큰 경우
- active `PLAN` snapshot version이 세션의 `roadmap_version`보다 큰 경우

다음 진입 처리
```text
현재 세션 기준: PROFILE v4, PLAN v2
최신 active 기준: PROFILE v4, PLAN v3
처리: 다음 대화 진입 시 새 로드맵이 있음을 안내하고 적용 여부를 사용자에게 묻는다.
```

사용자가 적용하지 않으면 기존 version을 유지한다. 사용자가 적용하면 새 세션을 만들고 최신 active snapshot version을 고정한다.

## 7. 금지 사항

- Coach 응답 생성 도중 latest active snapshot으로 자동 전환하지 않는다.
- 새 로드맵이 생성됐다는 이유만으로 기존 세션의 `roadmap_version`을 조용히 바꾸지 않는다.
- Context Manager가 재계획 여부를 판단하거나 Analyzer/Planner를 직접 실행하지 않는다.
- snapshot version과 원본 결과 version을 같은 값으로 가정하지 않는다.

## 8. 예시

### 예시 1. 새 로드맵 생성

1. 사용자가 Redis 2주차를 상담하며 Coach 세션을 시작한다.
2. 세션은 `PROFILE v4`, `PLAN v2`를 고정한다.
3. 대화 중 재계획으로 새 `PLAN v3`이 생성된다.
4. 현재 대화는 계속 `PLAN v2` 기준으로 답한다.
5. 다음 대화 진입 시 새 로드맵이 있음을 안내하고 적용 여부를 묻는다.

### 예시 2. 프로필 수정

1. 사용자가 목표 직무를 수정해 `PROFILE v5`가 생성된다.
2. 기존 세션은 `PROFILE v4`를 계속 읽는다.
3. 다음 대화 진입 시 프로필 변경이 있음을 안내한다.
4. 사용자가 적용하면 새 세션은 `PROFILE v5`와 현재 active `PLAN` version을 고정한다.

## 9. 문서 연결

- Context Snapshot payload와 active row 규칙은 `docs/16_v2_context_snapshot_contract.md`를 따른다.
- 템플릿별 읽기 범위와 캐시 무게는 `docs/17_v2_context_tier_assembly.md`를 따른다.
- 세션 version 고정은 캐시보다 우선한다.
