# v1 Demo Smoke 결과 - 2026-05-08

## 목적

#258 기준으로 v1 시연 핵심 흐름에서 저장된 결과가 재조회와 대시보드 snapshot까지
안정적으로 이어지는지 확인한다.

이번 검증은 수동 브라우저 smoke가 아니라 자동 테스트 기준 검증이다. GitHub OAuth와
AI Gateway 실환경 값이 필요한 전체 브라우저 smoke는 실행하지 않았다.

## 실행 환경

- 기준 브랜치: `test/v1-demo-smoke-verification`
- 기준 commit: `bd3815b` (`origin/dev` pull 직후)
- 검증 방식: backend 자동 테스트
- 수동 smoke: 미실행

민감 정보인 token, cookie, API key, OAuth secret 원문은 기록하지 않았다.

## 자동 테스트 매핑

| 시연 검증 항목 | 자동 테스트 | 결과 |
| --- | --- | --- |
| GitHub 분석 보정 저장 | `GithubAnalysisControllerTest`, `GithubAnalysisDetailServiceTest` | PASS |
| 진단 상세 재조회 | `DiagnosisDetailControllerTest`, `V1ResultFlowIntegrationTest` | PASS |
| 로드맵 상세 재조회 | `RoadmapDetailControllerTest`, `V1ResultFlowIntegrationTest` | PASS |
| 진도 저장 후 상세 반영 | `V1ResultFlowIntegrationTest`, `RoadmapProgressSnapshotServiceTest` | PASS |
| 대시보드 최신 snapshot 반영 | `V1ResultFlowIntegrationTest`, `DashboardSnapshotServiceTest` | PASS |

## 검증 결과

| 단계 | 결과 | 기록 |
| --- | --- | --- |
| GitHub 분석 보정 저장 | PASS | 보정 저장 응답과 payload 반영이 자동 테스트로 검증됨 |
| 진단 상세 새로고침 재조회 | PASS | 저장된 진단 결과를 사용자 소유권 기준으로 재조회하는 경로가 검증됨 |
| 로드맵 상세 새로고침 재조회 | PASS | 저장된 로드맵과 주차 원본을 상세 응답으로 조립하는 경로가 검증됨 |
| 진도 저장 후 로드맵 상세 반영 | PASS | `progress_logs` 최신 상태가 로드맵 상세 snapshot에 반영됨 |
| 대시보드 snapshot 반영 | PASS | 최신 프로필, GitHub 분석, 진단, 로드맵, 진도 요약 조립이 검증됨 |

## 실행한 명령

```powershell
cd backend
.\gradlew.bat test --tests com.back.coach.domain.flow.V1ResultFlowIntegrationTest --tests com.back.coach.domain.github.controller.GithubAnalysisControllerTest --tests com.back.coach.domain.github.service.GithubAnalysisDetailServiceTest --tests com.back.coach.domain.roadmap.service.RoadmapProgressSnapshotServiceTest --tests com.back.coach.domain.dashboard.service.DashboardSnapshotServiceTest --tests com.back.coach.domain.diagnosis.controller.DiagnosisDetailControllerTest --tests com.back.coach.domain.roadmap.controller.RoadmapDetailControllerTest
```

결과: `BUILD SUCCESSFUL`

## Blocker

자동 테스트 기준 blocker는 없다.

수동 브라우저 smoke는 이번 PR에서 실행하지 않았으므로, 실제 OAuth 승인 화면과 AI Gateway
응답 시간은 별도 시연 리허설에서 확인해야 한다.

## 후속 작업

| 이슈 | 내용 |
| --- | --- |
| #259 | GitHub 분석 보정 저장 계약 테스트를 더 세밀하게 보강 |
| #260 | 진도 저장과 대시보드 snapshot 반영 테스트를 더 세밀하게 보강 |

## 결론

v1 시연 핵심 저장/재조회 구간은 자동 테스트 기준으로 PASS다. 2026-05-06 smoke에서
미검증으로 남았던 GitHub 분석 보정 저장, 진도 저장, 대시보드 snapshot 반영은 이번 검증에서
자동 테스트 커버리지 기준으로 확인했다.
