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
| GithubConnection | github_connections |
| GithubProject | github_projects |
| GithubAnalysis | github_analyses |
| CapabilityDiagnosis | capability_diagnoses |
| LearningRoadmap | learning_roadmaps |
| RoadmapWeek | roadmap_weeks |
| ProgressLog | progress_logs |
| UserContextSnapshot | user_context_snapshots |
| ChatSession | chat_sessions |
| CoachConversation | coach_conversations |
| AgentEvent | agent_events |
| DetectedPattern | detected_patterns |

참고
- 코딩테스트 관련 테이블은 현재 v1 공식 물리 스키마 범위에서 제외하고 v2 후순위 draft로만 남긴다.

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
| interest_areas_json | JSONB | N | DEFAULT '[]'::jsonb |
| resume_asset_id | BIGINT | Y | 외부 자료 참조 키 |
| portfolio_asset_id | BIGINT | Y | 외부 자료 참조 키 |
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

### 4.6 github_connections

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | FK -> users.id |
| github_user_id | VARCHAR(100) | N | GitHub 사용자 식별값 |
| github_login | VARCHAR(100) | N | GitHub 로그인명 |
| access_type | VARCHAR(30) | N | 계약 부록 enum |
| connected_at | TIMESTAMPTZ | N | DEFAULT now() |
| disconnected_at | TIMESTAMPTZ | Y |  |

인덱스/제약
- `uq_github_connections_user_github_user` on (`user_id`, `github_user_id`)

### 4.7 github_projects

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | FK -> users.id |
| github_connection_id | BIGINT | N | FK -> github_connections.id |
| repo_node_id | VARCHAR(100) | Y | GitHub node id |
| repo_full_name | VARCHAR(200) | N | owner/repo |
| repo_url | VARCHAR(255) | N |  |
| primary_language | VARCHAR(50) | Y |  |
| default_branch | VARCHAR(100) | Y |  |
| is_selected | BOOLEAN | N | DEFAULT false |
| is_core_repo | BOOLEAN | N | DEFAULT false |
| metadata_payload | JSONB | N | DEFAULT '{}'::jsonb |
| synced_at | TIMESTAMPTZ | N | DEFAULT now() |

인덱스/제약
- `uq_github_projects_user_repo_full_name` on (`user_id`, `repo_full_name`)
- `idx_github_projects_connection_selected` on (`github_connection_id`, `is_selected`)

### 4.8 github_analyses

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | FK -> users.id |
| github_connection_id | BIGINT | N | FK -> github_connections.id |
| version | INTEGER | N | 사용자별 순차 증가 |
| summary | TEXT | N |  |
| analysis_payload | JSONB | N | DEFAULT '{}'::jsonb |
| created_at | TIMESTAMPTZ | N | DEFAULT now() |

인덱스/제약
- `uq_github_analyses_user_version` on (`user_id`, `version`)
- `idx_github_analyses_user_version_created_at` on (`user_id`, `version desc`, `created_at desc`)
- `idx_github_analyses_user_created_at` on (`user_id`, `created_at desc`)

### 4.9 capability_diagnoses

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | FK -> users.id |
| profile_id | BIGINT | N | FK -> user_profiles.id |
| github_analysis_id | BIGINT | N | FK -> github_analyses.id |
| job_role_id | BIGINT | N | FK -> job_roles.id |
| version | INTEGER | N | 사용자별 순차 증가 |
| current_level | VARCHAR(30) | N | 계약 부록 enum |
| summary | TEXT | N |  |
| diagnosis_payload | JSONB | N | DEFAULT '{}'::jsonb |
| created_at | TIMESTAMPTZ | N | DEFAULT now() |

인덱스/제약
- `uq_capability_diagnoses_user_version` on (`user_id`, `version`)
- `idx_capability_diagnoses_user_version_created_at` on (`user_id`, `version desc`, `created_at desc`)
- `idx_capability_diagnoses_user_created_at` on (`user_id`, `created_at desc`)

### 4.10 learning_roadmaps

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | FK -> users.id |
| diagnosis_id | BIGINT | N | FK -> capability_diagnoses.id |
| version | INTEGER | N | 사용자별 순차 증가 |
| total_weeks | SMALLINT | N |  |
| summary | TEXT | N |  |
| roadmap_payload | JSONB | N | DEFAULT '{}'::jsonb |
| created_at | TIMESTAMPTZ | N | DEFAULT now() |

인덱스/제약
- `uq_learning_roadmaps_user_version` on (`user_id`, `version`)
- `idx_learning_roadmaps_user_version_created_at` on (`user_id`, `version desc`, `created_at desc`)
- `idx_learning_roadmaps_user_created_at` on (`user_id`, `created_at desc`)

### 4.11 roadmap_weeks

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| roadmap_id | BIGINT | N | FK -> learning_roadmaps.id |
| week_number | SMALLINT | N | 1 이상 |
| topic | VARCHAR(255) | N |  |
| reason_text | TEXT | N |  |
| tasks_json | JSONB | N | DEFAULT '[]'::jsonb |
| materials_json | JSONB | N | DEFAULT '[]'::jsonb |
| estimated_hours | NUMERIC(4,1) | N | 0 초과 |

인덱스/제약
- `uq_roadmap_weeks_roadmap_week_number` on (`roadmap_id`, `week_number`)
- `idx_roadmap_weeks_roadmap_id`

### 4.12 progress_logs

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
| context_type | VARCHAR(30) | N | PROFILE, PLAN, CONVERSATION |
| version | INTEGER | N | context_type별 순차 증가 |
| payload | JSONB | N | DEFAULT '{}'::jsonb |
| valid_from | TIMESTAMPTZ | N | DEFAULT now() |
| valid_to | TIMESTAMPTZ | Y | NULL이면 active |
| created_at | TIMESTAMPTZ | N | DEFAULT now() |

인덱스/제약
- `uq_user_context_snapshots_user_type_version` on (`user_id`, `context_type`, `version`)
- 부분 index: `valid_to is null`인 row 조회 최적화

### 5.2 chat_sessions

| 컬럼 | 타입 | NULL | 제약/기본값 |
| --- | --- | --- | --- |
| id | BIGINT | N | PK, identity |
| user_id | BIGINT | N | FK -> users.id |
| profile_version | INTEGER | N | snapshot 기준 고정 |
| roadmap_version | INTEGER | N | snapshot 기준 고정 |
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
| target_agent | VARCHAR(30) | Y |  |
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
- `users -> user_profiles/user_skills/github_connections/github_projects/github_analyses/capability_diagnoses/learning_roadmaps/...`
  - `ON DELETE CASCADE`
- `job_roles -> user_profiles/skill_requirements/capability_diagnoses`
  - `ON DELETE RESTRICT`
- `learning_roadmaps -> roadmap_weeks`
  - `ON DELETE CASCADE`
- `roadmap_weeks -> progress_logs`
  - `ON DELETE CASCADE`
- 결과 테이블 간 참조
  - `capability_diagnoses.github_analysis_id`와 `learning_roadmaps.diagnosis_id`는 `ON DELETE RESTRICT`

## 7. 조회 성능용 핵심 인덱스

반드시 필요한 인덱스
- 최신 GitHub 분석 조회: `github_analyses(user_id, version desc, created_at desc)`
- 최신 진단 조회: `capability_diagnoses(user_id, version desc, created_at desc)`
- 최신 로드맵 조회: `learning_roadmaps(user_id, version desc, created_at desc)`
- 로드맵 주차 조회: `roadmap_weeks(roadmap_id, week_number)`
- 진도 최신 상태 조회: `progress_logs(user_id, roadmap_week_id, created_at desc)`
- 활성 snapshot 조회: `user_context_snapshots(user_id, context_type)` where `valid_to is null`

선택 인덱스
- JSONB 내부 검색이 필요한 경우에만 GIN 인덱스 추가
