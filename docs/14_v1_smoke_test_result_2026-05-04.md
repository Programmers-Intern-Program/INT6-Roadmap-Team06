# v1 Smoke 검증 결과 - 2026-05-04

## 목적

v1 사용자 흐름을 로컬 환경에서 실제 실행해 통과 지점과 blocker를 기록한다. 이 결과는 `docs/13_v1_smoke_test_checklist.md`의 기준에 맞춰 작성했다.

## 실행 환경

- 기준 브랜치: `dev` 최신화 후 `test/v1-smoke-run-2026-05-04`
- DB/Redis: `docker compose -f docker/docker-compose.yml up -d`
- backend: `local,oauth` profile, `http://localhost:8080`
- frontend: `http://localhost:3000`
- GitHub OAuth env: 설정됨
- AI Gateway env: `AI_GATEWAY_API_KEY`, `AI_GATEWAY_BASE_URL`, `AI_GATEWAY_MODEL` 설정됨
- frontend local env: `NEXT_PUBLIC_API_BASE_URL`, `NEXT_PUBLIC_GITHUB_CLIENT_ID` 필요

민감 정보인 토큰, 쿠키, API key, OAuth secret 원문은 기록하지 않았다.

## 요약

| 단계 | 결과 | 기록 |
| --- | --- | --- |
| DB/Redis 실행 | PASS | `coach-db`, `coach-redis` 컨테이너 정상 실행 |
| backend health | PASS | `/actuator/health` 200, db/redis UP |
| frontend 실행 | PASS | Next dev server `http://localhost:3000` ready |
| 로그인 화면 진입 | PASS | `/login`에서 GitHub 로그인 버튼 확인 |
| GitHub OAuth 로그인 | PASS | #165 merge 후 실제 GitHub OAuth callback 성공, `/me`에서 `userId=2`, `authProvider=GITHUB` 확인 |
| 인증 API 확인 | PASS | smoke용 local JWT로 `/api/v1/auth/me` 200 |
| 프로필 저장/조회 | PASS | `profileId=1`, 저장 후 `/api/profiles/me` 재조회 성공 |
| 대시보드 재검증 | PASS | frontend env 보강 후 `/`에서 404 없이 신규 GitHub user 기준 empty snapshot 렌더링 |
| GitHub 연결/저장소 수집 | BLOCKER | repo scope 연결 OAuth가 GitHub `Invalid Redirect URI`로 중단 |
| GitHub 분석 조회/보정 | PASS | seed된 `githubAnalysisId=1` 조회, 보정 저장 후 재조회 성공 |
| 진단 생성 | BLOCKER | AI Gateway 응답 파싱 실패, `LLM_INVALID_RESPONSE` |
| 진단 상세 조회 | PASS | seed된 `diagnosisId=1` 상세 조회 성공 |
| 로드맵 상세 조회 | PASS | seed된 `roadmapId=1`, `roadmapWeekId=1` 상세 조회 성공 |
| 진도 저장 | PASS | `progressLogId=1`, `DONE` 저장 후 로드맵 상세 재조회 반영 |
| 대시보드 snapshot | PASS | 최신 profile/githubAnalysis/diagnosis/roadmap/progress 요약 반영 |

## 주요 ID

| 항목 | 값 | 비고 |
| --- | --- | --- |
| userId | `1` | smoke 전용 local user |
| oauthUserId | `2` | #165 이후 실제 GitHub OAuth 로그인 사용자 |
| profileId | `1` | API로 저장 |
| githubConnectionId | `1` | OAuth blocker 이후 tail 검증용 seed |
| repoId | `1` | tail 검증용 seed |
| githubAnalysisId | `1` | tail 검증용 seed |
| diagnosisId | `1` | AI blocker 이후 tail 검증용 seed |
| roadmapId | `1` | tail 검증용 seed |
| roadmapWeekId | `1` | tail 검증용 seed |
| progressLogId | `1` | API로 저장 |

## 상세 결과

### 1. 로그인 / OAuth

- `/login` 화면은 정상 렌더링됐다.
- GitHub OAuth 시작 버튼은 확인됐다.
- 최초 실행에서는 Codex in-app browser가 OAuth 이동 또는 클릭 시 `failed to start codex app-server` 오류로 중단되어 GitHub 승인 화면까지 검증하지 못했다.
- #165 merge 후 `http://localhost:3000/login`에서 실제 GitHub OAuth 로그인을 재검증했다.
- GitHub callback은 backend에서 정상 처리됐고 `/me`에서 `userId=2`, `authProvider=GITHUB` 응답을 확인했다.
- 따라서 #143에서 의심하던 로그인 OAuth callback 실패는 현재 smoke 기준으로 재현되지 않는다.

### 1-1. frontend local env 보강

- frontend dev server에 `NEXT_PUBLIC_API_BASE_URL`이 없어 dashboard가 backend 대신 Next dev server의 `/api/dashboard`를 호출했고 `API request failed with status 404`가 발생했다.
- ignored local 파일인 `frontend/.env.local`에 `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`을 추가하고 frontend를 재시작하자 `/` dashboard 404는 사라졌다.
- `/github` 연결 버튼은 `NEXT_PUBLIC_GITHUB_CLIENT_ID`가 없으면 `client_id=`가 빈 OAuth URL을 만든다.
- root `.env`의 GitHub client id와 같은 값을 `NEXT_PUBLIC_GITHUB_CLIENT_ID`로 추가하고 frontend를 재시작하자 `/github` 연결 URL의 `client_id`가 채워지는 것을 확인했다.
- 값 원문은 문서에 남기지 않았다.

### 2. 프로필 저장 / 조회

- smoke용 local user와 access token으로 `/api/v1/auth/me`가 200을 반환했다.
- `/api/profiles` 저장 성공 후 `profileId=1`을 받았다.
- `/api/profiles/me` 재조회에서 `targetRole=BACKEND_DEVELOPER`, `currentLevel=BASIC`, `weeklyStudyHours=8`, `targetDate=2026-06-30`이 유지됐다.

### 3. GitHub 분석 조회 / 보정

- 로그인 OAuth는 #165 이후 통과했다.
- 별도 repo scope 연결 OAuth는 GitHub OAuth App 설정 문제로 중단됐다.
- `/github`의 `GitHub 연결하기` 클릭 시 GitHub가 `Invalid Redirect URI` 페이지를 반환했다.
- 요청 redirect URI는 frontend callback인 `http://localhost:3000/github/callback`이며, 현재 GitHub OAuth App에 등록된 callback과 맞지 않는 것으로 판단된다.
- 따라서 저장소 목록 조회와 실제 GitHub 분석 실행은 이번 재검증에서도 완료하지 못했다.
- 이후 저장/재조회 흐름 검증을 위해 local DB에 GitHub 분석 payload를 seed했다.
- `/api/github-analyses/1` 조회 성공.
- `/api/github-analyses/1/corrections` 저장 성공.
- 재조회에서 `finalTechProfile.confirmedSkills=[Java, Spring Boot, SQL]`, `userCorrectionCount=1` 기준 데이터가 유지됐다.

### 4. 진단 생성

- `/api/diagnoses` 호출은 AI Gateway 응답 수신 후 backend에서 `LLM_INVALID_RESPONSE`로 종료됐다.
- backend log:

```text
traceId=23b831b0-5d4f-4c1f-a071-eb334e476fe3
Diagnosis 응답 JSON 파싱 실패: Unexpected character ('`' (code 96)): expected a valid value
ServiceException: code=LLM_INVALID_RESPONSE, message=LLM 응답을 해석할 수 없습니다.
```

판단:
- AI Gateway 연결 자체는 응답을 받은 것으로 보인다.
- 응답이 순수 JSON이 아니라 Markdown code fence 같은 문자로 시작했을 가능성이 있다.
- 진단/로드맵 생성 프롬프트 또는 parser 쪽에서 fenced JSON 제거, structured output 강제, gateway 응답 shape 확인이 필요하다.

### 5. 로드맵 / 진도 / 대시보드

- 진단 생성 blocker 이후 tail 검증을 위해 local DB에 진단과 로드맵을 seed했다.
- `/api/diagnoses/1` 상세 조회 성공.
- `/api/roadmaps/1` 상세 조회 성공, 1주차 기본 상태는 `TODO`.
- `/api/roadmaps/1/progress`로 `roadmapWeekId=1`, `status=DONE` 저장 성공.
- `/api/roadmaps/1` 재조회에서 1주차 `progressStatus=DONE`, `progressNote=Smoke progress update` 확인.
- `/api/dashboard`에서 최신 snapshot이 다음 값으로 반영됐다.
  - profile: `profileId=1`
  - githubAnalysis: `githubAnalysisId=1`, `userCorrectionCount=1`
  - diagnosis: `diagnosisId=1`
  - roadmap: `roadmapId=1`
  - progress: `totalWeeks=1`, `doneWeeks=1`

### 6. #165 이후 대시보드 재검증

- 실제 GitHub OAuth 사용자 `userId=2` 기준 `/` dashboard가 404 없이 렌더링됐다.
- 신규 OAuth user에는 저장된 프로필, GitHub 분석, 진단, 로드맵이 없어 각 카드가 empty state로 표시됐다.
- 이 결과는 데이터 부재에 따른 정상 empty snapshot이며, API 라우팅 실패는 아니다.

## Blocker

### Resolved. 로그인 OAuth callback 실패

```text
단계: 로그인 / OAuth
화면 경로: /login -> /oauth2/authorization/github
이전 증상: Codex in-app browser 오류로 승인 완료 여부 미확정
재검증: #165 merge 후 실제 GitHub OAuth callback 성공, /me 인증 JSON 확인
판단: 로그인 OAuth blocker는 해결로 기록
후속: #143은 #165 해결 사항과 smoke 재검증 결과를 근거로 정리 가능
```

### B1. GitHub 저장소 연결 OAuth redirect URI 불일치

```text
단계: GitHub 연결 / 저장소 수집
화면 경로: /github -> GitHub 연결하기
증상: GitHub가 Invalid Redirect URI 반환
확인: NEXT_PUBLIC_GITHUB_CLIENT_ID 설정 후에도 redirect_uri=http://localhost:3000/github/callback 에서 중단
판단: GitHub OAuth App에 frontend callback URI가 등록되지 않았거나, 백엔드 로그인 OAuth용 callback만 등록된 상태로 보임
후속: GitHub OAuth App callback 등록 또는 저장소 연결 flow를 백엔드 callback 계약으로 통일 필요
```

### B2. 진단 생성 LLM 응답 파싱 실패

```text
단계: 진단 생성
API: POST /api/diagnoses
관련 ID: profileId=1, githubAnalysisId=1
backend traceId: 23b831b0-5d4f-4c1f-a071-eb334e476fe3
응답/로그: LLM_INVALID_RESPONSE, 응답 첫 문자가 ` 로 시작
판단: AI Gateway 응답이 parser가 기대하는 순수 JSON이 아닌 형태로 반환된 것으로 보임
재검증: repo scope GitHub 연결 blocker 때문에 실제 GitHub 분석 생성 이후 경로로는 재실행하지 못함
후속: LLM 응답 shape 확인과 parser/프롬프트 보정 필요
```

## 결론

v1 저장/재조회 tail 흐름은 local seed 기준으로 통과했다. 프로필 저장, GitHub 분석 보정 저장, 진단/로드맵 상세 재조회, 진도 저장, 대시보드 최신 snapshot 반영은 확인됐다.

#165 이후 로그인 OAuth와 신규 OAuth user 기준 dashboard 렌더링은 통과했다. 남은 전체 사용자 흐름 blocker는 저장소 연결용 GitHub OAuth redirect URI 불일치와 AI Gateway JSON 응답 파싱 실패다.
