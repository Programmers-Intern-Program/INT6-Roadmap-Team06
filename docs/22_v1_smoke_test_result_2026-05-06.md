# v1 Full Smoke 결과 - 2026-05-06

## 목적

`fix/v1-smoke-2026-05-06` 브랜치에서 v1 전체 사용자 흐름을 실제 API 호출로 검증한다.
이전 smoke(2026-05-05)에서 blocker였던 저장소 연결 OAuth, LLM schema 불일치를 포함해
새로 발견된 버그를 수정하면서 진행했다.

## 실행 환경

- 기준 브랜치: `fix/v1-smoke-2026-05-06`
- 기준 commit: `621541a`
- DB/Redis: `docker compose -f docker/docker-compose.yml up -d`
- backend: `local,oauth` profile, `http://localhost:8080`
- frontend: `http://localhost:3000`
- smoke 전용 설정: JWT TTL 24시간, LLM read timeout 300s (`application-local.yml`, smoke 후 제거 필요)

민감 정보인 token, cookie, API key, OAuth secret 원문은 기록하지 않았다.

## 사전 검증

| 항목 | 결과 | 기록 |
| --- | --- | --- |
| DB/Redis 실행 | PASS | `coach-db`, `coach-redis` 실행 중 |
| backend health | PASS | `/actuator/health` 200, db/redis UP |
| frontend 실행 | PASS | `/login` 200, Next dev server ready |
| .env 환경변수 | PASS | GitHub 로그인/연결 OAuth App 분리, AI Gateway 설정 완료 |

## Smoke 요약

| 단계 | 결과 | 기록 |
| --- | --- | --- |
| 로그인 / OAuth | PASS | GitHub OAuth callback 정상, JWT 쿠키 설정 |
| 프로필 저장 / 조회 | PASS | BUG-1(targetRole 기본값) 수정 후 성공 |
| GitHub 연결 / 저장소 목록 | PASS | BUG-2(422), BUG-3(duplicate key) 수정 후 성공 |
| GitHub 분석 생성 | PASS | BUG-4(repoId schema), BUG-5(triage skip), synthesis schema 강화 후 성공 |
| GitHub 분석 보정 저장 | 미검증 | 분석 결과 화면 진입 확인, 보정값 저장 단계는 별도 확인 필요 |
| 진단 생성 / 상세 조회 | PASS | `POST /api/diagnoses` 성공, 진단 결과 화면 진입 |
| 로드맵 생성 / 상세 조회 | PASS | BUG-7(LLM 트랜잭션 분리, timeout) 수정 후 성공, 주차별 계획 표시 |
| 진도 저장 / 대시보드 | 미검증 | 로드맵 생성까지 확인, 진도 저장·대시보드 snapshot 반영은 미확인 |

## 주요 ID

| 항목 | 값 | 비고 |
| --- | --- | --- |
| userId | `1` | 로컬 GitHub OAuth 로그인 사용자 |
| profileId | 신규 생성 | `BACKEND_DEVELOPER` 기준 |
| githubAnalysisId | 신규 생성 | 저장소 2개 선택 기준 |
| diagnosisId | 신규 생성 | — |
| roadmapId | 신규 생성 | 8주 이내 제한 기준 |

## 수정된 버그 목록

| 버그 | 증상 | 수정 |
| --- | --- | --- |
| BUG-1 | `profileTargetRole` 기본값 `BACKEND_ENGINEER` → DB 없음 → INVALID_INPUT | `profile-view.tsx` 기본값 `BACKEND_DEVELOPER`로 수정 |
| BUG-2 | `listUserRepos` `affiliation+type` 동시 사용 → GitHub 422 | `type=all` 파라미터 제거 |
| BUG-3 | 연결 재시도 시 `github_projects` duplicate key | `saveAndFlush` + `DataIntegrityViolationException` catch 후 재조회 |
| BUG-4 | `repoId` integer 반환 → schema `string` 기대 → LLM_INVALID_RESPONSE | `repo-summary.schema.json` `["string","integer"]` 허용 |
| BUG-5 | 기여 커밋 없는 저장소 triage 실패 → 분석 전체 중단 | `GithubAnalysisService` triage 실패 시 해당 repo skip |
| BUG-6 | JWT TTL 15분 → 분석 중 인증 만료 | smoke 전용 TTL 24시간 설정 (복원 필요) |
| BUG-7 | 로드맵 LLM 호출이 `@Transactional` 안에서 실행 → DB 커넥션 장시간 점유 → rollback 실패 | `TransactionTemplate`으로 LLM 호출 분리, timeout/max_tokens 설정 |

## 잔여 이슈 및 후속 작업

| 항목 | 유형 | 내용 |
| --- | --- | --- |
| GitHub 분석 보정 저장 | 미검증 | `PATCH /api/github-analyses/{id}/corrections` 호출 확인 필요 |
| 진도 저장 | 미검증 | `POST /api/roadmaps/{id}/progress` 호출 확인 필요 |
| 대시보드 snapshot | 미검증 | 신규 생성 ID 기준으로 snapshot 반영 확인 필요 |
| COMMIT_LIMIT 원복 | 임시 조치 | smoke 목적으로 5로 축소 → 구조 리팩터(이슈 #182) 또는 20 원복 |
| JWT TTL 복원 | 임시 조치 | `application-local.yml` smoke 전용 TTL 제거 필요 |
| LLM URL 검증 | 품질 | 로드맵 자료 URL hallucination 발생, tool calling 기반 검증 필요 (이슈 미생성) |
| 메타데이터 수집 구조 | 근본 수정 | 연결 시 전체 저장소 수집 → 분석 시점으로 이동 (이슈 #182) |
| LLM schema 회귀 테스트 | 품질 | 각 ResponseParser에 schema 위반 패턴 단위 테스트 추가 (이슈 #183) |
| targetRole 입력 UX | 품질 | 자유 입력 → select 드롭다운 (이슈 #184) |

## 결론

v1 핵심 흐름(로그인 → 프로필 → GitHub 연결 → 분석 → 진단 → 로드맵)은 수정을 거쳐 PASS.
진도 저장, 대시보드 snapshot, 분석 보정 저장은 이번 smoke에서 미검증 상태로 남았다.
미검증 3개 구간은 이전 smoke(2026-05-05) 기준 seed 데이터로 PASS가 확인된 바 있어
v1 merge 블로커로 보지 않는다.

## 관련 문서

- [13_v1_smoke_test_checklist.md](13_v1_smoke_test_checklist.md)
- [15_v1_smoke_test_result_2026-05-05.md](15_v1_smoke_test_result_2026-05-05.md)
- [16_v1_smoke_debug_2026-05-06.md](16_v1_smoke_debug_2026-05-06.md)
