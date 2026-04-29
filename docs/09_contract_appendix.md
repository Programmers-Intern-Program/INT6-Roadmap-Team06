# AI 개발자 성장 코치 서비스 계약 부록 문서

## 1. 문서 목적

이 문서는 팀 개발 시 가장 해석이 갈리기 쉬운 공통 계약을 한곳에 모아 고정하기 위한 문서다.

포함 범위
- enum/code 사전
- JSONB shape
- validation 규칙
- state transition

제외 범위
- 상세 SQL DDL
- 화면 와이어프레임
- API 전문
- Function Calling 세부 인터페이스

## 2. 공통 계약 원칙

- API, DTO, DB, 프론트 화면은 이 문서의 enum과 validation을 공통 기준으로 사용한다.
- DB에 저장되는 enum은 대문자 스네이크 케이스 문자열로 통일한다.
- JSONB는 자유형 텍스트 묶음이 아니라, 이 문서에 정의된 key shape를 따른다.
- 새 결과 생성 시 기존 row를 덮어쓰지 않고 새 version row를 생성한다.
- 로드맵 원본은 `learning_roadmaps + roadmap_weeks + progress_logs`이며, `roadmap_payload`는 보조 결과다.
- GitHub 분석에서 AI 추정값과 사용자 보정값은 구분해 저장한다.

## 3. enum / code 사전

### 3.1 인증/사용자

| 코드명 | 허용값 | 설명 |
| --- | --- | --- |
| auth_provider | LOCAL, GOOGLE, GITHUB | 로그인 제공자 |
| user_active_flag | true, false | 계정 활성 여부 |

### 3.2 프로필/역량

| 코드명 | 허용값 | 설명 |
| --- | --- | --- |
| current_level | BEGINNER, BASIC, JUNIOR, INTERMEDIATE, ADVANCED | 현재 수준 |
| proficiency_level | NONE, BASIC, WORKING, STRONG | 사용자 기술 숙련도 |
| skill_source_type | USER_INPUT, GITHUB_ESTIMATED, SYSTEM_DERIVED | 기술 스택 출처 |
| requirement_importance | 1, 2, 3, 4, 5 | 직무 기준 중요도 |
| diagnosis_severity | LOW, MEDIUM, HIGH | 부족 기술 심각도 |

### 3.3 GitHub

| 코드명 | 허용값 | 설명 |
| --- | --- | --- |
| github_access_type | OAUTH | GitHub 연결 방식 |
| github_evidence_type | README, CODE, CONFIG, REPO_METADATA, COMMIT | 근거 타입 |
| github_depth_level | INTRO, APPLIED, PRACTICAL, DEEP | 사용 깊이 후보 |

### 3.4 로드맵/진도

| 코드명 | 허용값 | 설명 |
| --- | --- | --- |
| progress_status | TODO, IN_PROGRESS, DONE, SKIPPED | 주차별 진도 상태 |
| roadmap_task_type | READ_DOCS, BUILD_EXAMPLE, WRITE_NOTE, APPLY_PROJECT, REVIEW | 실행 작업 타입 |
| material_type | DOCS, ARTICLE, REPOSITORY, VIDEO, TEMPLATE | 참고 자료 타입 |

### 3.5 비동기 작업 상태

| 코드명 | 허용값 | 설명 |
| --- | --- | --- |
| job_status | REQUESTED, RUNNING, SUCCEEDED, FAILED | 장시간 분석 작업 상태 |

### 3.6 v2 확장

| 코드명 | 허용값 | 설명 |
| --- | --- | --- |
| context_type | PROFILE, PLAN, CONVERSATION | snapshot 종류 |
| message_type | USER, ASSISTANT, SYSTEM | 대화 메시지 종류 |
| detected_intent | CHECK_TODAY_PLAN, CHECK_PROGRESS, REQUEST_REPLAN, REQUEST_REANALYSIS, ASK_EXPLANATION, GENERAL_CHAT | 코치 의도 분류 |
| pattern_type | CONSECUTIVE_INCOMPLETE, REPEATED_FAILURE, INTEREST_SHIFT | 감지 패턴 |
| pattern_severity | LOW, MEDIUM, HIGH | 패턴 심각도 |

## 4. JSONB shape 정의

## 4.1 user_profiles.interest_areas_json

타입
- `string[]`

예시
```json
["백엔드", "성능 최적화", "AI 서비스"]
```

## 4.2 capability_diagnoses.diagnosis_payload

필수 key
- `missingSkills`
- `strengths`
- `recommendations`
- `githubInsights`

shape
```json
{
  "missingSkills": [
    {
      "skillName": "Redis",
      "severity": "HIGH",
      "reason": "캐시/세션 설계 경험이 부족함",
      "priorityOrder": 1
    }
  ],
  "strengths": ["Spring Boot", "JPA"],
  "recommendations": [
    "Redis 캐시와 TTL 기반 설계를 먼저 학습",
    "예제 구현 후 미니 프로젝트에 캐시 적용"
  ],
  "githubInsights": {
    "confirmedSkills": ["Java", "Spring Boot"],
    "newFromGithub": ["Redis"]
  }
}
```

규칙
- `missingSkills`는 배열이며 `priorityOrder`는 1부터 중복 없이 증가
- `severity`는 `diagnosis_severity` enum 사용
- summary는 JSONB가 아니라 헤더 컬럼 `summary`에 저장

## 4.3 github_analyses.analysis_payload

필수 key
- `staticSignals`
- `repoSummaries`
- `techTags`
- `depthEstimates`
- `evidences`
- `userCorrections`
- `finalTechProfile`

shape
```json
{
  "staticSignals": {
    "primaryLanguages": [
      {
        "lang": "Java",
        "ratio": 0.6
      }
    ],
    "activeRepos": 12,
    "commitFrequency": "WEEKLY",
    "contributionPattern": "CONSISTENT"
  },
  "repoSummaries": [
    {
      "repoId": "9001",
      "repoName": "team06/ai-growth-coach",
      "summary": "Spring Boot 기반 백엔드 서비스",
      "highlights": ["Redis 캐시 적용", "배치 로직 구성"]
    }
  ],
  "techTags": [
    {
      "skillName": "Redis",
      "tagReason": "캐시 설정, TTL 관련 코드와 설정 파일 확인"
    }
  ],
  "depthEstimates": [
    {
      "skillName": "Redis",
      "level": "APPLIED",
      "reason": "단순 의존성 추가를 넘어 캐시 키 설계와 TTL 사용 흔적이 있음"
    }
  ],
  "evidences": [
    {
      "repoName": "team06/ai-growth-coach",
      "type": "CODE",
      "source": "src/main/java/.../CacheConfig.java",
      "summary": "RedisTemplate과 TTL 설정 사용"
    }
  ],
  "userCorrections": [
    {
      "skillName": "Redis",
      "correction": "캐시에만 사용했고 Pub/Sub은 사용하지 않음"
    }
  ],
  "finalTechProfile": {
    "confirmedSkills": ["Java", "Spring Boot", "Redis"],
    "focusAreas": ["백엔드", "성능 최적화"]
  }
}
```

규칙
- `evidences.type`은 `github_evidence_type` enum 사용
- `depthEstimates.level`은 `github_depth_level` enum 사용
- AI가 만든 후보와 사용자가 확정한 값은 같은 key 아래에 섞어 저장하지 않는다
- `staticSignals`, `repoSummaries`, `techTags`, `depthEstimates`, `evidences`는 분석 실행 시 생성된 AI/정적 분석 결과다
- `userCorrections`는 사용자가 AI 추정 결과를 보정한 설명이며, 보정 저장 시 이 배열 전체를 요청값으로 교체한다
- `finalTechProfile`은 진단/로드맵에서 사용할 사용자 확정 기술 프로필이며, 보정 저장 시 요청값으로 교체한다
- GitHub 분석 실행은 새 `version` row를 생성하지만, 보정 저장은 기존 row의 `analysis_payload.userCorrections`와 `analysis_payload.finalTechProfile`만 갱신한다
- 보정 저장은 사용자 확정값을 같은 분석 결과에 반영하는 작업이므로 latest 조회 기준의 `version`을 변경하지 않는다

## 4.4 learning_roadmaps.roadmap_payload

주의
- 이 필드는 원본 데이터가 아니다.
- 화면 응답용 요약과 snapshot 조립용 보조 결과다.

필수 key
- `weeks`

shape
```json
{
  "weeks": [
    {
      "weekNumber": 1,
      "topic": "Redis 기초",
      "reason": "백엔드 포지션에서 실무 활용도와 포트폴리오 활용도가 높음",
      "tasks": [
        {
          "type": "READ_DOCS",
          "title": "Redis 공식 문서에서 자료구조와 TTL 개념 읽기"
        },
        {
          "type": "BUILD_EXAMPLE",
          "title": "간단한 캐시 예제를 구현해 보기"
        }
      ],
      "materials": [
        {
          "type": "DOCS",
          "title": "Redis Documentation",
          "url": "https://redis.io/docs"
        }
      ],
      "estimatedHours": 8
    }
  ]
}
```

규칙
- `weekNumber`는 `roadmap_weeks.week_number`와 동일해야 한다
- `tasks[].type`은 `roadmap_task_type` enum 사용
- `materials[].type`은 `material_type` enum 사용
- 진도 상태는 `roadmap_payload` 안에 저장하지 않는다
- 진도 상태는 `progress_logs` 최신 row 기준으로 계산한다

## 4.5 roadmap_weeks.tasks_json / materials_json

### tasks_json
```json
[
  {
    "type": "READ_DOCS",
    "title": "Redis 공식 문서 읽기"
  }
]
```

### materials_json
```json
[
  {
    "type": "DOCS",
    "title": "Redis Documentation",
    "url": "https://redis.io/docs"
  }
]
```

규칙
- `title`은 필수
- `url`은 선택
- `type`은 각각 `roadmap_task_type`, `material_type` enum 사용

## 4.6 v2 JSONB 최소 shape

### user_context_snapshots.payload
```json
{
  "profile": {},
  "plan": {},
  "conversation": {}
}
```

### agent_events.event_data
```json
{
  "requestId": "optional-string",
  "triggerReason": "interest_shift",
  "changedSnapshotType": "PLAN"
}
```

### detected_patterns.metadata
```json
{
  "count": 3,
  "windowDays": 7,
  "lastDetectedAt": "2026-04-24T09:00:00Z"
}
```

## 5. validation 규칙

## 5.1 사용자/프로필

| 항목 | 규칙 |
| --- | --- |
| email | 필수, 이메일 형식, 최대 255자 |
| password | 로컬 로그인 시 필수, OAuth 로그인 시 미사용 |
| targetRole | 필수, 서버에서 `job_roles.role_code`로 매핑 가능해야 함 |
| currentLevel | 필수, `current_level` enum |
| weeklyStudyHours | 선택, 1~40 |
| targetDate | 선택, 오늘 이후 날짜 |
| interestAreas | 선택, 최대 10개, 항목당 최대 50자 |
| resumeAssetId | 선택, 숫자 문자열 |
| portfolioAssetId | 선택, 숫자 문자열 |

## 5.2 기술 스택 입력

| 항목 | 규칙 |
| --- | --- |
| skills | 최소 1개, 최대 20개 |
| skillName | 최대 100자 |
| 중복 | 대소문자/공백 정규화 후 중복 제거 |
| proficiencyLevel | 선택, 없으면 `NULL` 허용 |

## 5.3 GitHub 분석 입력

| 항목 | 규칙 |
| --- | --- |
| githubConnectionId | 필수 |
| selectedRepositoryIds | 최소 1개 |
| coreRepositoryIds | 선택, `selectedRepositoryIds` 부분집합 |
| core repo 개수 | 최대 5개 권장 |
| repo summary 길이 | 1000자 이하 |

## 5.4 역량 진단 입력

| 항목 | 규칙 |
| --- | --- |
| profileId | 필수, 현재 사용자 소유 |
| githubAnalysisId | 선택, 현재 사용자 소유. 제공 시 진단 품질이 향상됨 |

## 5.5 로드맵 입력/생성

| 항목 | 규칙 |
| --- | --- |
| diagnosisId | 필수 |
| githubAnalysisId | 선택. Planner에 추가 GitHub 맥락을 제공할 때 사용 |
| codingTestAnalysisId | 선택 |
| totalWeeks | 기본 12, 허용 범위 1~24 |
| estimatedHours | 주차별 0 초과, 소수 1자리 허용 |

## 5.6 progress 저장

| 항목 | 규칙 |
| --- | --- |
| roadmapWeekId | 필수, 해당 사용자의 로드맵 주차여야 함 |
| status | 필수, `progress_status` enum |
| note | 선택, 최대 1000자 |
| completedAt | status가 DONE일 때만 저장 |

## 6. state transition

## 6.1 분석 작업 상태

적용 대상
- GitHub 분석
- 로드맵 생성의 장시간 작업 모드

허용 전이
- `REQUESTED -> RUNNING`
- `RUNNING -> SUCCEEDED`
- `RUNNING -> FAILED`

규칙
- `FAILED`는 종료 상태다
- 재시도는 기존 row를 되살리지 않고 새 실행으로 시작한다
- v1에서 동기 처리하더라도 내부적으로는 이 상태 모델을 기준으로 삼을 수 있다

## 6.2 주차별 진도 상태

허용 전이
- `TODO -> IN_PROGRESS`
- `TODO -> DONE`
- `TODO -> SKIPPED`
- `IN_PROGRESS -> DONE`
- `IN_PROGRESS -> SKIPPED`
- `DONE -> IN_PROGRESS`
- `SKIPPED -> IN_PROGRESS`

규칙
- 사용자가 바로 완료 체크할 수 있으므로 `TODO -> DONE` 허용
- `DONE -> TODO`로 직접 되돌리지는 않는다
- 상태 변경은 append-only로 `progress_logs`에 새 row를 추가한다

## 6.3 버전 증가 규칙

적용 대상
- `capability_diagnoses`
- `github_analyses`
- `learning_roadmaps`

규칙
- 같은 사용자, 같은 결과 종류 내에서 `version`을 1씩 증가
- 새 실행 결과는 기존 row update가 아니라 새 row insert
- 기본 snapshot/latest 조회는 `version desc, created_at desc` 순서의 최신 결과를 반환
- 상세 조회는 `id` 기준으로 고정하며 latest 기준으로 다른 결과를 대신 반환하지 않는다
- 과거 조회가 필요하면 version 지정 조회 허용
- GitHub 분석 보정 저장은 새 실행 결과가 아니므로 기존 row의 `analysis_payload.userCorrections`, `analysis_payload.finalTechProfile`만 갱신하고 `version`을 올리지 않는다
- v2 `chat_sessions.profile_version`, `roadmap_version`은 세션 시작 시 snapshot version을 고정한다

## 7. 구현 체크포인트

- enum 문자열은 화면 표시용 한글과 분리해서 저장한다
- JSONB는 key 이름을 임의로 바꾸지 않는다
- validation은 프론트와 백엔드가 둘 다 수행하되, 최종 기준은 백엔드다
- `roadmap_payload`와 `roadmap_weeks` 내용이 불일치하면 `roadmap_weeks`를 우선한다
- progress 최신 상태 조회 규칙을 팀 전체가 동일하게 사용해야 한다
- GitHub 분석에서 AI 후보와 사용자 보정값은 구분해 취급해야 한다

## 8. 최종 요약

이 문서는 팀 해석 차이를 막기 위한 최소 계약이다.

가장 중요한 기준
- enum 고정
- JSONB key 고정
- validation 고정
- 상태 전이 고정
- 로드맵 원본 고정
- version은 결과 이력용으로 유지
