# AI 개발자 성장 코치 서비스 기능 명세 정렬본

## 1. 문서 목적

이 문서는 기존 기능 명세를 아키텍처 문서, 사용자 흐름 문서, 이후 계약 문서 기준에 맞춰 팀 개발 계약 수준으로 정렬한 버전이다.

핵심 목적
- 기능별 입력, 처리, 출력, 저장 규칙 해석 차이 제거
- GitHub 중심 v1 흐름과 v2 멀티 에이전트 책임 경계 반영
- 로드맵 원본 구조와 append-only 진도 로그 규칙 반영
- v1 구현 범위와 v2 확장 범위를 기능 책임 기준으로 다시 고정

적용 기준
- enum, JSONB key, validation은 계약 부록 문서를 따른다
- API 입력/응답 형태는 API 명세 정렬본을 따른다
- 로드맵 원본 데이터는 `learning_roadmaps + roadmap_weeks + progress_logs`다
- `version`은 서비스 단계가 아니라 개별 결과 이력 관리용이다

---

## 2. 기능 분류 기준

- v1 필수: MVP에서 반드시 구현할 기능
- v1 확장: 여유가 있을 경우 추가하는 기능
- v2 확장: 멀티 에이전트 구조에서 확장하는 기능

공통 원칙
- v1은 페이지 기반 기능 완성이 우선이다
- v2는 v1 결과를 재사용하는 상위 경험 레이어다
- 같은 기능이라도 입력 규칙, 상태 규칙, 저장 규칙은 문서 간 동일한 의미를 유지해야 한다

---

## 3. 프로필 관리 기능

### 3.1 기능명

프로필 생성 및 수정

### 3.2 목적

GitHub 분석, 역량 진단, 로드맵 생성의 공통 기준이 되는 사용자 프로필을 저장한다.

### 3.3 입력

- `targetRole`
- `currentLevel`
- `skills[]`
  - `skillName`
  - `proficiencyLevel`
- `interestAreas[]`
- `weeklyStudyHours`
- `targetDate`
- `resumeAssetId(optional)`
- `portfolioAssetId(optional)`

입력 규칙
- `targetRole`은 자유 텍스트가 아니라 `job_roles.role_code` 기준 문자열이다
- `currentLevel`, `proficiencyLevel`은 enum 허용값만 사용한다
- 자료 선택 값은 선택 항목이다

### 3.4 처리

1. 인증 컨텍스트에서 현재 사용자 식별
2. `targetRole`을 `job_roles` 기준으로 검증
3. 프로필 저장 또는 갱신
4. 사용자 입력 기술 스택을 `user_skills`에 `USER_INPUT` source로 저장
5. 관심 분야와 선택 자료 참조값을 저장

### 3.5 출력

- `profileId`
- 저장 완료 시각
- 저장 메시지
- 조회 시 현재 프로필 전체

### 3.6 저장 규칙

- 프로필 기본값은 `user_profiles`에 저장한다
- 기술 스택은 `user_skills`에 저장한다
- 사용자 식별용 `userId`는 요청 body로 받지 않는다
- 한 사용자당 활성 프로필은 1개를 기본으로 한다

### 3.7 단계 구분

- v1 필수
  - 프로필 생성/수정/조회
- v1 확장
  - 입력 추천, 자동 완성
- v2 확장
  - 대화 중 프로필 변경 감지 후 재평가 유도

---

## 4. GitHub 분석 기능

### 4.1 기능명

GitHub 기반 실제 사용 기술 분석

### 4.2 목적

실제 프로젝트 기반 기술 사용 근거를 수집하고 사용자 보정을 반영해 신뢰도 높은 기술 프로필을 만든다.

### 4.3 입력

- `githubConnectionId`
- `selectedRepositoryIds[]`
- `coreRepositoryIds[]`

입력 규칙
- GitHub 연결은 URL 수기 입력이 아니라 OAuth 연결 기준으로 처리한다
- `coreRepositoryIds[]`는 `selectedRepositoryIds[]`의 부분집합이어야 한다
- 입력된 저장소는 현재 로그인 사용자 소유 연결에 속해야 한다

### 4.4 처리

1. GitHub 연결 상태 확인 또는 재사용
2. 선택된 저장소의 메타데이터와 정적 신호 수집
3. 핵심 repo에 대한 LLM 요약 생성
4. 기술 태그, 사용 깊이 후보, 근거 정보 계산
5. 사용자 보정값 반영
6. `finalTechProfile` 생성
7. 새 결과 row 생성 및 version 증가

### 4.5 출력

- `githubAnalysisId`
- `version`
- `staticSignals`
- `repoSummaries[]`
- `techTags[]`
- `depthEstimates[]`
- `evidences[]`
- `userCorrections[]`
- `finalTechProfile`

### 4.6 저장 규칙

- 연결 정보는 `github_connections`에 저장한다
- 저장소 메타정보는 `github_projects`에 저장한다
- 분석 실행 결과는 `github_analyses`와 `analysis_payload` JSONB에 저장한다
- AI 추정값과 사용자 보정값은 구분해 보관한다

### 4.7 상태 규칙

- v1 외부 계약은 기본 동기 처리다
- 화면 상태는 `연결 전 / 분석 중 / 분석 완료 / 보정 저장 완료 / 분석 실패`로 표현할 수 있다
- 추후 비동기화 시 내부 상태는 별도 job 모델로 확장할 수 있다

### 4.8 단계 구분

- v1 필수
  - 저장소 선택, 정적 분석, 핵심 repo 요약, 사용자 보정
- v1 확장
  - 프로젝트별 근거 카드 고도화
  - 기술 태그 수정 UI 강화
- v2 확장
  - Analyzer 재실행
  - 이력서 데이터와 통합 분석

---

## 5. 역량 진단 기능

### 5.1 기능명

역량 진단 및 부족 스택 추천

### 5.2 목적

사용자의 목표 직무, 프로필, GitHub 최종 분석 결과를 비교해 부족한 기술과 우선순위를 도출한다.

### 5.3 입력

- `profileId`
- `githubAnalysisId`

입력 규칙
- `profileId`, `githubAnalysisId`는 현재 로그인 사용자 소유 데이터여야 한다
- 진단 실행 시 프로필과 GitHub 최종 분석 결과를 함께 사용한다

### 5.4 처리

1. 프로필 조회
2. GitHub 최종 분석 결과 조회
3. `job_roles`, `skill_requirements` 기준표 조회
4. 입력 기술 스택과 실제 사용 기술 스택을 함께 분류
5. 부족 기술 도출
6. severity와 priority 계산
7. 현재 역량 요약 생성
8. 새 결과 row 생성 및 version 증가

### 5.5 출력

- `diagnosisId`
- `version`
- `targetRole`
- `currentLevel`
- `summary`
- `missingSkills[]`
- `strengths[]`
- `recommendations[]`

### 5.6 저장 규칙

- 결과 헤더는 `capability_diagnoses`에 저장한다
- 상세 결과는 `diagnosis_payload` JSONB에 저장한다
- 기존 row update가 아니라 새 row insert 방식으로 저장한다
- `summary`는 JSONB가 아니라 헤더 컬럼에 저장한다

### 5.7 상태 규칙

- v1 외부 계약은 기본 동기 처리다
- 결과 상태 테이블을 따로 두지 않고 성공 시 결과를 반환한다
- 추후 장시간 작업으로 확장할 경우 별도 job 상태 모델을 따른다

### 5.8 단계 구분

- v1 필수
  - GitHub 반영 진단, 부족 기술 도출, 우선순위 계산
- v1 확장
  - 카테고리별 점수화
  - 관심 분야 반영 강화
- v2 확장
  - 대화 중 재진단 트리거
  - snapshot version 기반 재평가

---

## 6. 학습 로드맵 생성 기능

### 6.1 기능명

개인화 학습 로드맵 생성 및 진도 관리

### 6.2 목적

진단 결과를 바탕으로 실행 가능한 학습 계획을 생성하고, 주차별 진도를 관리한다.

### 6.3 입력

로드맵 생성 입력
- `diagnosisId`
- `weeklyStudyHours`
- `targetDate`

진도 저장 입력
- `roadmapWeekId`
- `status`
- `note`

입력 규칙
- `diagnosisId`는 필수다
- `weeklyStudyHours`, `targetDate`는 프로필 값 override 용도다
- 진도 저장은 `roadmapWeekId` 기준으로 한다
- `status`는 `progress_status` enum만 허용한다

### 6.4 처리

1. 진단 결과 조회
2. 학습 우선순위와 선행 관계 결정
3. 주차별 학습 주제 구조 생성
4. v1에서는 실행 가능한 작업 단위를 일반화된 형태로 배치한다
5. `learning_roadmaps` 헤더 저장
6. `roadmap_weeks` 원본 row 생성
7. 화면 요약 및 snapshot 조립용 `roadmap_payload` 저장
8. 진도 체크 시 `progress_logs`에 append-only 로그 추가
9. 조회 시 최신 로그 기준으로 주차별 현재 상태 계산

### 6.5 출력

로드맵 생성 출력
- `roadmapId`
- `version`
- `totalWeeks`
- `summary`
- `weeks[]`
  - `roadmapWeekId`
  - `weekNumber`
  - `topic`
  - `reason`
  - `tasks[]`
  - `materials[]`
  - `estimatedHours`

로드맵 조회 추가 출력
- `progressStatus`
- `progressNote`
- `progressUpdatedAt`

### 6.6 저장 규칙

- 로드맵 헤더와 JSONB 결과는 `learning_roadmaps`에 저장한다
- 주차별 원본 항목은 `roadmap_weeks`에 저장한다
- 사용자의 진도 상태는 `progress_logs`에 저장한다
- `roadmap_payload`는 원본이 아니며 보조 결과다
- 진도 상태는 `roadmap_payload`가 아니라 `progress_logs` 최신 row 기준으로 계산한다

### 6.7 상태 규칙

- 상태 이력은 update가 아니라 append-only 로그로 남긴다
- `DONE -> TODO` 직접 복귀는 허용하지 않는다
- 화면에는 `weekNumber`를 보여주고 저장은 `roadmapWeekId`로 처리한다

### 6.8 단계 구분

- v1 필수
  - 주차별 계획 생성
  - 추천 이유와 작업 단위 제공
  - 주차별 진도 체크
- v1 확장
  - 시간, 관심 분야 반영 강화
  - 포트폴리오 작성 연결 가이드
- v2 확장
  - Planner 재계획
  - Function Calling 기반 검증 자료 결합

---

## 7. AI 코치 기능

### 7.1 기능명

대화형 학습 코치

### 7.2 목적

사용자의 현재 상태와 로드맵을 바탕으로 일일 학습 실행을 돕는다.

### 7.3 입력

세션 생성 입력
- 없음

메시지 전송 입력
- `sessionId`
- `message`

컨텍스트 입력
- 최신 진단 결과
- 최신 로드맵
- 최근 대화 이력
- 패턴 감지 이벤트

### 7.4 처리

1. 세션 생성 시 `profileVersion`, `roadmapVersion` 고정
2. Context Manager를 통한 Tier 기반 컨텍스트 조회
3. 의도 분류
4. 자연어 응답 생성
5. 필요 시 재분석 또는 재계획 이벤트 발행

### 7.5 출력

- `sessionId`
- `profileVersion`
- `roadmapVersion`
- `responseText`
- `detectedIntent`
- `suggestedTriggers[]`

### 7.6 저장 규칙

- 세션은 `chat_sessions`에 저장한다
- 대화 메시지는 `coach_conversations`에 저장한다
- 세션은 시작 시점의 버전을 고정해서 읽는다
- 새 결과가 생성되어도 기존 세션 기준 버전은 자동 변경하지 않는다

### 7.7 상태 규칙

- 메시지 전송 전 세션이 먼저 생성되어야 한다
- 스트리밍 응답은 SSE를 사용한다
- `detectedIntent`는 계약 부록 enum을 따른다

### 7.8 단계 구분

- v1 필수
  - 포함하지 않음
- v1 확장
  - 포함하지 않음
- v2 확장
  - AI 코치 채팅
  - 재계획 요청
  - 실행 점검 자동화

---

## 8. v2 컨텍스트 및 이벤트 기능

### 8.1 기능명

컨텍스트 조립 및 이벤트 기반 피드백 루프

### 8.2 목적

Analyzer, Planner, Coach가 같은 기준 데이터를 읽고 느슨하게 연결되도록 한다.

### 8.3 입력

- `userId`
- 활성 `profileVersion`
- 활성 `roadmapVersion`
- 최근 대화 및 활동 정보
- Pattern Detector 감지 결과

### 8.4 처리

1. Context Manager가 필요한 Tier만 조립한다
2. 세션 시작 시 읽을 snapshot version을 고정한다
3. Event System은 최소 메타데이터만 담은 notification을 발행한다
4. 구독 컴포넌트는 필요한 시점에 Context Manager를 통해 실제 데이터를 pull 한다

### 8.5 출력

- 조립된 Tier 기반 컨텍스트
- 이벤트 로그
- 재분석 / 재계획 트리거 결과

### 8.6 저장 규칙

- 조립 결과는 `user_context_snapshots`에 저장할 수 있다
- 이벤트 로그는 `agent_events`에 저장한다
- 패턴 감지 결과는 `detected_patterns`에 저장한다

### 8.7 단계 구분

- v1 필수
  - 포함하지 않음
- v1 확장
  - 포함하지 않음
- v2 확장
  - Context Manager
  - Event Notification + Pull
  - Pattern Detector
  - 쿨다운 기반 재계획 제어

---

## 9. 기능 간 연결 규칙

- 프로필은 모든 기능의 시작 입력이다
- GitHub 분석은 실제 사용 기술 프로필을 만든다
- 역량 진단은 프로필과 GitHub 최종 분석 결과를 함께 사용한다
- 학습 로드맵은 진단 결과를 행동 계획으로 연결한다
- 진도 상태 원본은 `progress_logs`다
- AI 코치는 v1 결과를 읽어 실행을 돕는 상위 경험 레이어다
- v2에서는 Context Manager가 v1 결과를 snapshot으로 조립해 에이전트가 재사용한다
- 코딩테스트 분석은 v2 후순위 draft로 남기며 현재 v1 핵심 흐름에는 포함하지 않는다

---

## 10. 구현 시 최종 체크포인트

- `targetRole`은 자유 텍스트가 아니라 `job_roles.role_code` 기준이다
- GitHub 입력은 `githubConnectionId`, `selectedRepositoryIds[]`, `coreRepositoryIds[]` 기준으로 처리한다
- 역량 진단은 `profileId`, `githubAnalysisId`를 함께 사용한다
- 로드맵 생성은 `diagnosisId` 중심으로 처리한다
- `userId`를 요청 body로 받지 않는다
- 로드맵 원본은 `learning_roadmaps + roadmap_weeks + progress_logs`다
- `roadmap_payload`는 원본이 아니라 보조 결과다
- 진도 상태는 append-only 로그 기준으로 계산한다
- 결과 version은 개별 결과 이력 관리용이다
