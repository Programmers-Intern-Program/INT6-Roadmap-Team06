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

## 2. 공통 계약 원칙

- API, DTO, DB, 프론트 화면은 이 문서의 enum과 validation을 공통 기준으로 사용한다.
- DB에 저장되는 enum은 대문자 스네이크 케이스 문자열로 통일한다.
- JSONB는 자유형 텍스트 묶음이 아니라, 이 문서에 정의된 key shape를 따른다.
- 새 결과 생성 시 기존 row를 덮어쓰지 않고 새 version row를 생성한다.
- 로드맵 원본은 `learning_roadmaps + roadmap_weeks + progress_logs`이며, `roadmap_payload`는 보조 결과다.

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
| skill_source_type | USER_INPUT, GITHUB_EXTRACTED, SYSTEM_DERIVED | 기술 스택 출처 |
| requirement_importance | 1, 2, 3, 4, 5 | 직무 기준 중요도 |
| diagnosis_severity | LOW, MEDIUM, HIGH | 부족 기술 심각도 |

### 3.3 GitHub

| 코드명 | 허용값 | 설명 |
| --- | --- | --- |
| github_access_type | PUBLIC_URL, OAUTH | GitHub 연결 방식 |
| github_evidence_type | README, CODE, TOPIC, LANGUAGE, DESCRIPTION | 근거 타입 |

### 3.4 코딩테스트

| 코드명 | 허용값 | 설명 |
| --- | --- | --- |
| problem_type | ARRAY, STRING, HASH, SORT, STACK_QUEUE, BFS_DFS, BINARY_SEARCH, DP, GREEDY, GRAPH, TREE, PREFIX_SUM, IMPLEMENTATION, ETC | 문제 유형 |
| difficulty | EASY, MEDIUM, HARD | 난이도 |
| analysis_strength_level | NORMAL, WEAK, CRITICAL | 분석용 내부 등급 |

### 3.5 로드맵/진도

| 코드명 | 허용값 | 설명 |
| --- | --- | --- |
| progress_status | TODO, IN_PROGRESS, DONE, SKIPPED | 주차별 진도 상태 |
| resource_type | LECTURE, ARTICLE, PROBLEM, PROJECT, DOCS | 학습 자료 타입 |

### 3.6 비동기 작업 상태

| 코드명 | 허용값 | 설명 |
| --- | --- | --- |
| job_status | REQUESTED, RUNNING, SUCCEEDED, FAILED | 장시간 분석 작업 상태 |

### 3.7 v2 확장

| 코드명 | 허용값 | 설명 |
| --- | --- | --- |
| context_type | PROFILE, PLAN, ACTIVITY | snapshot 종류 |
| message_type | USER, ASSISTANT, SYSTEM | 대화 메시지 종류 |
| detected_intent | CHECK_TODAY_PLAN, CHECK_PROGRESS, REQUEST_REPLAN, ASK_EXPLANATION, GENERAL_CHAT | 코치 의도 분류 |
| pattern_type | CONSECUTIVE_MISS, REPEATED_FAILURE, LOW_ACTIVITY | 감지 패턴 |
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
    "실무형 장애 대응 사례를 추가 학습"
  ]
}
```

규칙
- `missingSkills`는 배열이며 `priorityOrder`는 1부터 중복 없이 증가
- `severity`는 `diagnosis_severity` enum 사용
- summary는 JSONB가 아니라 헤더 컬럼 `summary`에 저장

## 4.3 github_analyses.analysis_payload

필수 key
- `extractedSkills`
- `repoSummaries`
- `evidence`
- `comparisonResult`
- `adjustedDiagnosisSummary`

shape
```json
{
  "extractedSkills": ["Java", "Spring Boot", "Redis"],
  "repoSummaries": [
    {
      "repoName": "ai-growth-coach",
      "primaryLanguage": "Java",
      "summary": "Spring Boot 기반 백엔드 서비스"
    }
  ],
  "evidence": [
    {
      "repoName": "ai-growth-coach",
      "type": "README",
      "source": "README.md",
      "snippet": "Redis 캐시를 사용"
    }
  ],
  "comparisonResult": {
    "matchedSkills": ["Java", "Spring Boot"],
    "missingInGithub": ["Kafka"],
    "newFromGithub": ["Redis"]
  },
  "adjustedDiagnosisSummary": "실사용 근거 기준으로 Redis 경험은 일부 보유"
}
```

규칙
- `evidence.type`은 `github_evidence_type` enum 사용
- `comparisonResult` 내부 key 이름은 고정
- `adjustedDiagnosisSummary`는 문자열 1개로 고정

## 4.4 coding_test_analyses.analysis_payload

필수 key
- `stats`
- `weakTypes`
- `recommendedProblems`

shape
```json
{
  "stats": [
    {
      "problemType": "DP",
      "accuracy": 0.42,
      "averageSolveTimeSeconds": 1800,
      "retryCount": 3,
      "weaknessScore": 87
    }
  ],
  "weakTypes": ["DP", "GRAPH"],
  "recommendedProblems": [
    {
      "problemId": "BOJ-1234",
      "title": "예시 문제",
      "problemType": "DP"
    }
  ]
}
```

규칙
- `accuracy`는 0 이상 1 이하
- `weaknessScore`는 0 이상 100 이하
- `problemType`은 `problem_type` enum 사용

## 4.5 learning_roadmaps.roadmap_payload

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
      "subtopics": ["자료구조", "캐시 전략", "TTL"],
      "resources": [
        {
          "type": "LECTURE",
          "title": "Redis 입문",
          "url": "https://example.com/redis"
        }
      ],
      "estimatedHours": 8
    }
  ]
}
```

규칙
- `weekNumber`는 `roadmap_weeks.week_number`와 동일해야 한다
- `resources.type`은 `resource_type` enum 사용
- 진도 상태는 `roadmap_payload` 안에 저장하지 않는다
- 진도 상태는 `progress_logs` 최신 row 기준으로 계산한다

## 4.6 roadmap_weeks.subtopics_json / resources_json

### subtopics_json
- 타입: `string[]`

### resources_json
```json
[
  {
    "type": "LECTURE",
    "title": "Redis 입문",
    "url": "https://example.com/redis"
  }
]
```

규칙
- `title`은 필수
- `url`은 선택
- `type`은 `resource_type` enum 사용

## 4.7 v2 JSONB 최소 shape

### user_context_snapshots.payload
```json
{
  "profile": {},
  "plan": {},
  "activity": {}
}
```

### agent_events.event_data
```json
{
  "requestId": "optional-string",
  "reason": "재계획 요청 이유"
}
```

### detected_patterns.metadata
```json
{
  "count": 3,
  "windowDays": 7
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
| githubUrl | 선택, `https://github.com/`로 시작하는 profile/repo URL |
| interestAreas | 선택, 최대 10개, 항목당 최대 50자 |

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
| githubUrl | 필수 |
| diagnosisId | 선택 |
| private repo | v1에서는 OAuth 연결이 없으면 분석 제외 가능 |
| repo summary 길이 | 1000자 이하 |

## 5.4 코딩테스트 입력

| 항목 | 규칙 |
| --- | --- |
| problemId | 필수, 최대 100자 |
| problemTitle | 필수, 최대 255자 |
| problemType | 필수, `problem_type` enum |
| difficulty | 선택, `difficulty` enum |
| attemptNumber | 1 이상 |
| solveTimeSeconds | 0 이상 |
| upload batch | 업로드 방식일 때 같은 배치는 같은 `upload_id` 사용 |

## 5.5 로드맵 입력/생성

| 항목 | 규칙 |
| --- | --- |
| diagnosisId | 필수 |
| githubAnalysisId | 선택 |
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
- 코딩테스트 분석
- 로드맵 생성의 장시간 작업 모드

허용 전이
- `REQUESTED -> RUNNING`
- `RUNNING -> SUCCEEDED`
- `RUNNING -> FAILED`

규칙
- `FAILED`는 종료 상태다
- 재시도는 기존 row를 되살리지 않고 새 실행으로 시작한다
- v1에서 동기 처리하더라도 내부적으로는 이 상태 모델을 기준으로 삼는다

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
- `coding_test_analyses`
- `learning_roadmaps`

규칙
- 같은 사용자, 같은 결과 종류 내에서 `version`을 1씩 증가
- 새 실행 결과는 기존 row update가 아니라 새 row insert
- 기본 조회는 최신 version 반환
- 과거 조회가 필요하면 version 지정 조회 허용
- v2 `chat_sessions.profile_version`, `plan_version`은 세션 시작 시 snapshot version을 고정한다

## 7. 구현 체크포인트

- enum 문자열은 화면 표시용 한글과 분리해서 저장한다
- JSONB는 key 이름을 임의로 바꾸지 않는다
- validation은 프론트와 백엔드가 둘 다 수행하되, 최종 기준은 백엔드다
- `roadmap_payload`와 `roadmap_weeks` 내용이 불일치하면 `roadmap_weeks`를 우선한다
- progress 최신 상태 조회 규칙을 팀 전체가 동일하게 사용해야 한다

## 8. 최종 요약

이 문서는 팀 해석 차이를 막기 위한 최소 계약이다.

가장 중요한 기준
- enum 고정
- JSONB key 고정
- validation 고정
- 상태 전이 고정
- 로드맵 원본 고정
- version은 결과 이력용으로 유지