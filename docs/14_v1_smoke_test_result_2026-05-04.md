# v1 Smoke 검증 결과 - 2026-05-04

## 목적

v1 사용자 흐름을 로컬 환경에서 실제 실행해 통과 지점과 blocker를 기록한다. 이 결과는 `docs/13_v1_smoke_test_checklist.md`의 기준에 맞춰 작성했다.

## 실행 환경

- 기준 브랜치: `dev` 최신화 후 `test/v1-smoke-run-2026-05-04`
- DB/Redis: `docker compose -f docker/docker-compose.yml up -d`
- backend: `local,oauth` profile, `http://localhost:8080`
- frontend: `http://127.0.0.1:3000`
- GitHub OAuth env: 설정됨
- AI Gateway env: `AI_GATEWAY_API_KEY`, `AI_GATEWAY_BASE_URL`, `AI_GATEWAY_MODEL` 설정됨

민감 정보인 토큰, 쿠키, API key, OAuth secret 원문은 기록하지 않았다.

## 요약

| 단계 | 결과 | 기록 |
| --- | --- | --- |
| DB/Redis 실행 | PASS | `coach-db`, `coach-redis` 컨테이너 정상 실행 |
| backend health | PASS | `/actuator/health` 200, db/redis UP |
| frontend 실행 | PASS | Next dev server `http://127.0.0.1:3000` ready |
| 로그인 화면 진입 | PASS | `/login`에서 GitHub 로그인 버튼 확인 |
| GitHub OAuth 브라우저 승인 | BLOCKER | Codex in-app browser가 OAuth 이동/클릭 시 플러그인 오류로 중단 |
| 인증 API 확인 | PASS | smoke용 local JWT로 `/api/v1/auth/me` 200 |
| 프로필 저장/조회 | PASS | `profileId=1`, 저장 후 `/api/profiles/me` 재조회 성공 |
| GitHub 연결/저장소 수집 | BLOCKER | OAuth 브라우저 승인 blocker 때문에 실제 GitHub 연결 생성 미실행 |
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
- Codex in-app browser가 OAuth 이동 또는 클릭 시 `failed to start codex app-server` 오류로 중단되어 GitHub 승인 화면까지 검증하지 못했다.
- #143의 실제 재현 여부는 이번 실행만으로 확정할 수 없다.

### 2. 프로필 저장 / 조회

- smoke용 local user와 access token으로 `/api/v1/auth/me`가 200을 반환했다.
- `/api/profiles` 저장 성공 후 `profileId=1`을 받았다.
- `/api/profiles/me` 재조회에서 `targetRole=BACKEND_DEVELOPER`, `currentLevel=BASIC`, `weeklyStudyHours=8`, `targetDate=2026-06-30`이 유지됐다.

### 3. GitHub 분석 조회 / 보정

- 실제 GitHub OAuth가 막혀 GitHub connection과 repository 수집은 실행하지 못했다.
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

## Blocker

### B1. OAuth 브라우저 승인 미완료

```text
단계: 로그인 / OAuth
화면 경로: /login -> /oauth2/authorization/github
증상: Codex in-app browser가 OAuth 이동/클릭 시 플러그인 오류로 중단
판단: 제품 코드 실패로 단정할 수 없고, 테스트 도구 blocker로 기록
후속: 사용자의 실제 브라우저 또는 안정적인 브라우저 자동화 환경에서 #143 재검증 필요
```

### B2. 진단 생성 LLM 응답 파싱 실패

```text
단계: 진단 생성
API: POST /api/diagnoses
관련 ID: profileId=1, githubAnalysisId=1
backend traceId: 23b831b0-5d4f-4c1f-a071-eb334e476fe3
응답/로그: LLM_INVALID_RESPONSE, 응답 첫 문자가 ` 로 시작
판단: AI Gateway 응답이 parser가 기대하는 순수 JSON이 아닌 형태로 반환된 것으로 보임
후속: LLM 응답 shape 확인과 parser/프롬프트 보정 필요
```

## 결론

v1 저장/재조회 tail 흐름은 local seed 기준으로 통과했다. 프로필 저장, GitHub 분석 보정 저장, 진단/로드맵 상세 재조회, 진도 저장, 대시보드 최신 snapshot 반영은 확인됐다.

전체 사용자 흐름의 실제 성공 여부는 OAuth 브라우저 승인과 AI Gateway JSON 응답 blocker가 해결된 뒤 다시 확인해야 한다.
