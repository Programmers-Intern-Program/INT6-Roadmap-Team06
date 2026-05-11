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

## 사전 확인

| 항목 | 결과 | 기록 |
| --- | --- | --- |
| dev 최신화 | PASS | `git pull origin dev` fast-forward 후 smoke 브랜치 생성 |
| DB/Redis 실행 | PASS | `coach-db`, `coach-redis` 실행 |
| backend health | PASS | `/actuator/health` UP |
| frontend 실행 | PASS | `/login` 200 |
| GitHub 로그인 OAuth env | PASS | `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `NEXT_PUBLIC_GITHUB_CLIENT_ID` 존재 |
| GitHub 저장소 연결 전용 env | BLOCKER 후보 | `GITHUB_CONNECTION_CLIENT_ID`, `GITHUB_CONNECTION_CLIENT_SECRET`, `NEXT_PUBLIC_GITHUB_CONNECTION_CLIENT_ID`, `NEXT_PUBLIC_GITHUB_CONNECTION_REDIRECT_URI` 미설정. 코드 fallback으로 로그인 OAuth App 값을 사용함 |

## Smoke 결과

| 단계 | 결과 | 기록 |
| --- | --- | --- |
| 로그인 / OAuth | PASS | GitHub 로그인 화면 진입 후 사용자 인증 완료, `/me`로 복귀 |
| 프로필 저장 / 조회 | PASS | 프로필 저장 성공, 새로고침 후 기술/관심 분야/학습 시간이 유지됨 |
| GitHub 연결 / 저장소 선택 | BLOCKED | GitHub 저장소 연결 OAuth에서 callback URL 불일치 경고 발생 |
| GitHub 분석 생성 / 결과 조회 | 미실행 | GitHub 연결 단계 blocker로 중단 |
| GitHub 분석 보정 저장 | 미실행 | GitHub 분석 미생성 |
| 진단 생성 / 상세 조회 | 미실행 | GitHub 분석 미생성 |
| 로드맵 생성 / 상세 조회 | 미실행 | 진단 미생성 |
| 진도 저장 / 대시보드 snapshot | 미실행 | 로드맵 미생성 |

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

관련 이슈:

- #182: GitHub 연결 단계 metadata fetch 제거
- #254: AI Gateway latency 원인 분석
- #215: Redis 기반 비동기 polling
- #282: 실제 OAuth와 AI Gateway 포함 수동 v1 리허설

## 결론

로그인과 프로필 저장/재조회는 실제 브라우저 smoke 기준으로 PASS다.

GitHub 저장소 연결은 OAuth App callback 설정 불일치로 차단되어, GitHub 분석 이후 AI Gateway 실응답 구간은 아직 검증하지 못했다. #282를 계속 진행하려면 저장소 연결 전용 OAuth env를 먼저 설정한 뒤 같은 체크리스트로 재실행해야 한다.
