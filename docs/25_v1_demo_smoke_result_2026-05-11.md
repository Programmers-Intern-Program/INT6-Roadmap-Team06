# v1 Demo Smoke 결과 - 2026-05-11

## 목적

#276 기준으로 v1 시연 핵심 흐름의 수동 smoke 기준을 최신화하고, 현재 브랜치에서 자동 검증으로 확인 가능한 범위를 기록한다.

이번 검증은 브라우저에서 실제 GitHub OAuth 승인과 AI Gateway 실응답까지 확인하는 full smoke가 아니다. 시연 전 같은 순서로 점검할 수 있도록 `docs/13_v1_smoke_test_checklist.md`를 기준으로 정리하고, 코드 회귀 여부는 자동 검증 명령으로 확인했다.

## 실행 환경

- 기준 브랜치: `test/v1-demo-smoke-check-276`
- 기준 commit: `a1404a6` (`origin/dev` pull 직후)
- 검증 방식: 문서 기준 정리 + frontend/backend 자동 검증
- 수동 브라우저 smoke: 미실행
- Docker 상태: Docker Engine `28.5.1` 접근 가능

민감 정보인 token, cookie, API key, OAuth secret 원문은 기록하지 않았다.

## 자동 검증 결과

| 검증 항목 | 명령 | 결과 |
| --- | --- | --- |
| Frontend lint | `cd frontend; npm run lint` | PASS |
| Frontend build | `cd frontend; npm run build` | PASS |
| Backend PR 검증 | `cd backend; .\gradlew.bat prVerification --no-daemon` | PASS |
| Backend 통합 테스트 | `cd backend; .\gradlew.bat integrationTest --no-daemon` | PASS |

## Smoke 체크리스트 정리 결과

| 구간 | 정리 내용 | 결과 |
| --- | --- | --- |
| 로컬 실행 전제 | backend/frontend 실행 명령, Docker, OAuth callback, frontend env 확인 항목 정리 | 완료 |
| v1 핵심 흐름 | 프로필, GitHub 연결/분석, 보정, 진단, 로드맵, 진도, 대시보드 순서로 확인 기준 정리 | 완료 |
| 인증 복구 | 401 응답을 일반 API 오류와 구분하고 재로그인 복구 여부를 확인 항목에 추가 | 완료 |
| blocker 기록 | 요청, 응답 status/body, 화면, 관련 ID, backend 로그, frontend Network 기록 기준 보강 | 완료 |

## 미실행 범위

| 항목 | 상태 | 이유 |
| --- | --- | --- |
| 실제 GitHub OAuth 승인 | 미실행 | 외부 계정 승인과 로컬 민감 env가 필요한 수동 리허설 영역 |
| 실제 GitHub API 저장소 데이터 | 미실행 | 계정/저장소 상태에 따라 결과가 달라지는 외부 연동 영역 |
| AI Gateway 실응답 기반 분석/진단/로드맵 생성 | 미실행 | API key와 모델 응답 시간이 필요한 수동 리허설 영역 |

## Blocker

자동 검증 기준 blocker는 없다.

수동 브라우저 smoke는 이번 PR에서 실행하지 않았으므로, 실제 OAuth 승인 화면, GitHub 저장소 권한, AI Gateway 응답 시간과 출력 품질은 시연 전 별도 리허설에서 확인해야 한다.

## 결론

현재 브랜치 기준으로 프론트 빌드, 백엔드 PR 검증, Testcontainers 통합 테스트는 모두 PASS다. v1 시연 핵심 흐름의 수동 smoke 기준과 blocker 기록 양식은 최신 화면 흐름 기준으로 정리했다.
