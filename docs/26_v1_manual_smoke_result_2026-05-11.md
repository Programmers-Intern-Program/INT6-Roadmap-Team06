# v1 Manual Smoke 결과 - 2026-05-11

## 목적

#282 기준으로 실제 브라우저에서 GitHub OAuth와 AI Gateway 실응답을 포함한 v1 수동 smoke를 진행했다.

이번 검증은 자동 테스트가 아니라 사용자가 시연에서 밟는 순서를 기준으로 확인했다. 민감 정보인 token, cookie, API key, OAuth secret 원문은 기록하지 않았다.

## 실행 환경

- 기준 브랜치: `test/v1-manual-smoke-282`
- 기준 commit: `ff379bb`
- 검증 방식: 로컬 backend/frontend 실행 + 브라우저 수동 smoke
- backend: `local,oauth` profile, `http://localhost:8080`
- frontend: `http://localhost:3000`
- DB/Redis: `docker compose -f docker/docker-compose.yml up -d`
- 후속 재검증 코드: `fix/llm-synthesis-evidence-type-288` 로컬 실행본
- 후속 재검증 PR: #289

## 사전 확인

| 항목 | 결과 | 기록 |
| --- | --- | --- |
| dev 최신화 | PASS | `git pull origin dev` fast-forward 후 smoke 브랜치 생성 |
| DB/Redis 실행 | PASS | `coach-db`, `coach-redis` 실행 |
| backend health | PASS | `/actuator/health` UP |
| frontend 실행 | PASS | `/login` 200 |
| GitHub 로그인 OAuth env | PASS | `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `NEXT_PUBLIC_GITHUB_CLIENT_ID` 존재 |
| GitHub 저장소 연결 전용 env | BLOCKER 후보 | `GITHUB_CONNECTION_CLIENT_ID`, `GITHUB_CONNECTION_CLIENT_SECRET`, `NEXT_PUBLIC_GITHUB_CONNECTION_CLIENT_ID`, `NEXT_PUBLIC_GITHUB_CONNECTION_REDIRECT_URI` 미설정. 코드 fallback으로 로그인 OAuth App 값을 사용함 |

## 재실행 전 보정

저장소 연결 전용 GitHub OAuth App을 새로 만들고 callback URL을 `http://localhost:3000/github/callback`로 설정했다.

로컬 실행 전용 env에 아래 키를 추가했다. 실제 client secret, token, cookie, API key 원문은 기록하지 않는다.

- root `.env`: `GITHUB_CONNECTION_CLIENT_ID`, `GITHUB_CONNECTION_CLIENT_SECRET`
- `frontend/.env.local`: `NEXT_PUBLIC_GITHUB_CONNECTION_CLIENT_ID`, `NEXT_PUBLIC_GITHUB_CONNECTION_REDIRECT_URI`

프론트 `/github` 연결 URL이 로그인용 OAuth App이 아니라 저장소 연결 전용 OAuth App client id를 사용하는 것을 확인했다.

## Smoke 결과

| 단계 | 결과 | 기록 |
| --- | --- | --- |
| 로그인 / OAuth | PASS | GitHub 로그인 화면 진입 후 사용자 인증 완료, `/me`로 복귀 |
| 프로필 저장 / 조회 | PASS | 프로필 저장 성공, 새로고침 후 기술/관심 분야/학습 시간이 유지됨 |
| GitHub 연결 / 저장소 선택 | PASS | 전용 OAuth App 설정 후 승인 화면 진입, callback 성공, 저장소 36개 목록 표시 |
| GitHub 분석 생성 / 결과 조회 | PASS | #288 fix 적용 후 `githubAnalysisId=2` 결과 화면 도달. AI Gateway 응답 약 45.0초 |
| GitHub 분석 보정 저장 | PASS | `Spring Boot` 사용자 보정 1개 저장, 화면 재조회에서 보정 개수 1개 표시 |
| 진단 생성 / 상세 조회 | PASS | `diagnosisId=3` 생성 및 상세 화면 도달. AI Gateway 응답 약 71.7초 |
| 로드맵 생성 / 상세 조회 | PASS | `roadmapId=2` 생성 및 8주차 상세 화면 도달. AI Gateway 응답 약 233.0초 |
| 진도 저장 / 대시보드 snapshot | PASS | 1주차 상태를 `진행 중`으로 저장, 대시보드에서 예정 7주 / 진행 1주 / 완료 0주 표시 |

## Blocker

```text
단계: GitHub 연결 / 저장소 선택
화면 경로: /github → GitHub OAuth authorize
관련 ID: 기록하지 않음
요청: GitHub 저장소 연결 OAuth 시작
응답 status/body: GitHub OAuth 승인 전 경고 화면
화면 증상: redirect_uri가 해당 OAuth App에 연결되어 있지 않다는 GitHub 경고가 표시됨
backend 로그: 연결 callback까지 도달하지 않아 관련 backend 처리 없음
frontend 콘솔/Network: GitHub authorize URL의 redirect_uri가 http://localhost:3000/github/callback 으로 전달됨
재현 조건: 저장소 연결 전용 OAuth env가 없는 상태에서 로그인 OAuth App client id fallback 사용
판단: 로그인용 OAuth App callback은 backend 로그인 callback 기준이고, 저장소 연결 callback은 frontend /github/callback 기준이라 OAuth App 설정이 맞지 않음
후속 조치: 저장소 연결 전용 GitHub OAuth App env를 설정하거나, 기존 OAuth App callback 설정과 프론트 redirect_uri 정책을 맞춘 뒤 #282 smoke 재실행
```

상태: 2026-05-11 재실행에서 저장소 연결 전용 OAuth App과 로컬 env 설정으로 해소됨.

## Blocker 2

```text
단계: GitHub 분석 생성 / 결과 조회
화면 경로: /github
선택 저장소: qkrqhdtn3/project-2-backend
요청: 분석 실행
응답 status/body: LLM_INVALID_RESPONSE / LLM 응답을 해석할 수 없습니다.
화면 증상: "LLM 응답을 해석할 수 없습니다." 메시지와 다시 연결하기 버튼 표시
backend 로그: AI Gateway 요청 완료 후 Synthesis 응답 schema 위반
backend 상세: $.evidences[2].type 값이 ["README", "CODE", "CONFIG", "REPO_METADATA", "COMMIT"] enum에 없음
AI Gateway latency: 약 35.6초
추가 관찰: 선택 저장소 분석 전 "메타데이터 없음"으로 repo 분석이 skip되고 synthesis prompt만 생성됨
판단: OAuth blocker는 해소됐지만, 실제 AI Gateway 응답 schema 위반으로 v1 핵심 분석 흐름이 중단됨
후속 조치: 기존 LLM schema/AI Gateway 안정화 이슈에 연결하거나 별도 blocker로 분리 후 #282 smoke 재실행
```

관련 이슈:

- #288: GitHub 분석 synthesis evidence type schema 위반 수정
- #182: GitHub 연결 단계 metadata fetch 제거
- #254: AI Gateway latency 원인 분석
- #215: Redis 기반 비동기 polling
- #282: 실제 OAuth와 AI Gateway 포함 수동 v1 리허설

상태: #288 수정 로컬 실행본으로 재검증하여 해소됨. 최종 저장/응답 payload에서 evidence type은 기존 enum 값으로 유지됨.

## 후속 관찰

- GitHub 분석, 진단, 로드맵 생성은 모두 완료됐지만 AI Gateway 응답 시간이 길다.
- 관찰된 LLM 응답 시간은 GitHub synthesis 약 45.0초, 진단 약 71.7초, 로드맵 약 233.0초다.
- 진행 불가 blocker는 아니지만 시연 UX 리스크이므로 #254, #215와 연결해 추적한다.
- Codex in-app browser 자동 텍스트 입력은 virtual clipboard 문제로 실패했다. 앱 기능 blocker로 보지 않고, 보정/로드맵 입력값은 사용자가 직접 입력해 검증했다.

## 결론

로그인, 프로필 저장/재조회, GitHub 저장소 연결/저장소 목록 조회, GitHub 분석 생성/조회, 보정 저장/재조회, 진단 생성/조회, 로드맵 생성/조회, 진도 저장, 대시보드 확인까지 실제 브라우저 smoke 기준으로 PASS다.

기존 GitHub 분석 synthesis schema blocker는 #288로 분리해 수정했고, 로컬 재검증에서 해소됐다.

남은 리스크는 기능 중단이 아니라 긴 동기 대기 시간이다. GitHub 분석/진단/로드맵 생성 중 브라우저가 장시간 대기 상태로 보이는 문제는 #254, #215 범위에서 계속 추적한다.
