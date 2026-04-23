# AI 개발자 성장 코치 서비스 기능 명세 정렬본

## 1. 문서 목적

이 문서는 기존 기능 명세를 DB 물리 스키마 문서, 계약 부록 문서, API 명세 정렬본 기준에 맞춰 팀 개발 계약 수준으로 정렬한 버전이다.

핵심 목적
- 기능별 입력, 처리, 출력, 저장 규칙 해석 차이 제거
- enum, validation, 상태 규칙을 기능 명세 수준에서도 일치시킴
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
- 같은 기능이라도 입력 규칙, 상태 규칙, 저장 규칙은 v1/v2 모두 동일한 계약을 따른다

---

## 3. 프로필 관리 기능

### 3.1 기능명

프로필 생성 및 수정

### 3.2 목적

역량 진단, GitHub 분석, 로드맵 생성의 공통 기준이 되는 사용자 프로필을 저장한다.

### 3.3 입력

- `targetRole`
- `currentLevel`
- `skills[]`
  - `skillName`
  - `proficiencyLevel`
- `interestAreas[]`
- `githubUrl`
- `weeklyStudyHours`
- `targetDate`

입력 규칙
- `targetRole`은 자유 텍스트가 아니라 `job_roles.role_code` 기준 문자열이다
- `currentLevel`, `proficiencyLevel`은 enum 허용값만 사용한다
- `githubUrl`은 선택값이며 GitHub URL 형식만 허용한다

### 3.4 처리

1. 인증 컨텍스트에서 현재 사용자 식별
2. `targetRole`을 `job_roles` 기준으로 검증
3. 프로필 저장 또는 갱신
4. 사용자 입력 기술 스택을 `user_skills`에 `USER_INPUT` source로 저장
5. 관심 분야를 JSON 배열로 저장

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

## 4. 역량 진단 기능

### 4.1 기능명

역량 진단 및 부족 스택 추천

### 4.2 목적

사용자의 목표 직무와 현재 기술 스택을 비교해 부족한 기술과 우선순위를 도출한다.

### 4.3 입력

- `profileId`

입력 규칙
- `profileId`는 현재 로그인 사용자 소유 프로필이어야 한다
- 진단 실행 시 사용자 입력 기술 스택과 프로필 값을 기준으로 한다

### 4.4 처리

1. 프로필 조회
2. `job_roles`, `skill_requirements` 기준표 조회
3. 입력 기술 스택을 카테고리별로 분류
4. 부족 기술 도출
5. severity와 priority 계산
6. 현재 역량 요약 생성
7. 새 결과 row 생성 및 version 증가

### 4.5 출력

- `diagnosisId`
- `version`
- `targetRole`
- `currentLevel`
- `summary`
- `missingSkills[]`
- `strengths[]`
- `recommendations[]`

### 4.6 저장 규칙

- 결과 헤더는 `capability_diagnoses`에 저장한다
- 상세 결과는 `diagnosis_payload` JSONB에 저장한다
- 기존 row update가 아니라 새 row insert 방식으로 저장한다
- `summary`는 JSONB가 아니라 헤더 컬럼에 저장한다

### 4.7 상태 규칙

- v1 외부 계약은 기본 동기 처리다
- 결과 상태 테이블을 따로 두지 않고 성공 시 결과를 반환한다
- 추후 장시간 작업으로 확장할 경우 `job_status` 모델을 따른다

### 4.8 단계 구분

- v1 필수
  - 기본 입력, 비교, 부족 기술 도출
- v1 확장
  - 카테고리별 점수화
  - 관심 분야 반영 강화
- v2 확장
  - 대화 중 재진단 트리거
  - snapshot version 기반 재평가

---

## 5. GitHub 분석 기능

### 5.1 기능명

GitHub 기반 실제 사용 기술 분석

### 5.2 목적

실제 프로젝트 기반 기술 사용 근거를 수집해 역량 진단의 신뢰도를 높인다.

### 5.3 입력

- `githubUrl`
- `diagnosisId` 선택

입력 규칙
- 외부 계약상 입력은 GitHub 계정명 또는 자유 텍스트가 아니라 `githubUrl`로 통일한다
- `diagnosisId`가 있으면 현재 사용자 소유 진단 결과여야 한다

### 5.4 처리

1. GitHub 연결 정보 저장 또는 재사용
2. 저장소 목록, 언어, 설명, README 등 메타데이터 수집
3. 실제 사용 스택 추출
4. 자기기입 스택과 비교
5. 프로젝트별 근거 정리
6. 필요 시 진단 결과 보정 요약 생성
7. 새 결과 row 생성 및 version 증가

### 5.5 출력

- `analysisId`
- `version`
- `extractedSkills[]`
- `repoSummaries[]`
- `evidence[]`
- `comparisonResult`
- `adjustedDiagnosisSummary`

### 5.6 저장 규칙

- 연결 정보는 `github_connections`에 저장한다
- 저장소 메타정보는 `github_projects`에 저장한다
- 분석 실행 결과는 `github_analyses`와 `analysis_payload` JSONB에 저장한다
- `evidence.type`은 enum 허용값만 사용한다

### 5.7 상태 규칙

- v1 외부 계약은 기본 동기 처리다
- 화면 상태는 `입력 전 / 분석 중 / 분석 완료 / 분석 실패`로 표현할 수 있다
- 추후 비동기화 시 내부 상태는 `REQUESTED / RUNNING / SUCCEEDED / FAILED`를 따른다

### 5.8 단계 구분

- v1 필수
  - 저장소 수집, 스택 추출, 비교 결과 표시
- v1 확장
  - 프로젝트별 근거 카드 고도화
  - 근거 신뢰도 표시
- v2 확장
  - Analyzer 이벤트 재실행
  - 이력서 데이터와 통합 분석

---

## 6. 코딩테스트 약점 분석 기능

### 6.1 기능명

코딩테스트 풀이 이력 업로드 및 약점 분석

### 6.2 목적

사용자의 코딩테스트 취약 유형을 파악하고 추천 문제를 제공한다.

### 6.3 입력

업로드 입력
- `submissions[]` 또는 파일

분석 입력
- `uploadId` 선택

제출 항목
- `problemId`
- `problemTitle`
- `problemType`
- `difficulty`
- `isCorrect`
- `attemptNumber`
- `solveTimeSeconds`
- `submittedAt`

입력 규칙
- 분석 실행 시 `userId`는 요청 body로 받지 않는다
- `uploadId`가 있으면 해당 업로드 배치만 분석한다
- `uploadId`가 없으면 현재 사용자의 전체 제출 이력을 분석한다
- `problemType`, `difficulty`는 enum 허용값만 사용한다

### 6.4 처리

1. 제출 이력 저장
2. 업로드 단위 식별을 위해 `uploadId` 부여 가능
3. 유형별 제출 이력 집계
4. 정답률 계산
5. 평균 풀이 시간 계산
6. 재시도 패턴 분석
7. 취약 유형 도출
8. 추천 문제 선택
9. 새 결과 row 생성 및 version 증가

### 6.5 출력

- `analysisId`
- `version`
- `uploadId`
- `stats[]`
- `weakTypes[]`
- `recommendedProblems[]`
- `recommendation`

### 6.6 저장 규칙

- 원본 제출 기록은 `coding_test_submissions`에 저장한다
- 분석 실행 결과는 `coding_test_analyses`와 `analysis_payload` JSONB에 저장한다
- 분석 대상 범위는 `uploadId` 유무로 구분한다

### 6.7 상태 규칙

- v1 외부 계약은 기본 동기 처리다
- 파일 업로드 실패는 형식 오류, 크기 초과, 필수 필드 누락으로 구분한다
- 추후 비동기화 시 내부 상태는 `job_status`를 따른다

### 6.8 단계 구분

- v1 필수
  - 유형별 정답률, 시간, 재시도 분석
- v1 확장
  - 오답노트 요약
  - 난이도별 약점 분석
- v2 확장
  - 반복 실패 패턴 감지
  - Pattern Detector 연동

---

## 7. 학습 로드맵 생성 기능

### 7.1 기능명

개인화 학습 로드맵 생성 및 진도 관리

### 7.2 목적

진단 결과와 약점 결과를 바탕으로 실행 가능한 학습 계획을 생성하고, 주차별 진도를 관리한다.

### 7.3 입력

로드맵 생성 입력
- `diagnosisId`
- `githubAnalysisId` 선택
- `codingTestAnalysisId` 선택
- `weeklyStudyHours` 선택
- `targetDate` 선택

진도 저장 입력
- `roadmapWeekId`
- `status`
- `note`

입력 규칙
- `diagnosisId`는 필수다
- `weeklyStudyHours`, `targetDate`는 프로필 값 override 용도다
- 진도 저장은 `roadmapWeekId` 기준으로 한다
- `status`는 `progress_status` enum만 허용한다

### 7.4 처리

1. 진단 결과 조회
2. 선택된 GitHub 분석, 코테 분석 결과 조회
3. 학습 우선순위 결정
4. 선행학습 관계 반영
5. 주차별 학습 계획 생성
6. `learning_roadmaps` 헤더 저장
7. `roadmap_weeks` 원본 row 생성
8. 화면 요약 및 snapshot 조립용 `roadmap_payload` 저장
9. 진도 체크 시 `progress_logs`에 append-only 로그 추가
10. 조회 시 최신 로그 기준으로 주차별 현재 상태 계산

### 7.5 출력

로드맵 생성 출력
- `roadmapId`
- `version`
- `totalWeeks`
- `summary`
- `weeks[]`
  - `roadmapWeekId`
  - `weekNumber`
  - `topic`
  - `subtopics[]`
  - `resources[]`
  - `estimatedHours`

로드맵 조회 추가 출력
- `progressStatus`
- `progressNote`
- `progressUpdatedAt`

### 7.6 저장 규칙

- 로드맵 헤더와 JSONB 결과는 `learning_roadmaps`에 저장한다
- 주차별 원본 항목은 `roadmap_weeks`에 저장한다
- 사용자의 진도 상태는 `progress_logs`에 저장한다
- `roadmap_payload`는 원본이 아니며 보조 결과다
- 진도 상태는 `roadmap_payload`가 아니라 `progress_logs` 최신 row 기준으로 계산한다

### 7.7 상태 규칙

- 상태 이력은 update가 아니라 append-only 로그로 남긴다
- `DONE -> TODO` 직접 복귀는 허용하지 않는다
- 화면에는 `weekNumber`를 보여주고 저장은 `roadmapWeekId`로 처리한다

### 7.8 단계 구분

- v1 필수
  - 기본 12주 계획 생성
  - 추천 자료 연결
  - 주차별 진도 체크
- v1 확장
  - 시간, 관심 분야 반영 강화
- v2 확장
  - Planner 재계획
  - Function Calling 기반 자료 연결 고도화

---

## 8. AI 코치 기능

### 8.1 기능명

대화형 학습 코치

### 8.2 목적

사용자의 현재 상태와 로드맵을 바탕으로 일일 학습 실행을 돕는다.

### 8.3 입력

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

### 8.4 처리

1. 세션 생성 시 `profileVersion`, `planVersion` 고정
2. Context Manager를 통한 컨텍스트 조회
3. 의도 분류
4. 자연어 응답 생성
5. 필요 시 재분석 또는 재계획 이벤트 발행

### 8.5 출력

- `sessionId`
- `profileVersion`
- `planVersion`
- `responseText`
- `detectedIntent`
- `suggestedTriggers[]`

### 8.6 저장 규칙

- 세션은 `chat_sessions`에 저장한다
- 대화 메시지는 `coach_conversations`에 저장한다
- 세션은 시작 시점의 버전을 고정해서 읽는다
- 새 결과가 생성되어도 기존 세션 기준 버전은 자동 변경하지 않는다

### 8.7 상태 규칙

- 메시지 전송 전 세션이 먼저 생성되어야 한다
- 스트리밍 응답은 SSE를 사용한다
- `detectedIntent`는 계약 부록 enum을 따른다

### 8.8 단계 구분

- v1 필수
  - 포함하지 않음
- v1 확장
  - 포함하지 않음
- v2 확장
  - AI 코치 채팅
  - Daily Quest
  - Streak
  - Activity Heatmap
  - 재계획 요청

---

## 9. 기능 간 연결 규칙

- 프로필은 모든 기능의 시작 입력이다
- 역량 진단은 핵심 기준 결과를 만든다
- GitHub 분석은 역량 진단 보정 기능이다
- 코테 약점 분석은 알고리즘 보완 축이다
- 학습 로드맵은 진단과 약점 결과를 행동 계획으로 연결한다
- 진도 상태 원본은 `progress_logs`다
- AI 코치는 v1 결과를 읽어 실행을 돕는 상위 경험 레이어다
- v2에서는 Context Manager가 v1 결과를 snapshot으로 조립해 에이전트가 재사용한다

---

## 10. 구현 시 최종 체크포인트

- `targetRole`은 자유 텍스트가 아니라 `job_roles.role_code` 기준이다
- `githubUrl` 입력으로 계약을 통일한다
- `userId`를 요청 body로 받지 않는다
- 코테 분석은 `uploadId`가 없으면 전체 제출 이력을 분석한다
- 로드맵 원본은 `learning_roadmaps + roadmap_weeks + progress_logs`다
- `roadmap_payload`는 원본이 아니라 보조 결과다
- 진도 상태는 append-only 로그 기준으로 계산한다
- 결과 version은 개별 결과 이력 관리용이다
