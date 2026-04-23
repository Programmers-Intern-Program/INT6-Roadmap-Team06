# AI 개발자 성장 코치 서비스 DB 물리 스키마 문서

## 1. 문서 목적

이 문서는 기존 논리 데이터 모델을 실제 구현 가능한 물리 스키마 수준으로 고정하기 위한 문서다.

적용 목적
- 팀원 간 컬럼 타입, NULL 허용 여부, 제약조건 해석 차이 제거
- API, DTO, validation의 기준점 제공
- v1 원본 데이터 구조와 v2 확장 구조의 연결 기준 고정

핵심 기준
- v1, v2는 서비스 단계 구분이다.
- `version`은 개별 결과 이력 관리용이다.
- 로드맵의 원본 데이터는 `learning_roadmaps + roadmap_weeks + progress_logs`다.
- `roadmap_payload`는 결과 보관 및 snapshot 조립용이며 원본이 아니다.

## 2. 공통 물리 설계 기준

### 2.1 네이밍
- 테이블명: snake_case 복수형
- 컬럼명: snake_case
- 논리 엔티티 `User`는 물리 테이블 `users`로 사용한다.

### 2.2 기본 타입 기준
- PK: `BIGINT` + identity
- FK: `BIGINT`
- 일반 문자열: `VARCHAR(n)`
- 긴 설명/요약: `TEXT`
- 구조화 결과: `JSONB`
- 시간: `TIMESTAMPTZ`
- 날짜: `DATE`
- 불리언: `BOOLEAN`

### 2.3 공통 컬럼 기준
- 생성 시각은 `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- 수정 시각이 필요한 테이블은 `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- JSONB 컬럼은 `NOT NULL`로 두고 기본값을 명시한다.
- 결과 테이블의 `version`은 사용자별, 결과 종류별로 1부터 증가한다.

### 2.4 공통 제약 기준
- 문자열 길이는 UI 입력 길이보다 약간 넉넉하게 잡는다.
- enum은 DB native enum 대신 `VARCHAR` + 계약 부록의 허용값으로 통일한다.
- 상태 이력은 가능하면 append-only를 우선한다.
- FK 삭제 규칙은 원본 데이터 보호를 우선한다.

## 3. 논리 엔티티와 물리 테이블 매핑

| 논리 엔티티 | 물리 테이블 |
| --- | --- |
| User | users |
| UserProfile | user_profiles |
| JobRole | job_roles |
| SkillRequirement | skill_requirements |
| UserSkill | user_skills |
| CapabilityDiagnosis | capability_diagnoses |
| GithubConnection | github_connections |
| GithubProject | github_projects |
| GithubAnalysis | github_analyses |
| CodingTestSubmission | coding_test_submissions |
| CodingTestAnalysis | coding_test_analyses |
| LearningRoadmap | learning_roadmaps |
| RoadmapWeek | roadmap_weeks |
| ProgressLog | progress_logs |
| UserContextSnapshot | user_context_snapshots |
| ChatSession | chat_sessions |
| CoachConversation | coach_conversations |
| AgentEvent | agent_events |
| DetectedPattern | detected_patterns |

## 4. v1 물리 스키마

### 4.1 users

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| email | VARCHAR(255) | N | UNIQUE |
| password_hash | VARCHAR(255) | Y | 로컬 로그인만 사용 |
| auth_provider | VARCHAR(30) | N | DEFAULT 'LOCAL' |
| provider_user_id | VARCHAR(255) | Y | OAuth 사용자 식별값 |
| is_active | BOOLEAN | N | DEFAULT true |
| created_at | TIMESTAMPTZ | N | DEFAULT now() |
| updated_at | TIMESTAMPTZ | N | DEFAULT now() |

인덱스/제약
- `uq_users_email`
- `uq_users_provider_user_id` on (`auth_provider`, `provider_user_id`) where `provider_user_id is not null`

### 4.2 job_roles

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| role_code | VARCHAR(50) | N | UNIQUE |
| role_name | VARCHAR(100) | N | UNIQUE |
| description | TEXT | Y |  |
| is_active | BOOLEAN | N | DEFAULT true |
| created_at | TIMESTAMPTZ | N | DEFAULT now() |
| updated_at | TIMESTAMPTZ | N | DEFAULT now() |

### 4.3 user_profiles

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | UNIQUE, FK -> users.id |
| job_role_id | BIGINT | N | FK -> job_roles.id |
| current_level | VARCHAR(30) | N | 계약 부록 enum |
| weekly_study_hours | SMALLINT | Y |  |
| target_date | DATE | Y |  |
| github_url | VARCHAR(255) | Y |  |
| interest_areas_json | JSONB | N | DEFAULT '[]'::jsonb |
| created_at | TIMESTAMPTZ | N | DEFAULT now() |
| updated_at | TIMESTAMPTZ | N | DEFAULT now() |

삭제 규칙
- `user_profiles.user_id`는 `ON DELETE CASCADE`
- `user_profiles.job_role_id`는 `ON DELETE RESTRICT`

### 4.4 skill_requirements

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| job_role_id | BIGINT | N | FK -> job_roles.id |
| skill_name | VARCHAR(100) | N |  |
| category | VARCHAR(50) | N |  |
| importance | SMALLINT | N | 1~5 |
| created_at | TIMESTAMPTZ | N | DEFAULT now() |
| updated_at | TIMESTAMPTZ | N | DEFAULT now() |

인덱스/제약
- `uq_skill_requirements_role_skill` on (`job_role_id`, `skill_name`)
- `idx_skill_requirements_role_category` on (`job_role_id`, `category`)

### 4.5 user_skills

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | FK -> users.id |
| skill_name | VARCHAR(100) | N |  |
| proficiency_level | VARCHAR(30) | Y | 계약 부록 enum |
| source_type | VARCHAR(30) | N | 계약 부록 enum |
| created_at | TIMESTAMPTZ | N | DEFAULT now() |

인덱스/제약
- `uq_user_skills_user_skill_source` on (`user_id`, `skill_name`, `source_type`)
- `idx_user_skills_user_id`

### 4.6 capability_diagnoses

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | FK -> users.id |
| profile_id | BIGINT | N | FK -> user_profiles.id |
| job_role_id | BIGINT | N | FK -> job_roles.id |
| version | INTEGER | N | 사용자별 순차 증가 |
| current_level | VARCHAR(30) | N | 계약 부록 enum |
| summary | TEXT | N |  |
| diagnosis_payload | JSONB | N | DEFAULT '{}'::jsonb |
| created_at | TIMESTAMPTZ | N | DEFAULT now() |

인덱스/제약
- `uq_capability_diagnoses_user_version` on (`user_id`, `version`)
- `idx_capability_diagnoses_user_created_at` on (`user_id`, `created_at desc`)

### 4.7 github_connections

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | FK -> users.id |
| github_url | VARCHAR(255) | N |  |
| access_type | VARCHAR(30) | N | 계약 부록 enum |
| connected_at | TIMESTAMPTZ | N | DEFAULT now() |
| disconnected_at | TIMESTAMPTZ | Y |  |

인덱스/제약
- `uq_github_connections_user_url` on (`user_id`, `github_url`)

### 4.8 github_projects

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | FK -> users.id |
| github_connection_id | BIGINT | Y | FK -> github_connections.id |
| repo_name | VARCHAR(200) | N |  |
| repo_url | VARCHAR(255) | N |  |
| primary_language | VARCHAR(50) | Y |  |
| description | TEXT | Y |  |
| analyzed_at | TIMESTAMPTZ | N | DEFAULT now() |

인덱스/제약
- `uq_github_projects_user_repo_url` on (`user_id`, `repo_url`)
- `idx_github_projects_user_id`

### 4.9 github_analyses

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | FK -> users.id |
| diagnosis_id | BIGINT | Y | FK -> capability_diagnoses.id |
| version | INTEGER | N | 사용자별 순차 증가 |
| summary | TEXT | N |  |
| analysis_payload | JSONB | N | DEFAULT '{}'::jsonb |
| created_at | TIMESTAMPTZ | N | DEFAULT now() |

인덱스/제약
- `uq_github_analyses_user_version` on (`user_id`, `version`)
- `idx_github_analyses_user_created_at` on (`user_id`, `created_at desc`)

### 4.10 coding_test_submissions

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | FK -> users.id |
| upload_id | UUID | Y | 배치 업로드 그룹 식별자 |
| problem_id | VARCHAR(100) | N | 외부 문제 식별자 |
| problem_title | VARCHAR(255) | N |  |
| problem_type | VARCHAR(50) | N | 계약 부록 enum |
| difficulty | VARCHAR(30) | Y | 계약 부록 enum |
| is_correct | BOOLEAN | N |  |
| attempt_number | SMALLINT | N | DEFAULT 1 |
| solve_time_seconds | INTEGER | Y |  |
| submitted_at | TIMESTAMPTZ | N |  |

인덱스
- `idx_coding_test_submissions_user_submitted_at` on (`user_id`, `submitted_at desc`)
- `idx_coding_test_submissions_upload_id`
- `idx_coding_test_submissions_user_problem_type`

### 4.11 coding_test_analyses

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | FK -> users.id |
| upload_id | UUID | Y | 특정 업로드 기준 분석 시 사용 |
| version | INTEGER | N | 사용자별 순차 증가 |
| recommendation | TEXT | N |  |
| analysis_payload | JSONB | N | DEFAULT '{}'::jsonb |
| analyzed_at | TIMESTAMPTZ | N | DEFAULT now() |

인덱스/제약
- `uq_coding_test_analyses_user_version` on (`user_id`, `version`)
- `idx_coding_test_analyses_user_analyzed_at` on (`user_id`, `analyzed_at desc`)

### 4.12 learning_roadmaps

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | FK -> users.id |
| diagnosis_id | BIGINT | N | FK -> capability_diagnoses.id |
| github_analysis_id | BIGINT | Y | FK -> github_analyses.id |
| coding_test_analysis_id | BIGINT | Y | FK -> coding_test_analyses.id |
| version | INTEGER | N | 사용자별 순차 증가 |
| total_weeks | SMALLINT | N |  |
| summary | TEXT | N |  |
| roadmap_payload | JSONB | N | DEFAULT '{}'::jsonb |
| created_at | TIMESTAMPTZ | N | DEFAULT now() |

인덱스/제약
- `uq_learning_roadmaps_user_version` on (`user_id`, `version`)
- `idx_learning_roadmaps_user_created_at` on (`user_id`, `created_at desc`)

### 4.13 roadmap_weeks

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| roadmap_id | BIGINT | N | FK -> learning_roadmaps.id |
| week_number | SMALLINT | N | 1 이상 |
| topic | VARCHAR(255) | N |  |
| subtopics_json | JSONB | N | DEFAULT '[]'::jsonb |
| resources_json | JSONB | N | DEFAULT '[]'::jsonb |
| estimated_hours | NUMERIC(4,1) | N | 0 초과 |

인덱스/제약
- `uq_roadmap_weeks_roadmap_week_number` on (`roadmap_id`, `week_number`)
- `idx_roadmap_weeks_roadmap_id`

### 4.14 progress_logs

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | FK -> users.id |
| roadmap_week_id | BIGINT | N | FK -> roadmap_weeks.id |
| status | VARCHAR(30) | N | 계약 부록 enum |
| note | TEXT | Y |  |
| completed_at | TIMESTAMPTZ | Y | status가 DONE일 때 사용 |
| created_at | TIMESTAMPTZ | N | DEFAULT now() |

설계 기준
- `progress_logs`는 append-only 로그다.
- 현재 상태 조회는 사용자 + 주차 기준 최신 `created_at` row를 사용한다.

인덱스
- `idx_progress_logs_user_week_created_at` on (`user_id`, `roadmap_week_id`, `created_at desc`)
- `idx_progress_logs_user_created_at` on (`user_id`, `created_at desc`)

## 5. v2 확장 물리 스키마

### 5.1 user_context_snapshots

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | FK -> users.id |
| context_type | VARCHAR(30) | N | PROFILE, PLAN, ACTIVITY 등 |
| version | INTEGER | N | context_type별 순차 증가 |
| payload | JSONB | N | DEFAULT '{}'::jsonb |
| is_active | BOOLEAN | N | DEFAULT true |
| valid_from | TIMESTAMPTZ | N | DEFAULT now() |
| valid_to | TIMESTAMPTZ | Y |  |
| created_at | TIMESTAMPTZ | N | DEFAULT now() |

인덱스/제약
- `uq_user_context_snapshots_user_type_version` on (`user_id`, `context_type`, `version`)
- 부분 unique index: `is_active = true`인 row는 사용자 + context_type별 1개만 유지

### 5.2 chat_sessions

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | FK -> users.id |
| profile_version | INTEGER | N | snapshot 기준 고정 |
| plan_version | INTEGER | N | snapshot 기준 고정 |
| started_at | TIMESTAMPTZ | N | DEFAULT now() |
| ended_at | TIMESTAMPTZ | Y |  |

인덱스
- `idx_chat_sessions_user_started_at` on (`user_id`, `started_at desc`)

### 5.3 coach_conversations

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| session_id | BIGINT | N | FK -> chat_sessions.id |
| user_id | BIGINT | N | FK -> users.id |
| message_type | VARCHAR(20) | N | 계약 부록 enum |
| message_text | TEXT | N |  |
| detected_intent | VARCHAR(50) | Y | 계약 부록 enum |
| created_at | TIMESTAMPTZ | N | DEFAULT now() |

인덱스
- `idx_coach_conversations_session_created_at` on (`session_id`, `created_at`)

### 5.4 agent_events

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | FK -> users.id |
| source_agent | VARCHAR(30) | N |  |
| event_type | VARCHAR(50) | N |  |
| event_data | JSONB | N | DEFAULT '{}'::jsonb |
| processed_at | TIMESTAMPTZ | Y |  |
| created_at | TIMESTAMPTZ | N | DEFAULT now() |

인덱스
- `idx_agent_events_user_created_at` on (`user_id`, `created_at desc`)
- `idx_agent_events_event_type_created_at` on (`event_type`, `created_at desc`)

### 5.5 detected_patterns

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | FK -> users.id |
| pattern_type | VARCHAR(50) | N | 계약 부록 enum |
| severity | VARCHAR(30) | N | 계약 부록 enum |
| metadata | JSONB | N | DEFAULT '{}'::jsonb |
| acknowledged_at | TIMESTAMPTZ | Y |  |
| created_at | TIMESTAMPTZ | N | DEFAULT now() |

인덱스
- `idx_detected_patterns_user_created_at` on (`user_id`, `created_at desc`)
- `idx_detected_patterns_user_pattern_type` on (`user_id`, `pattern_type`)

## 6. FK 삭제 규칙 요약

원칙
- 사용자 삭제 시 사용자 하위 데이터는 함께 삭제 가능
- 기준표 데이터와 과거 분석 결과는 임의 삭제를 막는다
- 결과 이력은 운영 중 함부로 재귀 삭제하지 않는다

권장 규칙
- `users -> user_profiles/user_skills/github_connections/github_projects/github_analyses/coding_test_submissions/coding_test_analyses/learning_roadmaps/...`
  - `ON DELETE CASCADE`
- `job_roles -> user_profiles/skill_requirements/capability_diagnoses`
  - `ON DELETE RESTRICT`
- `learning_roadmaps -> roadmap_weeks`
  - `ON DELETE CASCADE`
- `roadmap_weeks -> progress_logs`
  - `ON DELETE CASCADE`
- 결과 테이블 간 참조
  - `learning_roadmaps.diagnosis_id/github_analysis_id/coding_test_analysis_id`는 `ON DELETE RESTRICT`

## 7. 조회 성능용 핵심 인덱스

반드시 필요한 인덱스
- 최신 진단 조회: `capability_diagnoses(user_id, created_at desc)`
- 최신 GitHub 분석 조회: `github_analyses(user_id, created_at desc)`
- 최신 코테 분석 조회: `coding_test_analyses(user_id, analyzed_at desc)`
- 최신 로드맵 조회: `learning_roadmaps(user_id, created_at desc)`
- 로드맵 주차 조회: `roadmap_weeks(roadmap_id, week_number)`
- 진도 최신 상태 조회: `progress_logs(user_id, roadmap_week_id, created_at desc)`

선택 인덱스
- JSONB 내부 검색이 필요한 경우에만 GIN 인덱스 추가
- 초기에는 payload 전체에 GIN을 남발하지 않는다.

## 8. 구현 시 주의사항

- `users`는 이메일 로그인과 OAuth를 함께 지원하므로 `password_hash`는 nullable이다.
- `user_profiles`는 `job_role_id`를 기준으로 저장하고, API에서 문자열 role 입력은 서버에서 매핑한다.
- `progress_logs`는 현재 상태 테이블이 아니라 상태 이력 로그다.
- `roadmap_payload`만 보고 진도 체크를 구현하면 안 된다.
- 최신 결과 조회는 `MAX(version)` 또는 최신 생성 시각 기준으로 일관되게 구현해야 한다.
- DB 타입과 API 타입은 1:1로 같지 않아도 되지만, 계약 부록의 enum과 validation을 반드시 따라야 한다.

## 9. 최종 결정 요약

- PK는 전부 `BIGINT identity`
- 결과 상세는 `JSONB NOT NULL`
- 버전은 결과 종류별 사용자 단위 이력 관리
- 로드맵 원본은 `learning_roadmaps + roadmap_weeks + progress_logs`
- 진도는 append-only 로그
- enum은 `VARCHAR + 계약 부록`으로 통일