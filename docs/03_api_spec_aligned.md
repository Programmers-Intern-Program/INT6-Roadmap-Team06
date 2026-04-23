# AI 개발자 성장 코치 서비스 API 명세 정렬본

## 1. 문서 목적

이 문서는 기존 API 명세를 DB 물리 스키마 문서와 계약 부록 문서 기준에 맞춰 정렬한 버전이다.

핵심 목적
- 요청/응답 필드 해석 차이 제거
- 필수/선택, enum, validation, 상태 규칙 고정
- v1 구현 범위와 v2 확장 범위 분리
- 로드맵 원본 구조와 version 규칙 반영

적용 기준
- 로드맵 원본 데이터는 `learning_roadmaps + roadmap_weeks + progress_logs`
- `version`은 서비스 단계가 아니라 개별 결과 이력 관리용
- enum, JSONB key, validation은 계약 부록 문서를 따른다
- DB PK는 `BIGINT`지만, API 응답의 식별자는 프론트 안전성을 위해 문자열로 반환한다

---

## 2. 공통 규칙

### 2.1 인증

- 로그인 방식: OAuth + JWT
- 보호 API는 `Authorization: Bearer {accessToken}` 사용
- 사용자 식별용 `userId`는 요청 body로 받지 않고 인증 컨텍스트에서 해석한다

### 2.2 콘텐츠 타입

- 기본: `application/json`
- 파일 업로드: `multipart/form-data`

### 2.3 식별자와 시간 포맷

- `id` 계열: 문자열
- `uploadId`: UUID 문자열
- 날짜/시간: ISO-8601 문자열
- `targetDate`: `YYYY-MM-DD`

### 2.4 공통 응답 규칙

성공 응답
```json
{
  "data": {},
  "meta": {}
}
```

실패 응답
```json
{
  "code": "INVALID_INPUT",
  "message": "요청 값이 올바르지 않습니다.",
  "details": {
    "field": "currentLevel"
  }
}
```

### 2.5 공통 에러 코드

- `INVALID_INPUT`
- `UNAUTHORIZED`
- `FORBIDDEN`
- `RESOURCE_NOT_FOUND`
- `CONFLICT`
- `UNSUPPORTED_FILE_FORMAT`
- `ANALYSIS_FAILED`
- `ROADMAP_GENERATION_FAILED`
- `INTERNAL_SERVER_ERROR`

### 2.6 enum 사용 규칙

- enum 허용값은 계약 부록 문서를 따른다
- 화면 표시용 한글은 프론트에서 매핑하고, API는 enum 원문 문자열을 사용한다

---

## 3. v1 API

## 3.1 프로필 API

### 3.1.1 프로필 생성 또는 수정

- Method: `POST`
- Path: `/api/profiles`

요청 body
```json
{
  "targetRole": "BACKEND_ENGINEER",
  "currentLevel": "JUNIOR",
  "skills": [
    {
      "skillName": "Spring Boot",
      "proficiencyLevel": "WORKING"
    },
    {
      "skillName": "PostgreSQL",
      "proficiencyLevel": "BASIC"
    }
  ],
  "interestAreas": ["백엔드", "성능 최적화"],
  "githubUrl": "https://github.com/example",
  "weeklyStudyHours": 10,
  "targetDate": "2026-08-31"
}
```

필드 규칙
- `targetRole`: 필수, `job_roles.role_code` 기준 문자열
- `currentLevel`: 필수, `current_level` enum
- `skills`: 필수, 1~20개
- `skills[].skillName`: 필수, 최대 100자
- `skills[].proficiencyLevel`: 선택, `proficiency_level` enum
- `interestAreas`: 선택, 최대 10개
- `githubUrl`: 선택, GitHub URL 형식
- `weeklyStudyHours`: 선택, 1~40
- `targetDate`: 선택, 오늘 이후 날짜

응답 body
```json
{
  "data": {
    "profileId": "101",
    "savedAt": "2026-04-22T14:10:00Z",
    "message": "프로필이 저장되었습니다."
  }
}
```

### 3.1.2 내 프로필 조회

- Method: `GET`
- Path: `/api/profiles/me`

응답 body
```json
{
  "data": {
    "userId": "1",
    "profileId": "101",
    "targetRole": "BACKEND_ENGINEER",
    "currentLevel": "JUNIOR",
    "skills": [
      {
        "skillName": "Spring Boot",
        "proficiencyLevel": "WORKING",
        "sourceType": "USER_INPUT"
      }
    ],
    "interestAreas": ["백엔드", "성능 최적화"],
    "githubUrl": "https://github.com/example",
    "weeklyStudyHours": 10,
    "targetDate": "2026-08-31"
  }
}
```

---

## 3.2 역량 진단 API

### 3.2.1 역량 진단 실행

- Method: `POST`
- Path: `/api/diagnoses`

요청 body
```json
{
  "profileId": "101"
}
```

필드 규칙
- `profileId`: 필수, 현재 로그인 사용자 소유 프로필이어야 함

응답 body
```json
{
  "data": {
    "diagnosisId": "301",
    "version": 1,
    "profileId": "101",
    "targetRole": "BACKEND_ENGINEER",
    "currentLevel": "JUNIOR",
    "summary": "백엔드 기본기는 있으나 Redis, Kafka 경험이 부족합니다.",
    "missingSkills": [
      {
        "skillName": "Redis",
        "severity": "HIGH",
        "reason": "캐시/세션 설계 경험 부족",
        "priorityOrder": 1
      }
    ],
    "createdAt": "2026-04-22T14:20:00Z"
  }
}
```

### 3.2.2 역량 진단 결과 조회

- Method: `GET`
- Path: `/api/diagnoses/{diagnosisId}`

응답 body
```json
{
  "data": {
    "diagnosisId": "301",
    "version": 1,
    "profileId": "101",
    "targetRole": "BACKEND_ENGINEER",
    "currentLevel": "JUNIOR",
    "summary": "백엔드 기본기는 있으나 Redis, Kafka 경험이 부족합니다.",
    "missingSkills": [
      {
        "skillName": "Redis",
        "severity": "HIGH",
        "reason": "캐시/세션 설계 경험 부족",
        "priorityOrder": 1
      }
    ],
    "strengths": ["Spring Boot", "JPA"],
    "recommendations": [
      "Redis 캐시와 TTL 기반 설계를 먼저 학습"
    ],
    "createdAt": "2026-04-22T14:20:00Z"
  }
}
```

---

## 3.3 GitHub 분석 API

### 3.3.1 GitHub 분석 실행

- Method: `POST`
- Path: `/api/github-analyses`

요청 body
```json
{
  "githubUrl": "https://github.com/example",
  "diagnosisId": "301"
}
```

필드 규칙
- `githubUrl`: 필수
- `diagnosisId`: 선택
- `diagnosisId`가 있으면 현재 사용자 소유 진단 결과여야 함

v1 처리 기준
- 현재 명세의 기본 외부 계약은 동기 응답이다
- 추후 장시간 작업으로 확장할 경우 `job_status` 상태 모델을 따른다

응답 body
```json
{
  "data": {
    "analysisId": "401",
    "version": 1,
    "diagnosisId": "301",
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
    "adjustedDiagnosisSummary": "실사용 근거 기준으로 Redis 경험은 일부 보유",
    "createdAt": "2026-04-22T14:25:00Z"
  }
}
```

### 3.3.2 GitHub 분석 결과 조회

- Method: `GET`
- Path: `/api/github-analyses/{analysisId}`

응답 body
- `POST /api/github-analyses`의 `data`와 동일 구조

---

## 3.4 코딩테스트 API

### 3.4.1 코딩테스트 이력 업로드

- Method: `POST`
- Path: `/api/coding-tests/submissions`

요청 방식
- `multipart/form-data`
- 또는 `application/json`

JSON 요청 body 예시
```json
{
  "submissions": [
    {
      "problemId": "BOJ-1000",
      "problemTitle": "A+B",
      "problemType": "IMPLEMENTATION",
      "difficulty": "EASY",
      "isCorrect": true,
      "attemptNumber": 1,
      "solveTimeSeconds": 120,
      "submittedAt": "2026-04-22T13:00:00Z"
    }
  ]
}
```

응답 body
```json
{
  "data": {
    "uploadId": "a6c0bb7d-f6db-4bc5-9de4-99b159d2ad54",
    "totalCount": 10,
    "acceptedCount": 10,
    "rejectedCount": 0
  }
}
```

### 3.4.2 코딩테스트 분석 실행

- Method: `POST`
- Path: `/api/coding-tests/analyses`

요청 body
```json
{
  "uploadId": "a6c0bb7d-f6db-4bc5-9de4-99b159d2ad54"
}
```

필드 규칙
- `uploadId`: 선택
- `uploadId`가 있으면 해당 업로드 배치만 분석
- `uploadId`가 없으면 현재 사용자의 전체 제출 이력을 분석
- `userId`는 받지 않는다

응답 body
```json
{
  "data": {
    "analysisId": "501",
    "version": 1,
    "uploadId": "a6c0bb7d-f6db-4bc5-9de4-99b159d2ad54",
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
    ],
    "recommendation": "DP와 GRAPH 유형을 우선 보완하는 것이 좋습니다.",
    "analyzedAt": "2026-04-22T14:30:00Z"
  }
}
```

### 3.4.3 코딩테스트 분석 결과 조회

- Method: `GET`
- Path: `/api/coding-tests/analyses/{analysisId}`

응답 body
- `POST /api/coding-tests/analyses`의 `data`와 동일 구조

---

## 3.5 학습 로드맵 API

### 3.5.1 로드맵 생성

- Method: `POST`
- Path: `/api/roadmaps`

요청 body
```json
{
  "diagnosisId": "301",
  "githubAnalysisId": "401",
  "codingTestAnalysisId": "501",
  "weeklyStudyHours": 10,
  "targetDate": "2026-08-31"
}
```

필드 규칙
- `diagnosisId`: 필수
- `githubAnalysisId`: 선택
- `codingTestAnalysisId`: 선택
- `weeklyStudyHours`: 선택, 프로필 값 override 용도
- `targetDate`: 선택, 프로필 값 override 용도

응답 body
```json
{
  "data": {
    "roadmapId": "601",
    "version": 1,
    "diagnosisId": "301",
    "githubAnalysisId": "401",
    "codingTestAnalysisId": "501",
    "totalWeeks": 12,
    "summary": "Redis와 코딩테스트 DP 보완을 중심으로 12주 계획을 생성했습니다.",
    "weeks": [
      {
        "roadmapWeekId": "7001",
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
        "estimatedHours": 8.0
      }
    ],
    "createdAt": "2026-04-22T14:40:00Z"
  }
}
```

정리
- 화면에는 `weekNumber`를 표시한다
- 저장과 진도 추적은 `roadmapWeekId`를 기준으로 한다
- 원본 데이터는 `learning_roadmaps + roadmap_weeks + progress_logs`다

### 3.5.2 로드맵 조회

- Method: `GET`
- Path: `/api/roadmaps/{roadmapId}`

응답 body
```json
{
  "data": {
    "roadmapId": "601",
    "version": 1,
    "totalWeeks": 12,
    "summary": "Redis와 코딩테스트 DP 보완을 중심으로 12주 계획을 생성했습니다.",
    "weeks": [
      {
        "roadmapWeekId": "7001",
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
        "estimatedHours": 8.0,
        "progressStatus": "IN_PROGRESS",
        "progressNote": "캐시 전략 학습 중",
        "progressUpdatedAt": "2026-04-22T15:00:00Z"
      }
    ],
    "createdAt": "2026-04-22T14:40:00Z"
  }
}
```

조회 규칙
- `progressStatus`, `progressNote`, `progressUpdatedAt`는 `progress_logs` 최신 row 기준으로 계산한다
- `roadmap_payload`만 보고 진도 상태를 계산하지 않는다

### 3.5.3 진도 체크 저장

- Method: `POST`
- Path: `/api/roadmaps/{roadmapId}/progress`

요청 body
```json
{
  "roadmapWeekId": "7001",
  "status": "DONE",
  "note": "TTL과 캐시 무효화 개념 정리 완료"
}
```

필드 규칙
- `roadmapWeekId`: 필수, path의 `roadmapId`에 속한 주차여야 함
- `status`: 필수, `progress_status` enum
- `note`: 선택, 최대 1000자
- 상태 변경은 update가 아니라 append-only 로그 추가

응답 body
```json
{
  "data": {
    "progressLogId": "9001",
    "roadmapWeekId": "7001",
    "status": "DONE",
    "savedAt": "2026-04-22T15:10:00Z"
  }
}
```

---

## 4. v2 확장 API

## 4.1 코치 세션 생성

- Method: `POST`
- Path: `/api/coach/sessions`

요청 body
```json
{}
```

응답 body
```json
{
  "data": {
    "sessionId": "10001",
    "profileVersion": 3,
    "planVersion": 2,
    "startedAt": "2026-04-22T16:00:00Z"
  }
}
```

규칙
- 세션 시작 시점의 snapshot version을 고정한다
- 이후 대화 중 새 결과가 생성되어도 현재 세션의 기준 버전은 바뀌지 않는다

## 4.2 코치 메시지 전송

- Method: `POST`
- Path: `/api/coach/messages`

요청 body
```json
{
  "sessionId": "10001",
  "message": "오늘 무엇부터 공부하면 좋을까?"
}
```

응답 body
```json
{
  "data": {
    "responseText": "이번 주는 Redis TTL과 캐시 무효화 개념부터 정리하는 것이 좋습니다.",
    "detectedIntent": "CHECK_TODAY_PLAN",
    "suggestedTriggers": []
  }
}
```

## 4.3 코치 스트리밍 연결

- Method: `GET`
- Path: `/api/coach/stream/{sessionId}`

응답
- `text/event-stream`

이벤트 예시
```text
event: token
data: {"text":"이번 주는 Redis TTL..."}

event: done
data: {"sessionId":"10001"}
```

## 4.4 오늘의 데일리 퀘스트 조회

- Method: `GET`
- Path: `/api/coach/daily-quests/today`

응답 body
```json
{
  "data": {
    "quests": [
      {
        "questType": "ROADMAP",
        "title": "1주차 Redis 자료 1개 정리",
        "isCompleted": false
      }
    ],
    "streak": 3
  }
}
```

---

## 5. 상태 및 버전 규칙

### 5.1 version 규칙

적용 대상
- `capability_diagnoses`
- `github_analyses`
- `coding_test_analyses`
- `learning_roadmaps`

규칙
- 새 실행은 기존 row update가 아니라 새 row insert
- 같은 사용자, 같은 결과 종류 내에서 `version` 1씩 증가
- 기본 조회는 최신 결과 반환 정책을 따르되, 상세 조회는 ID 기준으로 고정한다

### 5.2 진도 상태 규칙

허용값
- `TODO`
- `IN_PROGRESS`
- `DONE`
- `SKIPPED`

규칙
- 상태 이력은 `progress_logs` append-only
- 최신 상태는 사용자 + 주차 기준 최신 row로 계산
- `DONE -> TODO` 직접 복귀는 허용하지 않는다

### 5.3 비동기 상태 모델

현재 v1 외부 계약
- 기본은 동기 처리

확장 시 내부 기준
- `REQUESTED`
- `RUNNING`
- `SUCCEEDED`
- `FAILED`

---

## 6. 구현 시 최종 체크포인트

- `userId`를 요청 body로 받지 않는다
- BIGINT PK는 API에서 문자열로 반환한다
- enum 허용값은 계약 부록 문서를 기준으로 통일한다
- `targetRole`은 자유 텍스트가 아니라 `job_roles.role_code` 기준이다
- `coding-tests/analyses`는 `uploadId`가 없으면 전체 제출 이력을 분석한다
- `roadmap_payload`는 보조 결과이고, 진도 원본은 `progress_logs`다
- v2 코치 API는 세션 생성 후 메시지 전송 순서를 따른다

## 7. 이번 정렬본에서 핵심 수정한 부분

- `userId` 기반 요청 제거
- `uploadId` 없는 코테 분석 범위 명확화
- `targetRole`, `currentLevel`, `status` 등 enum 계약 명확화
- ID 응답 타입 문자열로 고정
- 로드맵 진도 원본을 `progress_logs`로 명확화
- v2 코치 세션 생성 API 추가
- version을 결과 이력 개념으로 명확화
