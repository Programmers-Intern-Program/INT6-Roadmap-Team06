# AI 개발자 성장 코치 서비스 데이터 모델 문서

## 1. 설계 원칙

- 입력 데이터와 분석 결과를 분리한다.
- 원본 입력과 진행 상태는 정규화된 테이블로 관리한다.
- LLM이 생성한 결과는 JSONB 컬럼으로 묶어 저장한다.
- 결과 데이터는 version 필드로 버전 관리가 가능해야 한다.
- v1 데이터 구조는 v2 Context Manager의 snapshot 구조로 확장 가능해야 한다.

## 2. 핵심 엔티티 목록

### 2.1 v1 엔티티

- User
- UserProfile
- JobRole
- SkillRequirement
- UserSkill
- CapabilityDiagnosis
- GithubConnection
- GithubProject
- GithubAnalysis
- CodingTestSubmission
- CodingTestAnalysis
- LearningRoadmap
- RoadmapWeek
- ProgressLog

### 2.2 v2 확장 엔티티

- UserContextSnapshot
- ChatSession
- CoachConversation
- AgentEvent
- DetectedPattern

## 3. 엔티티 상세

### 3.1 User

목적
- 사용자 계정의 기본 정보를 저장한다.

주요 속성
- id
- email
- passwordHash
- provider
- providerId
- createdAt

### 3.2 UserProfile

목적
- 사용자의 목표 직무와 학습 관련 기본 설정을 저장한다.

주요 속성
- userId
- targetRole
- currentLevel
- weeklyStudyHours
- targetDate
- githubUrl
- interestAreasJson
- createdAt
- updatedAt

### 3.3 JobRole

목적
- 직무 기준 정보를 관리한다.

주요 속성
- id
- roleName
- description

### 3.4 SkillRequirement

목적
- 직무별 필요 역량 기준표를 저장한다.

주요 속성
- id
- jobRoleId
- skillName
- category
- importance

### 3.5 UserSkill

목적
- 사용자가 직접 입력했거나 외부 분석으로 추출된 기술 스택을 저장한다.

주요 속성
- id
- userId
- skillName
- proficiencyLevel
- sourceType

### 3.6 CapabilityDiagnosis

목적
- 역량 진단 결과의 헤더 정보와 상세 결과를 함께 저장한다.

주요 속성
- id
- userId
- version
- currentLevel
- targetRole
- summary
- diagnosisPayload
- createdAt

설명
- `diagnosisPayload`는 부족 기술 목록, severity, reason, priorityOrder, 추천 근거 등 상세 결과를 저장하는 JSONB 필드다.

### 3.7 GithubConnection

목적
- GitHub 연결 정보를 저장한다.

주요 속성
- id
- userId
- githubUrl
- accessType
- connectedAt

### 3.8 GithubProject

목적
- GitHub 분석 대상 저장소 정보를 저장한다.

주요 속성
- id
- userId
- repoName
- repoUrl
- primaryLanguage
- description
- analyzedAt

### 3.9 GithubAnalysis

목적
- GitHub 분석 실행의 헤더 정보와 상세 결과를 저장한다.

주요 속성
- id
- userId
- version
- summary
- analysisPayload
- createdAt

설명
- `analysisPayload`는 추출 기술 목록, repo 요약, 비교 결과, 근거 목록, 보정된 진단 요약 등을 저장하는 JSONB 필드다.

### 3.10 CodingTestSubmission

목적
- 개별 코딩테스트 제출 이력을 저장한다.

주요 속성
- id
- userId
- problemId
- problemTitle
- problemType
- difficulty
- isCorrect
- attemptNumber
- solveTimeSeconds
- submittedAt

### 3.11 CodingTestAnalysis

목적
- 코딩테스트 분석 결과의 헤더 정보와 상세 결과를 저장한다.

주요 속성
- id
- userId
- version
- recommendation
- analysisPayload
- analyzedAt

설명
- `analysisPayload`는 유형별 accuracy, averageSolveTime, retryCount, weaknessScore, weakTypes, 추천 문제 등을 저장하는 JSONB 필드다.

### 3.12 LearningRoadmap

목적
- 학습 로드맵의 헤더 정보와 상세 계획을 저장한다.

주요 속성
- id
- userId
- version
- totalWeeks
- summary
- roadmapPayload
- createdAt

설명
- `roadmapPayload`는 주차별 계획 전체를 JSONB로 저장하는 결과 필드다.
- 단, v1에서 실제 진도 체크 기준은 `RoadmapWeek`를 원본으로 유지한다.

### 3.13 RoadmapWeek

목적
- 로드맵의 주차별 계획을 저장한다.

주요 속성
- id
- roadmapId
- weekNumber
- topic
- subtopicsJson
- resourcesJson
- estimatedHours

### 3.14 ProgressLog

목적
- 사용자의 로드맵 진행 상황을 저장한다.

주요 속성
- id
- userId
- roadmapWeekId
- status
- completedAt
- note

### 3.15 UserContextSnapshot (v2)

목적
- 에이전트가 읽는 사용자 컨텍스트를 버전 단위로 저장한다.

주요 속성
- id
- userId
- contextType
- version
- payload
- validFrom
- validTo

### 3.16 ChatSession (v2)

목적
- 코치 대화 세션과 사용된 버전을 저장한다.

주요 속성
- id
- userId
- profileVersion
- planVersion
- startedAt
- endedAt

### 3.17 CoachConversation (v2)

목적
- 코치 대화 메시지를 저장한다.

주요 속성
- id
- sessionId
- userId
- messageType
- messageText
- detectedIntent
- createdAt

### 3.18 AgentEvent (v2)

목적
- 에이전트 간 이벤트 로그를 저장한다.

주요 속성
- id
- userId
- sourceAgent
- eventType
- eventData
- processedAt
- createdAt

### 3.19 DetectedPattern (v2)

목적
- 패턴 감지 결과를 저장한다.

주요 속성
- id
- userId
- patternType
- severity
- metadata
- acknowledgedAt
- createdAt

## 4. 정리된 제외 엔티티

다음 항목은 별도 테이블 대신 JSONB 결과 필드에 통합한다.

- CapabilityDiagnosisItem
- SkillEvidence
- CodingCategoryStat
- WeaknessAnalysis
- RoadmapTask
- PlanVersion

## 5. 관계 요약

- User 1 : 1 UserProfile
- User 1 : N UserSkill
- JobRole 1 : N SkillRequirement
- User 1 : N CapabilityDiagnosis
- User 1 : N GithubConnection
- User 1 : N GithubProject
- User 1 : N GithubAnalysis
- User 1 : N CodingTestSubmission
- User 1 : N CodingTestAnalysis
- User 1 : N LearningRoadmap
- LearningRoadmap 1 : N RoadmapWeek
- RoadmapWeek 1 : N ProgressLog
- User 1 : N UserContextSnapshot
- User 1 : N ChatSession
- ChatSession 1 : N CoachConversation
- User 1 : N AgentEvent
- User 1 : N DetectedPattern

## 6. 저장 전략

- 입력 데이터와 진행 상태는 정규화된 테이블에 저장한다.
- 진단, GitHub 분석, 코테 분석, 로드맵 상세 계획은 JSONB 결과 필드에 저장한다.
- 결과 테이블은 version 필드로 재조회와 이력 관리를 지원한다.
- v2에서는 Context Manager가 위 결과 JSONB를 조합해 `UserContextSnapshot.payload`를 만든다.
- 로드맵은 `LearningRoadmap + RoadmapWeek + ProgressLog`를 원본으로 유지하고, `roadmapPayload`는 결과 보관 및 snapshot 조립용으로 사용한다.
