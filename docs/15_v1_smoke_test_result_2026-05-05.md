# v1 Full Smoke 재검증 결과 - 2026-05-05

## 목적

#167 저장소 연결 OAuth 설정 분리와 #169 fenced JSON 파서 보정이 `dev`에 merge된 뒤, v1 전체 사용자 흐름을 다시 확인한다. 이전 smoke에서 seed로 이어간 구간은 가능한 한 실제 API 호출로 재검증한다.

## 실행 환경

- 기준 브랜치: `dev` 최신화 후 `test/v1-full-smoke-2026-05-05`
- 기준 commit: `3440599`
- DB/Redis: `docker compose -f docker/docker-compose.yml up -d`
- backend: `local,oauth` profile, `http://localhost:8080`
- frontend: `http://localhost:3000`
- backend/frontend 로그: `.local/smoke-logs/2026-05-05/`
- 기존 DB/Redis volume은 유지했다.

민감 정보인 token, cookie, API key, OAuth secret 원문은 기록하지 않았다.

## 사전 검증

| 항목 | 결과 | 기록 |
| --- | --- | --- |
| DB/Redis 실행 | PASS | `coach-db`, `coach-redis` 실행 중 |
| backend health | PASS | `/actuator/health` 200, db/redis UP |
| frontend 실행 | PASS | `/login` 200, Next dev server ready |
| backend targeted test | PASS | `LlmJsonResponseExtractorTest`, `ResponseParserTest`, `RestGithubApiClientTest` |
| frontend lint | PASS | `npm run lint` |
| frontend build | PASS | `npm run build` |
| repository OAuth env | BLOCKER | connection 전용 env 이름이 로컬에 설정되지 않아 fallback client id 기준으로만 확인 가능 |

## Smoke 요약

| 단계 | 결과 | 기록 |
| --- | --- | --- |
| 인증 API 확인 | PASS | 로컬 JWT로 `/api/v1/auth/me` 200, `userId=1`, `authProvider=GITHUB` |
| 프로필 재조회 | PASS | 기존 DB 기준 `profileId=1`, `targetRole=BACKEND_DEVELOPER` |
| GitHub 연결/저장소 수집 | BLOCKER | CLI 환경에서 GitHub 웹 승인 완료 불가, connection 전용 frontend env 미설정으로 full OAuth 재검증 보류 |
| 진단 생성 | BLOCKER | AI Gateway 응답 수신 후 JSON 파싱은 진행됐으나 schema 검증 실패 |
| 로드맵 생성 | NOT RUN | 진단 생성 실패로 신규 `diagnosisId`가 없어 실행하지 않음 |
| 진도 저장 | NOT RUN | 신규 `roadmapId`가 없어 실행하지 않음 |
| 대시보드 snapshot | PASS | 기존 seed 기준 `profileId=1`, `diagnosisId=1`, `roadmapId=1` 조회 |

## 주요 ID

| 항목 | 값 | 비고 |
| --- | --- | --- |
| userId | `1` | 로컬 JWT smoke 사용자 |
| profileId | `1` | 기존 DB 데이터 |
| githubAnalysisId | `1` | 기존 DB 데이터, 진단 생성 입력 |
| 기존 diagnosisId | `1` | 대시보드 seed 확인용 |
| 기존 roadmapId | `1` | 대시보드 seed 확인용 |

## 상세 결과

### 1. 로컬 실행

- DB/Redis는 기존 volume 유지 상태로 정상 실행됐다.
- backend는 `local,oauth` profile로 기동했고 Flyway는 migration 추가 없이 최신 상태였다.
- `/actuator/health`에서 db/redis 모두 `UP`을 확인했다.
- frontend는 `http://localhost:3000`에서 ready 상태였고 `/login`이 200을 반환했다.

### 2. GitHub 저장소 연결 OAuth

- `frontend/.env.local`에는 `NEXT_PUBLIC_GITHUB_CLIENT_ID` fallback 값은 있지만, #167에서 추가한 `NEXT_PUBLIC_GITHUB_CONNECTION_CLIENT_ID`, `NEXT_PUBLIC_GITHUB_CONNECTION_REDIRECT_URI`는 설정돼 있지 않았다.
- root `.env`에도 `GITHUB_CONNECTION_CLIENT_ID`, `GITHUB_CONNECTION_CLIENT_SECRET`가 설정돼 있지 않았다.
- 따라서 이번 smoke는 저장소 연결 전용 OAuth App이 실제로 적용된 상태를 확인하지 못했다.
- GitHub 웹 승인 과정은 CLI만으로 완료할 수 없어 `/github/callback` 이후 `POST /api/github/connections`, `GET /api/github/repositories`까지는 재검증하지 못했다.

판단:
- #167 코드 변경은 merge됐지만, 현재 로컬 env가 README 기준과 맞지 않아 full smoke 완료 조건을 만족하지 못한다.
- repository OAuth 재검증은 connection 전용 OAuth App client/secret/client id를 로컬 env에 채운 뒤 브라우저로 다시 실행해야 한다.

### 3. AI Gateway 진단 생성

- 기존 DB의 `profileId=1`, `githubAnalysisId=1`로 `POST /api/diagnoses`를 호출했다.
- AI Gateway 응답은 도착했고, 이전처럼 첫 문자 백틱에서 JSON 파싱이 실패하지는 않았다.
- backend log:

```text
traceId=7e161817-a534-46dd-b5a6-d04881a3f7f3
Diagnosis 응답 schema 위반:
$.missingSkills[0]: 필수 속성 'skillName'을(를) 찾을 수 없습니다.
$.missingSkills[0]: 필수 속성 'reason'을(를) 찾을 수 없습니다.
$.missingSkills[0]: 'skill' 속성이 스키마에 정의되어 있지 않으며 스키마가 추가 속성을 허용하지 않습니다.
ServiceException: code=LLM_INVALID_RESPONSE, message=LLM 응답을 해석할 수 없습니다.
```

판단:
- #169의 fenced JSON parser 보정은 기존 “첫 문자 ` 때문에 파싱 실패” blocker를 한 단계 넘긴 것으로 보인다.
- 남은 문제는 LLM 출력 schema가 backend schema와 맞지 않는 것이다.
- 특히 `missingSkills[].skill`을 `missingSkills[].skillName`으로 강제하고, `reason` 필드를 반드시 포함하도록 prompt 또는 gateway structured output 계약을 보강해야 한다.

### 4. 대시보드 snapshot

- 진단 생성 실패 이후 신규 로드맵/진도는 생성하지 못했다.
- 기존 seed 데이터 기준 `/api/dashboard`는 200을 반환했다.
- 반환 snapshot은 `profileId=1`, `diagnosisId=1`, `roadmapId=1` 기준으로 유지됐다.

## Blocker

### B1. GitHub repository OAuth local env 미설정

```text
단계: GitHub 연결 / 저장소 수집
화면 경로: /github -> GitHub 연결하기
증상: connection 전용 OAuth env가 없어 저장소 연결 전용 OAuth App 적용 여부를 확인하지 못함
확인: NEXT_PUBLIC_GITHUB_CONNECTION_CLIENT_ID, NEXT_PUBLIC_GITHUB_CONNECTION_REDIRECT_URI, GITHUB_CONNECTION_CLIENT_ID, GITHUB_CONNECTION_CLIENT_SECRET 미설정
판단: #167 코드 merge만으로는 full smoke가 끝나지 않으며, 로컬 env에 connection OAuth App 값을 채운 뒤 브라우저 승인 재검증 필요
후속: README 기준 env 설정 후 /github/callback -> POST /api/github/connections -> GET /api/github/repositories 재검증
```

### B2. AI Gateway 진단 응답 schema 불일치

```text
단계: 진단 생성
API: POST /api/diagnoses
관련 ID: profileId=1, githubAnalysisId=1
backend traceId: 7e161817-a534-46dd-b5a6-d04881a3f7f3
응답/로그: LLM_INVALID_RESPONSE, missingSkills[].skillName/reason 누락, skill 추가 필드
판단: fenced JSON 파싱 문제는 재현되지 않았고, LLM 출력 schema 불일치로 blocker가 이동함
후속: 진단 prompt 또는 structured output 계약에서 missingSkills[].skillName, severity, reason, priorityOrder를 강제
```

## 결론

이번 재검증은 full PASS가 아니다. backend/frontend 기동, health, targeted test, frontend lint/build, 인증 API, 프로필 재조회, 기존 대시보드 snapshot은 통과했다.

#167 repository OAuth는 로컬 env가 준비되지 않아 실제 연결 완료까지 검증하지 못했다. #169 fenced JSON parser blocker는 첫 문자 백틱 파싱 실패에서 schema 검증 실패로 진행 지점이 바뀌었고, 후속 작업은 진단 LLM 출력 schema 정렬이다.
