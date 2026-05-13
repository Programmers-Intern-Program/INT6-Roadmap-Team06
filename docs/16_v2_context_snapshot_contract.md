# v2 Context Snapshot 계약

## 1. 목적

이 문서는 v2 Context Manager가 `user_context_snapshots`에 저장하는 읽기용 snapshot 계약을 정리한다.

Context Snapshot은 Coach, Analyzer, Planner가 같은 사용자 상태를 읽기 위한 조립 결과다. PostgreSQL의 v1 원본 데이터와 결과 이력을 대체하지 않는다.

템플릿별로 어느 정도의 snapshot을 읽을지는 `docs/17_v2_context_tier_assembly.md`의 3-Tier Context 조립 기준을 따른다.
Coach 세션에서 snapshot version을 고정하고 새 version을 반영하는 정책은 `docs/18_v2_session_version_policy.md`를 따른다.

## 2. 범위

포함 범위
- `PROFILE`, `PLAN`, `CONVERSATION` snapshot의 최소 payload shape
- 원본 result id/version 참조 기준
- snapshot version 증가와 active row 교체 기준

제외 범위
- DB migration, Entity, Repository, API 구현
- 실제 멀티 에이전트, Event System, Context Manager 구현

Pattern Detector 저장 원본과 `CONVERSATION.activeSignals` 요약 관계는 `docs/19_v2_detected_patterns_contract.md`를 따른다.

## 3. 공통 원칙

- `context_type` 컬럼은 snapshot 종류의 최종 기준이며, payload의 `contextType` 값과 일치해야 한다.
- `payload`는 읽기 최적화된 조립 결과다. 원본 row를 수정하거나 대체하지 않는다.
- 원본 추적을 위해 `sourceRefs`에 사용한 result id와 version을 남긴다.
- 민감 정보인 access token, refresh token, API key, OAuth secret, 원문 쿠키는 payload에 넣지 않는다.
- 화면 표시용 한글 라벨이 아니라 계약 enum 값을 저장한다.

공통 payload 필드
```json
{
  "contextType": "PROFILE",
  "generatedAt": "2026-05-06T09:00:00Z",
  "sourceRefs": {}
}
```

## 4. PROFILE snapshot

목적
- 사용자의 목표, 현재 수준, 기술 스택, GitHub 최종 기술 요약, 진단 요약을 한 번에 읽기 위한 snapshot이다.
- Analyzer, Planner, Coach가 공통으로 참조하는 기본 사용자 상태다.

최소 shape
```json
{
  "contextType": "PROFILE",
  "generatedAt": "2026-05-06T09:00:00Z",
  "sourceRefs": {
    "profileId": "1",
    "githubAnalysisId": "3",
    "githubAnalysisVersion": 2,
    "diagnosisId": "5",
    "diagnosisVersion": 2
  },
  "profile": {
    "targetRole": "BACKEND_DEVELOPER",
    "currentLevel": "JUNIOR",
    "weeklyStudyHours": 8,
    "interestAreas": ["백엔드", "성능 최적화"]
  },
  "skills": [
    {
      "skillName": "Spring Boot",
      "sourceType": "USER_INPUT",
      "proficiencyLevel": "WORKING"
    }
  ],
  "githubFinalTechProfile": {
    "confirmedSkills": ["Java", "Spring Boot", "Redis"],
    "focusAreas": ["백엔드", "성능 최적화"]
  },
  "diagnosisSummary": {
    "summary": "백엔드 기본기는 있으나 Redis 운용 경험 보강이 필요함",
    "topMissingSkills": [
      {
        "skillName": "Redis",
        "severity": "HIGH",
        "priorityOrder": 1
      }
    ],
    "priorityRecommendations": ["Redis 캐시와 TTL 기반 설계를 먼저 학습"]
  }
}
```

규칙
- `profile`은 최신 사용자 프로필을 기준으로 조립한다.
- `githubFinalTechProfile`은 최신 GitHub 분석의 `analysis_payload.finalTechProfile`을 사용한다.
- `diagnosisSummary`는 최신 진단 결과의 헤더 요약과 `diagnosis_payload` 핵심 필드만 축약한다.
- GitHub 분석이나 진단이 없으면 해당 필드는 `null` 또는 빈 배열로 둘 수 있다.

## 5. PLAN snapshot

목적
- 현재 로드맵, 현재 주차, 주차별 진도 요약을 한 번에 읽기 위한 snapshot이다.
- Planner 재계획과 Coach 진도 점검의 기준 데이터다.

최소 shape
```json
{
  "contextType": "PLAN",
  "generatedAt": "2026-05-06T09:00:00Z",
  "sourceRefs": {
    "roadmapId": "7",
    "roadmapVersion": 3,
    "diagnosisId": "5",
    "diagnosisVersion": 2
  },
  "progressAsOf": "2026-05-06T09:00:00Z",
  "roadmap": {
    "title": "백엔드 Redis 역량 보강 로드맵",
    "totalWeeks": 12,
    "summary": "Redis 캐시 설계부터 프로젝트 적용까지 진행",
    "weeks": [
      {
        "weekNumber": 1,
        "topic": "Redis 기초",
        "reason": "캐시 패턴 학습 전 자료구조와 TTL 개념을 먼저 확인",
        "estimatedHours": 4.5,
        "tasks": [
          {
            "title": "TTL 캐시 예제 구현",
            "type": "example"
          }
        ],
        "materials": [
          {
            "title": "Redis 공식 문서",
            "type": "docs",
            "url": "https://redis.io/docs/latest/"
          }
        ]
      }
    ]
  },
  "currentWeek": {
    "roadmapWeekId": "21",
    "weekNumber": 2,
    "topic": "Redis 캐시 패턴",
    "progressStatus": "IN_PROGRESS"
  },
  "weeks": [
    {
      "roadmapWeekId": "20",
      "weekNumber": 1,
      "topic": "Redis 기초",
      "progressStatus": "DONE",
      "taskCount": 4,
      "completedTaskCount": 4
    }
  ],
  "progressSummary": {
    "todoWeeks": 3,
    "inProgressWeeks": 1,
    "doneWeeks": 1,
    "skippedWeeks": 0
  }
}
```

규칙
- 로드맵 원본은 `learning_roadmaps + roadmap_weeks + progress_logs`다.
- `roadmap_payload`는 보조 결과로만 사용하고, `roadmap_weeks`와 불일치하면 `roadmap_weeks`를 우선한다.
- Coach가 실행형 학습 가이드를 만들 때 쓰는 주차별 학습 항목은 `roadmap.weeks[]`에 둔다.
- `roadmap.weeks[].tasks`, `roadmap.weeks[].materials`는 `roadmap_weeks.tasks_json`, `materials_json`의 구조를 유지하며, 파싱할 수 없으면 빈 배열로 둘 수 있다.
- 진도 상태는 `progress_logs` 최신 row 기준으로 계산한다.
- `progressAsOf`는 진도 snapshot을 조립한 기준 시각이다.
- 로드맵이 없으면 `roadmap`, `currentWeek`는 `null`, `weeks`는 빈 배열로 둘 수 있다.

## 6. CONVERSATION snapshot

목적
- Coach 세션이 고정해서 읽는 프로필/계획 snapshot version, 최근 대화 요약, 활성 신호 요약을 담는다.
- 대화 중간에 새 결과가 생성되어도 현재 세션의 읽기 기준이 흔들리지 않게 한다.

최소 shape
```json
{
  "contextType": "CONVERSATION",
  "generatedAt": "2026-05-06T09:00:00Z",
  "sourceRefs": {
    "sessionId": "10",
    "profileSnapshotVersion": 4,
    "planSnapshotVersion": 2,
    "lastConversationId": "42"
  },
  "conversation": {
    "recentSummary": "사용자는 Redis 2주차 과제를 진행 중이며 캐시 무효화 전략을 어려워함",
    "recentMessages": [
      {
        "messageType": "USER",
        "summary": "이번 주 과제가 어렵다고 말함",
        "createdAt": "2026-05-06T08:50:00Z"
      }
    ],
    "detectedIntent": "CHECK_PROGRESS"
  },
  "activeSignals": [
    {
      "sourcePatternId": 31,
      "patternType": "CONSECUTIVE_INCOMPLETE",
      "severity": "MEDIUM",
      "summary": "최근 3일 동안 계획 대비 완료율이 낮음",
      "detectedAt": "2026-05-06T00:00:00Z"
    }
  ]
}
```

규칙
- `chat_sessions.profile_version`은 `PROFILE` snapshot version을 고정한 값이다.
- `chat_sessions.roadmap_version`은 `PLAN` snapshot version을 고정한 값으로 해석한다.
- 세션 중 version 고정과 새 snapshot 반영 정책은 `docs/18_v2_session_version_policy.md`를 따른다.
- 원본 결과 row version은 `sourceRefs` 안에 별도로 남긴다.
- 최근 대화는 전체 원문을 무제한 저장하지 않고 요약 중심으로 조립한다.
- 활성 신호는 Coach 판단 입력일 뿐이며, snapshot 생성 자체가 재분석이나 재계획을 실행하지 않는다.

## 7. version / active 규칙

- `version`은 `user_id + context_type`별로 1씩 증가한다.
- 첫 snapshot은 version `1`로 시작한다.
- 새 snapshot을 active로 만들 때는 기존 active row의 `valid_to`를 닫고 새 row를 insert한다.
- 같은 사용자와 같은 `context_type`에서 `valid_to is null`인 active row는 최대 1개여야 한다.
- 과거 snapshot은 재현성과 디버깅을 위해 유지한다.
- snapshot payload를 갱신해야 하면 기존 payload를 덮어쓰지 않고 새 version row를 만든다.

생성 트리거 기준
- `PROFILE`: 프로필 저장, GitHub 분석 보정 저장, 신규 진단 생성 이후
- `PLAN`: 신규 로드맵 생성, `progress_logs` insert 이후
- `CONVERSATION`: Coach 세션 시작, 대화 요약 갱신, 활성 신호 요약 갱신 이후

## 8. 구현 체크포인트

- Context Manager는 snapshot을 읽고 조립할 뿐, LLM 응답 내용이나 재계획 여부를 결정하지 않는다.
- Coach가 재분석이나 재계획을 제안하더라도 사용자의 명시 동의 전에는 Analyzer/Planner를 호출하지 않는다.
- 세션 중 새 로드맵이 생성되어도 기존 세션은 고정된 `profileSnapshotVersion`, `planSnapshotVersion`을 계속 읽는다.
- 새 snapshot을 반영하려면 다음 세션에서 자동 적용하거나, 현재 세션에서 사용자 확인 후 기준 version을 바꾼다.

예시
- 사용자가 Coach와 Redis 2주차를 상담하는 중에 새 로드맵이 생성돼도, 현재 대화는 세션 시작 시 고정한 PLAN snapshot version `2`를 기준으로 답한다.
- 새 로드맵 version `3`은 다음 대화 진입 시 안내하거나 사용자가 명시적으로 적용을 승인했을 때 읽는다.
