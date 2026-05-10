# v1 Smoke 검증 체크리스트

## 목적

v1 시연 핵심 흐름을 같은 기준으로 수동 검증하기 위한 체크리스트다. 프로필 저장부터 대시보드 재조회까지 사용자 순서대로 확인하고, 실패하면 어느 단계에서 끊겼는지 같은 양식으로 기록한다.

## 검증 전 준비

- [ ] `dev` 브랜치를 최신화한 뒤 smoke 전용 브랜치를 만든다.
- [ ] 루트 `.env`에 backend 실행에 필요한 값을 채운다. 민감 정보 원문은 문서나 PR에 기록하지 않는다.
- [ ] `docker compose -f docker/docker-compose.yml up -d`로 PostgreSQL과 Redis를 실행한다.
- [ ] backend를 `local,oauth` profile로 실행한다.
  - 명령: `cd backend; .\gradlew.bat bootRun --args="--spring.profiles.active=local,oauth"`
  - 로그인 OAuth App callback: `http://localhost:8080/login/oauth2/code/github`
  - 저장소 연결 OAuth App callback: `http://localhost:3000/github/callback`
- [ ] frontend `.env.local`이 backend와 저장소 연결 OAuth App을 가리키는지 확인한다.
  - `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`
  - `NEXT_PUBLIC_GITHUB_CONNECTION_CLIENT_ID`
  - `NEXT_PUBLIC_GITHUB_CONNECTION_REDIRECT_URI=http://localhost:3000/github/callback`
- [ ] frontend를 실행한다.
  - 명령: `cd frontend; npm run dev`
- [ ] 브라우저 DevTools Network와 backend 로그를 열어 요청 URL, status, trace/log 메시지를 바로 기록할 수 있게 둔다.

## 완료 기준

- [ ] 프로필 저장 후 새로고침/재진입해도 입력값이 유지된다.
- [ ] GitHub 연결 후 저장소 목록을 조회하고 분석 대상을 선택할 수 있다.
- [ ] GitHub 분석 생성 후 실제 `githubAnalysisId`가 붙은 결과 화면으로 이동한다.
- [ ] GitHub 분석 보정값 저장 후 재조회해도 보정 내용이 유지된다.
- [ ] 진단 생성 후 실제 `diagnosisId` 기반 상세 화면으로 이동한다.
- [ ] 로드맵 생성 후 실제 `roadmapId` 기반 상세 화면으로 이동한다.
- [ ] 진도 저장 후 로드맵 상세와 대시보드 최신 snapshot에 반영된다.
- [ ] 401 응답은 로그인 CTA 또는 로그인 화면으로 복구 가능하고, 일반 API 오류와 구분된다.
- [ ] 실패한 단계가 있으면 blocker 양식에 요청, 응답, 화면 경로, 관련 ID, 로그를 기록한다.

## v1 흐름 체크리스트

### 1. 로그인 / 인증 복구

- [ ] `/login`에서 GitHub OAuth 로그인을 시작한다.
- [ ] GitHub 승인 후 frontend로 돌아온다.
- [ ] `/me` 또는 보호 화면에서 인증 쿠키 기반 요청이 401 없이 동작한다.
- [ ] 인증 만료나 쿠키 없음 상태에서는 로그인 CTA가 표시되고, 재로그인 후 원래 화면으로 돌아오는지 확인한다.
- [ ] blocker 기록값: redirect URL, error query, 응답 status, backend 로그의 주요 메시지.

### 2. 프로필 저장 / 조회

- [ ] `/profile`에서 목표 직무, 현재 수준, 기술 스택, 관심 분야, 주당 학습 가능 시간, 목표 날짜를 입력한다.
- [ ] 저장 성공 후 저장 완료 상태가 표시되는지 확인한다.
- [ ] 새로고침 또는 `/profile` 재진입 후 저장한 프로필 값이 유지되는지 확인한다.
- [ ] blocker 기록값: `profileId`, 요청 payload, 응답 status/message.

### 3. GitHub 연결 / 저장소 선택

- [ ] `/github`에서 GitHub 저장소 연결 OAuth를 시작한다.
- [ ] `/github/callback` 이후 저장소 선택 화면으로 돌아오는지 확인한다.
- [ ] 저장소 목록이 조회되는지 확인한다.
- [ ] 분석 대상 저장소를 1개 이상 선택한다.
- [ ] 선택값이 분석 실행 전까지 유지되는지 확인한다.
- [ ] blocker 기록값: `githubConnectionId`, 선택한 repository id 목록, callback query, 응답 status/message.

### 4. GitHub 분석 생성 / 결과 조회

- [ ] GitHub 분석 실행 버튼을 클릭한다.
- [ ] 성공 시 `/github/analysis?githubAnalysisId=...`로 이동한다.
- [ ] 분석 결과 화면에서 정적 신호, repo 요약, 기술 태그, 근거, 최종 기술 프로필이 표시되는지 확인한다.
- [ ] 새로고침 후에도 같은 `githubAnalysisId` 결과를 조회할 수 있는지 확인한다.
- [ ] blocker 기록값: `githubAnalysisId`, 분석 실행 요청/응답, 실패한 표시 섹션, backend 분석 로그.

### 5. GitHub 분석 보정 저장

- [ ] GitHub 분석 결과 화면에서 사용자 보정값과 최종 기술 프로필을 수정한다.
- [ ] 보정 저장 후 성공 상태가 표시되는지 확인한다.
- [ ] 새로고침 후 보정값이 유지되는지 확인한다.
- [ ] blocker 기록값: `githubAnalysisId`, 보정 요청 payload, 재조회 응답의 보정 필드.

### 6. 진단 생성 / 상세 조회

- [ ] GitHub 분석 결과 화면 또는 `/diagnoses`의 다음 행동에서 진단 생성을 실행한다.
- [ ] 성공 시 `/diagnoses/{diagnosisId}`로 이동한다.
- [ ] 부족 기술, 강점, 우선순위, 추천 이유가 표시되는지 확인한다.
- [ ] 새로고침 후 같은 `diagnosisId`를 재조회할 수 있는지 확인한다.
- [ ] blocker 기록값: `profileId`, `githubAnalysisId`, `diagnosisId`, 진단 생성 요청/응답.

### 7. 로드맵 생성 / 상세 조회

- [ ] `/roadmaps/new` 또는 진단 상세 화면에서 로드맵 생성을 실행한다.
- [ ] 성공 시 `/roadmaps/{roadmapId}`로 이동한다.
- [ ] 주차별 학습 주제, 작업, 자료, 진도 상태가 표시되는지 확인한다.
- [ ] 새로고침 후 같은 `roadmapId`를 재조회할 수 있는지 확인한다.
- [ ] blocker 기록값: `diagnosisId`, `roadmapId`, 로드맵 생성 요청/응답.

### 8. 진도 저장 / 대시보드 snapshot

- [ ] 로드맵 상세에서 한 주차의 진도를 `TODO`, `IN_PROGRESS`, `DONE`, `SKIPPED` 중 하나로 저장한다.
- [ ] 저장 후 로드맵 상세의 해당 주차 상태와 메모가 바뀌는지 확인한다.
- [ ] `/`에서 최신 프로필, GitHub 분석, 진단, 로드맵, 진도 요약이 표시되는지 확인한다.
- [ ] 대시보드 최신 snapshot이 방금 생성한 결과 ID 기준으로 보이는지 확인한다.
- [ ] blocker 기록값: `roadmapId`, `roadmapWeekId`, 저장한 상태/메모, 대시보드 응답의 최신 ID.

## Blocker 기록 양식

```text
단계:
화면 경로:
관련 ID:
요청:
응답 status/body:
화면 증상:
backend 로그:
frontend 콘솔/Network:
재현 조건:
판단:
후속 조치:
```
