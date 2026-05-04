# v1 Smoke 검증 체크리스트

## 목적

v1 전체 사용자 흐름을 같은 기준으로 수동 검증하기 위한 체크리스트다. 각 단계는 화면 이동, 주요 ID, 저장 후 재조회 여부, blocker 기록을 함께 확인한다.

## 검증 전 준비

- [ ] `dev` 브랜치를 최신화한다.
- [ ] PostgreSQL과 Redis를 로컬에서 실행한다.
- [ ] backend를 local profile로 실행한다.
- [ ] frontend를 실행하고 `NEXT_PUBLIC_API_BASE_URL`이 backend 주소를 가리키는지 확인한다.
- [ ] GitHub OAuth, GitHub API, AI Gateway 관련 환경변수가 실제 검증 가능한 값인지 확인한다.
- [ ] #134, #139, #143은 팀원 담당 의존성으로만 본다. 이 문서는 해당 이슈 해결을 포함하지 않는다.

## 완료 기준

- [ ] 프로필 저장 후 재조회가 된다.
- [ ] GitHub 연결 후 저장소 목록을 조회할 수 있다.
- [ ] GitHub 분석 생성 후 결과 화면에서 분석 ID를 확인할 수 있다.
- [ ] GitHub 분석 보정값 저장 후 재조회해도 보정 내용이 유지된다.
- [ ] 진단 생성 후 상세 화면으로 이동하고 진단 ID를 확인할 수 있다.
- [ ] 로드맵 생성 후 상세 화면으로 이동하고 로드맵 ID를 확인할 수 있다.
- [ ] 진도 저장 후 로드맵 상세와 대시보드 최신 snapshot에 반영된다.
- [ ] 실패한 단계가 있으면 blocker 항목에 요청, 응답, 화면 경로, 관련 ID를 기록한다.

## v1 흐름 체크리스트

### 1. 로그인 / OAuth

- [ ] `/login` 또는 시작 화면에서 GitHub OAuth 로그인을 시작한다.
- [ ] GitHub 승인 후 frontend로 돌아온다.
- [ ] 인증 쿠키가 설정되어 보호 API 요청이 401 없이 동작한다.
- [ ] 실패 시 blocker에 redirect URL, error query, backend 로그의 주요 메시지를 기록한다.

### 2. 프로필 저장 / 조회

- [ ] `/profile`에서 목표 직무, 현재 수준, 기술 스택, 관심 분야, 주당 학습 가능 시간을 입력한다.
- [ ] 저장 성공 후 화면에 `profileId` 또는 저장 완료 상태가 표시되는지 확인한다.
- [ ] 새로고침 또는 재진입 후 저장한 프로필 값이 유지되는지 확인한다.
- [ ] blocker 기록값: `profileId`, 요청 payload, 응답 message.

### 3. GitHub 연결 / 저장소 선택

- [ ] `/github`에서 authorization code로 GitHub 연결을 등록한다.
- [ ] 연결 성공 후 `githubConnectionId`와 GitHub login을 확인한다.
- [ ] 저장소 목록이 조회되는지 확인한다.
- [ ] 분석 대상 저장소를 1개 이상 선택한다.
- [ ] 핵심 repo를 1개 이상 선택한다.
- [ ] blocker 기록값: `githubConnectionId`, 선택한 `selectedRepositoryIds`, `coreRepositoryIds`.

### 4. GitHub 분석 생성 / 결과 조회

- [ ] GitHub 분석 실행 버튼을 클릭한다.
- [ ] 성공 시 `/github/analysis?githubAnalysisId=...`로 이동한다.
- [ ] 분석 결과 화면에서 정적 신호, repo 요약, 기술 태그, 근거, 최종 기술 프로필이 표시되는지 확인한다.
- [ ] 새로고침 후에도 같은 `githubAnalysisId` 결과를 조회할 수 있는지 확인한다.
- [ ] blocker 기록값: `githubAnalysisId`, 분석 실행 응답, 실패한 표시 섹션.

### 5. GitHub 분석 보정 저장

- [ ] GitHub 분석 결과 화면에서 사용자 보정값과 최종 기술 프로필을 수정한다.
- [ ] 보정 저장 후 성공 상태가 표시되는지 확인한다.
- [ ] 새로고침 후 보정값이 유지되는지 확인한다.
- [ ] blocker 기록값: `githubAnalysisId`, 보정 요청 payload, 재조회 응답의 보정 필드.

### 6. 진단 생성 / 상세 조회

- [ ] GitHub 분석 결과 화면 또는 연결된 다음 행동에서 진단 생성을 실행한다.
- [ ] 성공 시 `/diagnoses/{diagnosisId}`로 이동한다.
- [ ] 부족 기술, 강점, 우선순위, 추천 이유가 표시되는지 확인한다.
- [ ] 새로고침 후 같은 `diagnosisId`를 재조회할 수 있는지 확인한다.
- [ ] blocker 기록값: `profileId`, `githubAnalysisId`, `diagnosisId`, 진단 생성 응답.

### 7. 로드맵 생성 / 상세 조회

- [ ] `/roadmaps/new` 또는 진단 상세 화면에서 로드맵 생성을 실행한다.
- [ ] 성공 시 `/roadmaps/{roadmapId}`로 이동한다.
- [ ] 주차별 학습 목표, 주제, 자료, 진도 상태가 표시되는지 확인한다.
- [ ] 새로고침 후 같은 `roadmapId`를 재조회할 수 있는지 확인한다.
- [ ] blocker 기록값: `diagnosisId`, `githubAnalysisId`, `roadmapId`, 로드맵 생성 응답.

### 8. 진도 저장 / 대시보드 snapshot

- [ ] 로드맵 상세에서 한 주차의 진도를 `TODO`, `IN_PROGRESS`, `DONE`, `SKIPPED` 중 하나로 저장한다.
- [ ] 저장 후 로드맵 상세의 해당 주차 상태가 바뀌는지 확인한다.
- [ ] `/` 또는 대시보드 화면에서 최신 프로필, GitHub 분석, 진단, 로드맵, 진도 요약이 표시되는지 확인한다.
- [ ] 대시보드 최신 snapshot이 방금 생성한 결과 ID 기준으로 보이는지 확인한다.
- [ ] blocker 기록값: `roadmapId`, `roadmapWeekId`, 저장한 상태, 대시보드 응답의 최신 ID.

## Blocker 기록 양식

```text
단계:
화면 경로:
관련 ID:
요청:
응답:
화면 증상:
backend 로그:
판단:
```
