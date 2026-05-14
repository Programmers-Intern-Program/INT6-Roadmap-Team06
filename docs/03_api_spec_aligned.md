# AI 개발자 성장 코치 서비스 API 명세 정렬본

## 1. 문서 목적

이 문서는 기존 API 명세를 아키텍처 문서, DB 물리 스키마 문서, 계약 부록 문서 기준에 맞춰 정렬한 버전이다.

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

- 로그인 방식: OAuth (v1: GitHub) + JWT
- OAuth 콜백은 access/refresh JWT를 `HttpOnly; Secure; SameSite=Lax` 쿠키로 발급한다
- 보호 API는 다음 중 하나로 인증 가능하다 (둘 다 허용)
  - `Authorization: Bearer {accessToken}` 헤더
  - `accessToken` 쿠키 (브라우저 SPA 기본 경로)
- refresh 토큰은 `refreshToken` 쿠키로만 전달되며, `POST /api/v1/auth/refresh` 가 새 토큰 쌍을 같은 쿠키로 재발급한다
- 사용자 식별용 `userId`는 요청 body로 받지 않고 인증 컨텍스트에서 해석한다
- 토큰은 만료 전까지 서버 측 무효화 수단이 없다 (revocation은 후속 과제). 따라서 access TTL은 짧게(15분), refresh TTL은 24시간으로 둔다

### 2.2 콘텐츠 타입

- 기본: `application/json`

### 2.3 식별자와 시간 포맷

- `id` 계열: 문자열
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
  "weeklyStudyHours": 10,
  "targetDate": "2026-08-31",
  "resumeAssetId": "1001",
  "portfolioAssetId": "2001"
}
```

필드 규칙
- `targetRole`: 필수, `job_roles.role_code` 기준 문자열
- `currentLevel`: 필수, `current_level` enum
- `skills`: 필수, 1~20개
- `skills[].skillName`: 필수, 최대 100자
- `skills[].proficiencyLevel`: 선택, `proficiency_level` enum
- `interestAreas`: 선택, 최대 10개
- `weeklyStudyHours`: 선택, 1~40
- `targetDate`: 선택, 오늘 이후 날짜
- `resumeAssetId`, `portfolioAssetId`: 선택

저장 규칙
- 프로필은 사용자당 1개를 유지하며, 재저장 시 기존 프로필 row를 갱신한다
- 프로필 저장 시 `skills`는 `USER_INPUT` 출처 기술만 교체하고, GitHub 분석 또는 시스템 파생 기술은 삭제하지 않는다
- `skills[].skillName`은 trim 후 대소문자 구분 없이 중복될 수 없다

응답 body
```json
{
  "data": {
    "profileId": "101",
    "savedAt": "2026-04-24T14:10:00Z",
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
    "weeklyStudyHours": 10,
    "targetDate": "2026-08-31",
    "resumeAssetId": "1001",
    "portfolioAssetId": "2001"
  }
}
```

---

## 3.2 GitHub 연결 API

### 3.2.1 GitHub 연결 등록

- Method: `POST`
- Path: `/api/github/connections`

요청 body
```json
{
  "authorizationCode": "oauth-code-from-github"
}
```

응답 body
```json
{
  "data": {
    "githubConnectionId": "201",
    "githubLogin": "example-user",
    "connectedAt": "2026-04-24T14:15:00Z"
  }
}
```

규칙
- 연결 방식은 OAuth만 허용한다
- 이미 연결된 계정이면 기존 연결을 재사용할 수 있다

### 3.2.2 최신 GitHub 연결 조회

- Method: `GET`
- Path: `/api/github/connections/latest`

응답 body
```json
{
  "data": {
    "githubConnectionId": "201",
    "githubLogin": "example-user",
    "connectedAt": "2026-04-24T14:15:00Z"
  }
}
```

규칙
- 현재 로그인 사용자의 최신 GitHub 연결을 반환한다
- 연결이 없으면 `RESOURCE_NOT_FOUND`로 응답한다

### 3.2.3 연결된 저장소 목록 조회

- Method: `GET`
- Path: `/api/github/repositories?githubConnectionId={id}`

응답 body
```json
{
  "data": {
    "githubConnectionId": "201",
    "repositories": [
      {
        "repositoryId": "9001",
        "repoFullName": "team06/ai-growth-coach",
        "repoUrl": "https://github.com/team06/ai-growth-coach",
        "primaryLanguage": "Java",
        "defaultBranch": "main"
      }
    ]
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
  "githubConnectionId": "201",
  "selectedRepositoryIds": ["9001", "9002"],
  "coreRepositoryIds": ["9001"]
}
```

필드 규칙
- `githubConnectionId`: 필수
- `selectedRepositoryIds`: 필수, 최소 1개
- `coreRepositoryIds`: 필수 배열, `selectedRepositoryIds`의 부분집합

v1 처리 기준
- 현재 명세의 기본 외부 계약은 동기 응답이다
- 장시간 작업 모드에서는 `POST /api/github-analyses/async`와 `job_status` 상태 모델을 사용한다
- GitHub 분석 실행은 새 결과 생성이므로 기존 row를 덮어쓰지 않고 사용자별 다음 `version` row를 생성한다
- 최초 분석 결과의 `userCorrections`는 빈 배열일 수 있고, `finalTechProfile`은 AI 추정 결과 기반 초기값이다

응답 body
```json
{
  "data": {
    "githubAnalysisId": "401",
    "version": 1,
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
        "highlights": ["Redis 캐시 적용", "배치 처리 구성"]
      }
    ],
    "techTags": [
      {
        "skillName": "Redis",
        "tagReason": "캐시 설정과 TTL 관련 코드가 확인됨"
      }
    ],
    "depthEstimates": [
      {
        "skillName": "Redis",
        "level": "APPLIED",
        "reason": "설정과 실제 사용 코드가 함께 존재"
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
    "userCorrections": [],
    "finalTechProfile": {
      "confirmedSkills": ["Java", "Spring Boot"],
      "focusAreas": ["백엔드"]
    },
    "createdAt": "2026-04-24T14:25:00Z"
  }
}
```

### 3.3.2 GitHub 분석 비동기 실행

- Method: `POST`
- Path: `/api/github-analyses/async`

요청 body
- `POST /api/github-analyses`와 동일

응답 body
```json
{
  "data": {
    "jobId": "f5c0b0c1-7c7a-4d58-9f1e-111111111111"
  }
}
```

처리 규칙
- 요청을 접수하면 `REQUESTED` 상태의 job을 만들고 HTTP 202로 응답한다
- 실제 분석은 worker에서 수행하고 `RUNNING`, `SUCCEEDED`, `FAILED` 상태로 갱신한다
- 성공 시 job 상태 payload에는 생성된 `githubAnalysisId`를 결과 ID로 남긴다
- 상태 조회는 `GET /api/jobs/{jobId}/status`를 사용한다

### 3.3.3 GitHub 분석 목록 조회

- Method: `GET`
- Path: `/api/github-analyses`

조회 규칙
- 현재 로그인 사용자의 GitHub 분석 요약 목록을 반환한다
- 목록은 최신 생성 시각 기준으로 정렬한다
- 상세 payload 전체가 필요한 경우 개별 상세 조회를 사용한다

### 3.3.4 GitHub 분석 보정 저장

- Method: `PATCH`
- Path: `/api/github-analyses/{githubAnalysisId}/corrections`

요청 body
```json
{
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

응답 body
```json
{
  "data": {
    "githubAnalysisId": "401",
    "savedAt": "2026-04-24T14:28:00Z",
    "finalTechProfile": {
      "confirmedSkills": ["Java", "Spring Boot", "Redis"],
      "focusAreas": ["백엔드", "성능 최적화"]
    }
  }
}
```

처리 규칙
- 보정 저장은 새 분석 실행이 아니므로 새 `github_analyses` row 또는 새 `version`을 만들지 않는다.
- 기존 `analysis_payload`에서 `userCorrections`, `finalTechProfile`만 요청값으로 교체한다.
- `staticSignals`, `repoSummaries`, `techTags`, `depthEstimates`, `evidences`는 분석 실행 당시의 AI/정적 분석 결과로 유지한다.
- 이 저장은 기존 결과 row의 사용자 확정값 갱신이므로 latest 조회 기준의 `version` 값에는 영향을 주지 않는다.
- 응답의 `savedAt`은 보정 저장 처리 시각이다.

### 3.3.5 GitHub 분석 결과 조회

- Method: `GET`
- Path: `/api/github-analyses/{githubAnalysisId}`

응답 body
- `POST /api/github-analyses`의 `data`와 동일 구조

조회 규칙
- 현재 로그인 사용자가 소유한 `github_analyses` row만 조회할 수 있다.
- 응답의 `githubAnalysisId`, `version`, `createdAt`은 row 헤더 컬럼 기준이다.
- 나머지 분석 상세는 저장된 `analysis_payload` 기준으로 반환한다.
- 보정 저장 후 조회하면 갱신된 `userCorrections`, `finalTechProfile`이 반환된다.

---

## 3.4 역량 진단 API

### 3.4.1 역량 진단 실행

- Method: `POST`
- Path: `/api/diagnoses`

요청 body
```json
{
  "profileId": "101",
  "githubAnalysisId": "401"
}
```

필드 규칙
- `profileId`: 필수, 현재 로그인 사용자 소유 프로필이어야 함
- `githubAnalysisId`: 필수, 현재 로그인 사용자 소유 GitHub 분석 결과여야 함

응답 body
```json
{
  "data": {
    "diagnosisId": "301",
    "version": 1,
    "profileId": "101",
    "githubAnalysisId": "401",
    "targetRole": "BACKEND_ENGINEER",
    "currentLevel": "JUNIOR",
    "summary": "백엔드 기본기는 있으나 Redis와 캐시 설계 경험을 더 강화할 필요가 있습니다.",
    "missingSkills": [
      {
        "skillName": "Redis",
        "severity": "HIGH",
        "reason": "캐시 설계를 프로젝트 수준으로 설명할 근거가 더 필요함",
        "priorityOrder": 1
      }
    ],
    "strengths": ["Spring Boot", "JPA"],
    "recommendations": [
      "Redis 캐시와 TTL 기반 설계를 먼저 학습"
    ],
    "createdAt": "2026-04-24T14:30:00Z"
  }
}
```

### 3.4.2 역량 진단 결과 조회

- Method: `GET`
- Path: `/api/diagnoses/{diagnosisId}`

응답 body
- `POST /api/diagnoses`의 `data`와 동일 구조

### 3.4.3 역량 진단 목록 조회

- Method: `GET`
- Path: `/api/diagnoses`

조회 규칙
- 현재 로그인 사용자의 진단 요약 목록을 반환한다
- 상세 결과는 `diagnosisId` 기준 상세 조회로 확인한다

---

## 3.5 학습 로드맵 API

### 3.5.1 로드맵 생성 제약 조회

- Method: `GET`
- Path: `/api/roadmaps/constraints`

응답 body
```json
{
  "data": {
    "maxWeeks": 24
  }
}
```

규칙
- 프론트 로드맵 생성 화면은 서버의 최대 주차 제약을 이 API로 확인한다
- 실제 생성 요청의 상세 validation은 서버가 최종 기준이다

### 3.5.2 로드맵 생성

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
- `githubAnalysisId`: 선택. Planner에 추가 GitHub 맥락을 제공할 때 사용
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
    "totalWeeks": 12,
    "summary": "Redis 중심의 백엔드 실무 역량 보완 로드맵을 생성했습니다.",
    "weeks": [
      {
        "roadmapWeekId": "7001",
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
        "estimatedHours": 8.0
      }
    ],
    "createdAt": "2026-04-24T14:40:00Z"
  }
}
```

정리
- 화면에는 `weekNumber`를 표시한다
- 저장과 진도 추적은 `roadmapWeekId`를 기준으로 한다
- 원본 데이터는 `learning_roadmaps + roadmap_weeks + progress_logs`다

### 3.5.3 로드맵 조회

- Method: `GET`
- Path: `/api/roadmaps/{roadmapId}`

응답 body
```json
{
  "data": {
    "roadmapId": "601",
    "version": 1,
    "totalWeeks": 12,
    "summary": "Redis 중심의 백엔드 실무 역량 보완 로드맵을 생성했습니다.",
    "weeks": [
      {
        "roadmapWeekId": "7001",
        "weekNumber": 1,
        "topic": "Redis 기초",
        "reason": "백엔드 포지션에서 실무 활용도와 포트폴리오 활용도가 높음",
        "tasks": [
          {
            "type": "READ_DOCS",
            "title": "Redis 공식 문서 읽기"
          }
        ],
        "materials": [
          {
            "type": "DOCS",
            "title": "Redis Documentation",
            "url": "https://redis.io/docs"
          }
        ],
        "estimatedHours": 8.0,
        "progressStatus": "IN_PROGRESS",
        "progressNote": "캐시 전략 학습 중",
        "progressUpdatedAt": "2026-04-24T15:00:00Z"
      }
    ],
    "createdAt": "2026-04-24T14:40:00Z"
  }
}
```

조회 규칙
- `progressStatus`, `progressNote`, `progressUpdatedAt`는 `progress_logs` 최신 row 기준으로 계산한다
- `roadmap_payload`만 보고 진도 상태를 계산하지 않는다

### 3.5.4 로드맵 목록 조회

- Method: `GET`
- Path: `/api/roadmaps`

조회 규칙
- 현재 로그인 사용자의 로드맵 요약 목록을 반환한다
- 개별 로드맵의 주차와 진도 상태는 상세 조회에서 확인한다

### 3.5.5 진도 체크 저장

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
    "savedAt": "2026-04-24T15:10:00Z"
  }
}
```

---

## 3.6 대시보드 API (v1 확장)

### 3.6.1 대시보드 최신 결과 조회

- Method: `GET`
- Path: `/api/dashboard`

응답 body
```json
{
  "data": {
    "userId": "1",
    "profile": {
      "profileId": "101",
      "jobRoleId": "1",
      "currentLevel": "JUNIOR",
      "weeklyStudyHours": 10,
      "targetDate": "2026-08-31",
      "updatedAt": "2026-04-24T14:10:00Z"
    },
    "githubAnalysis": {
      "githubAnalysisId": "401",
      "version": 1,
      "summary": "Spring Boot 기반 백엔드 프로젝트 경험이 확인되었습니다.",
      "createdAt": "2026-04-24T14:25:00Z",
      "finalTechProfile": {
        "confirmedSkills": ["Java", "Spring Boot", "Redis"],
        "focusAreas": ["백엔드", "성능 최적화"]
      },
      "userCorrectionCount": 1
    },
    "diagnosis": {
      "diagnosisId": "301",
      "version": 1,
      "summary": "Redis와 캐시 설계 경험을 보완할 필요가 있습니다.",
      "createdAt": "2026-04-24T14:30:00Z"
    },
    "roadmap": {
      "roadmapId": "601",
      "version": 1,
      "totalWeeks": 12,
      "summary": "Redis 중심의 백엔드 실무 역량 보완 로드맵입니다.",
      "createdAt": "2026-04-24T14:40:00Z",
      "progress": {
        "totalWeeks": 12,
        "todoWeeks": 7,
        "inProgressWeeks": 1,
        "doneWeeks": 3,
        "skippedWeeks": 1
      }
    }
  }
}
```

조회 규칙
- 현재 로그인 사용자의 최신 프로필, 최신 GitHub 분석, 최신 진단, 최신 로드맵을 조립한다
- GitHub 분석, 진단, 로드맵의 최신 결과는 `version desc, created_at desc` 순서로 선택한다
- GitHub 분석 요약에는 `finalTechProfile`과 `userCorrectionCount`만 포함하고, 보정 상세 문장은 GitHub 분석 상세 조회에서 확인한다
- 아직 생성되지 않은 결과 영역은 `null`로 반환한다
- 로드맵 진행률은 `progress_logs` 최신 row 기준으로 계산한다
- 대시보드는 화면 편의용 snapshot이며, 각 결과의 원본 상세 조회를 대체하지 않는다

## 3.7 포트폴리오 초안 API

### 3.7.1 초안 생성

- Method: `POST`
- Path: `/api/portfolio/drafts`
- 요청 body: 없음

응답 body
```json
{
  "data": {
    "draftId": "9001",
    "title": "백엔드 성장 포트폴리오 초안",
    "draftPayload": {
      "format": "PROJECT_WRITEUP",
      "variants": [
        {
          "key": "DONE",
          "label": "완료 기반",
          "content": "## 개요\n완료한 학습과 구현만 기준으로 작성한 초안"
        },
        {
          "key": "DONE_IN_PROGRESS",
          "label": "완료 + 진행 중",
          "content": "## 개요\n완료와 진행 중인 작업까지 포함한 초안"
        },
        {
          "key": "ALL",
          "label": "전체 계획 포함",
          "content": "## 개요\n예정 후보까지 다음 개선으로 포함한 초안"
        }
      ]
    },
    "sourceRefs": {
      "roadmaps": [{"roadmapId": "601", "roadmapVersion": 2}],
      "roadmapWeekIds": ["7001"],
      "progressLogIds": ["8001"],
      "githubAnalyses": [{"githubAnalysisId": "401", "githubAnalysisVersion": 3}],
      "coachConversationIds": ["10001"]
    },
    "createdAt": "2026-05-13T14:00:00Z",
    "updatedAt": "2026-05-13T14:00:00Z"
  },
  "meta": {}
}
```

생성 규칙
- 공식 학습 근거는 `progress_logs + roadmap_weeks + learning_roadmaps`를 기준으로 한다
- 최근 여러 `github_analyses`를 병합하되 같은 repo는 최신 분석 내용을 우선한다
- Coach USER 발화는 학습 메모 후보로만, Coach 답변은 예정/추천 후보로만 사용한다
- 저장된 근거에 없는 URL, 책, 강의, 프로젝트명은 생성하지 않는다

### 3.7.2 초안 조회와 수정

- 목록 조회: `GET /api/portfolio/drafts`
- 상세 조회: `GET /api/portfolio/drafts/{draftId}`
- 편집 저장: `PATCH /api/portfolio/drafts/{draftId}`

수정 요청 body
```json
{
  "title": "수정된 백엔드 성장 포트폴리오 초안",
  "draftPayload": {
    "format": "PROJECT_WRITEUP",
    "variants": [
      {
        "key": "DONE",
        "label": "완료 기반",
        "content": "사용자가 편집한 초안"
      }
    ]
  }
}
```

조회/수정 규칙
- 모든 조회와 수정은 현재 로그인 사용자의 `portfolio_drafts.user_id` 기준으로 제한한다
- `draftPayload`는 사용자가 편집한 최종 내용을 저장하고, `sourceRefs`는 생성 시점 근거 ID를 보존한다

## 3.8 JobStatus 조회 API

### 3.8.1 장시간 작업 상태 조회

- Method: `GET`
- Path: `/api/jobs/{jobId}/status`

응답 body
```json
{
  "data": {
    "jobId": "job-20260508-001",
    "status": "RUNNING",
    "currentStep": "FETCH_REPOSITORIES",
    "error": null
  },
  "meta": {}
}
```

조회 규칙
- 현재 로그인 사용자 기준으로 `job:status:{userId}:{jobId}` Redis 값을 조회한다
- 없는 `jobId`와 다른 사용자 job은 모두 `RESOURCE_NOT_FOUND`로 응답한다
- 응답의 `status`는 `job_status` 공식 enum만 사용한다
- GitHub 분석 submit 202 전환은 `POST /api/github-analyses/async`를 사용한다

### 3.8.2 장시간 작업 이력 조회

- Method: `GET`
- Path: `/api/jobs/history?limit={limit}`

조회 규칙
- 현재 로그인 사용자의 완료된 작업 이력을 최신순으로 반환한다
- 이력 저장 대상은 `SUCCEEDED`, `FAILED` 상태의 finalized job이다
- `limit`은 서버가 허용 범위로 보정한다

---

## 4. v2 확장 API

Coach API의 상세 계약은 `docs/20_v2_coach_api_contract.md`를 기준으로 한다.
아래 항목은 외부 연결 시 확인해야 하는 핵심 path와 응답 shape 요약이다.

## 4.1 코치 세션 생성

- Method: `POST`
- Path: `/api/coach/sessions`

요청 body
- 없음

응답 body
```json
{
  "data": {
    "sessionId": "10001",
    "profileVersion": 3,
    "roadmapVersion": 2,
    "startedAt": "2026-04-24T16:00:00Z"
  }
}
```

규칙
- 세션 시작 시점의 snapshot version을 고정한다
- 이후 대화 중 새 결과가 생성되어도 현재 세션의 기준 버전은 바뀌지 않는다
- active `PROFILE` 또는 `PLAN` snapshot이 없으면 `SNAPSHOT_NOT_FOUND`를 반환한다

### 4.1.1 활성 코치 세션 조회

- Method: `GET`
- Path: `/api/coach/sessions/active`

규칙
- 현재 로그인 사용자의 최신 `ACTIVE` 세션을 반환한다
- 활성 세션이 없으면 `SESSION_NOT_FOUND` 계열 오류로 처리한다

### 4.1.2 코치 세션 목록 조회

- Method: `GET`
- Path: `/api/coach/sessions`

규칙
- 현재 로그인 사용자의 세션 목록을 시작 시각 내림차순으로 반환한다
- `ACTIVE`, `CLOSED` 세션을 모두 포함한다

## 4.2 코치 메시지 전송

- Method: `POST`
- Path: `/api/coach/sessions/{sessionId}/messages`

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
    "messageId": "9001",
    "responseText": "이번 주는 Redis TTL과 캐시 무효화 개념부터 정리하는 것이 좋습니다.",
    "route": "SIMPLE_GUIDE",
    "replanProposal": null
  }
}
```

재계획 제안이 필요한 경우 `route`는 `REPLAN_SUGGEST`이고 `replanProposal`에 `proposalId`, `reason`, `expiresAt`을 포함한다.

### 4.2.1 코치 메시지 히스토리 조회

- Method: `GET`
- Path: `/api/coach/sessions/{sessionId}/messages`

규칙
- 현재 로그인 사용자의 세션 메시지만 조회할 수 있다
- 종료된 세션도 히스토리 조회는 허용한다
- 메시지는 생성 시각 오름차순으로 반환한다

## 4.3 코치 재계획 확인

- Method: `POST`
- Path: `/api/coach/sessions/{sessionId}/replan`

요청 body
```json
{
  "proposalId": "7001",
  "confirmed": true
}
```

응답 body (`confirmed: true`)
```json
{
  "data": {
    "newRoadmapId": "601",
    "newRoadmapVersion": 3,
    "message": "새 로드맵이 생성됐습니다. 현재 세션은 기존 버전을 기준으로 유지됩니다."
  }
}
```

응답 body (`confirmed: false`)
```json
{
  "data": {
    "dismissed": true,
    "message": "재계획을 보류했습니다."
  }
}
```

## 4.4 코치 스트리밍 연결 (후속/선택)

- Method: `GET`
- Path: `/api/coach/sessions/{sessionId}/stream`

응답
- `text/event-stream`

이벤트 예시
```text
event: token
data: {"text":"이번 주는 Redis TTL..."}

event: done
data: {"messageId":"9001","route":"SIMPLE_GUIDE","replanProposal":null}
```

스트리밍은 후속 API이며, 단일 응답 API와 같은 처리 경로와 응답 필드를 사용한다.

## 4.5 코치 세션 종료

- Method: `DELETE`
- Path: `/api/coach/sessions/{sessionId}`

응답
- `204 No Content`

규칙
- 세션 상태를 `CLOSED`로 마킹한다
- 종료된 세션에 메시지를 보내면 `SESSION_CLOSED`를 반환한다

---

## 5. 상태 및 버전 규칙

### 5.1 version 규칙

적용 대상
- `capability_diagnoses`
- `github_analyses`
- `learning_roadmaps`

규칙
- 새 실행은 기존 row update가 아니라 새 row insert
- 같은 사용자, 같은 결과 종류 내에서 `version` 1씩 증가
- 기본 snapshot/latest 조회는 `version desc, created_at desc` 순서의 최신 결과 반환 정책을 따른다
- 상세 조회는 `id` 기준으로 고정하며, `created_at` 또는 latest 기준으로 다른 row를 대체 조회하지 않는다
- `PATCH /api/github-analyses/{githubAnalysisId}/corrections`는 새 분석 실행이 아니므로 `version`을 올리지 않고 기존 row의 보정 필드만 갱신한다

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

확장 시 API/내부 기준
- `REQUESTED`: 요청 접수됨. #215의 `PENDING` 표현에 대응한다
- `RUNNING`: 작업 실행 중. #215의 `IN_PROGRESS` 표현에 대응한다
- `SUCCEEDED`: 작업 성공 종료. #215의 `COMPLETED` 표현에 대응한다
- `FAILED`: 작업 실패 종료

비동기 조회 API가 추가되면 응답의 `status`는 위 공식 enum만 노출한다.
`PENDING`, `IN_PROGRESS`, `COMPLETED`는 API 응답값으로 사용하지 않는다.

---

## 6. 구현 시 최종 체크포인트

- `userId`를 요청 body로 받지 않는다
- BIGINT PK는 API에서 문자열로 반환한다
- enum 허용값은 계약 부록 문서를 기준으로 통일한다
- `targetRole`은 자유 텍스트가 아니라 `job_roles.role_code` 기준이다
- GitHub 분석 입력은 `githubConnectionId`, `selectedRepositoryIds`, `coreRepositoryIds`를 사용한다
- 로드맵 생성은 `diagnosisId` 중심으로 처리한다
- `roadmap_payload`는 보조 결과이고, 진도 원본은 `progress_logs`다
- v2 코치 API는 세션 생성 후 메시지 전송 순서를 따른다
- SSE 스트리밍은 후속/선택 계약이며 구현 완료 API로 간주하지 않는다

## 7. 이번 정렬본에서 핵심 수정한 부분

- GitHub 연결 입력을 OAuth 기반 계약으로 변경
- 최신 GitHub 연결 조회, GitHub 분석 비동기 실행, 분석/진단/로드맵 목록 조회, Job 이력 조회를 구현 계약에 맞게 보강
- GitHub 분석 결과 구조를 `staticSignals`, `repoSummaries`, `techTags`, `depthEstimates`, `evidences`, `userCorrections`, `finalTechProfile` 기준으로 정리
- 역량 진단 입력을 `profileId + githubAnalysisId`로 변경
- 로드맵 생성 입력을 `diagnosisId` 중심으로 단순화하고 생성 제약 조회를 추가
- 코치 세션 응답 버전 필드를 `roadmapVersion` 기준으로 정리하고 active/session list/message history 조회를 보강
- 코딩테스트 API를 현재 v1 공식 계약 범위에서 제거
