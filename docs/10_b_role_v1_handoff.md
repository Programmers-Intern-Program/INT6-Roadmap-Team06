# B 역할 v1 마감 체크

## 1. 목적

이 문서는 PR #94 merge 기준으로 박봉수(B) 담당 v1 범위의 완료 상태와 후속 의존성을 정리한다.

B 역할의 v1 핵심은 새 분석/진단/로드맵을 생성하는 알고리즘이 아니라, 결과를 안정적으로 저장하고 다시 조회하며 진도와 대시보드 snapshot을 일관되게 구성하는 것이다.

## 2. 완료 범위

| 범위 | 완료 상태 | 기준 |
| --- | --- | --- |
| v1 저장 기반 | 완료 | `users`, `user_profiles`, `github_analyses`, `capability_diagnoses`, `learning_roadmaps`, `roadmap_weeks`, `progress_logs` 등 핵심 저장 구조와 Repository 기반 정리 |
| 공통 global 기반 | MVP 기준 완료 | 응답, 에러, enum/code, JPA base, JWT 보안 골격, traceId logging, Swagger JWT 설정 구성 |
| OpenAPI/Swagger 계약 | 완료 | `docs/openapi.yml` 정적 계약을 Swagger UI에 연결하고 구현 상태를 `implemented/planned`로 구분 |
| 결과 version/latest 규칙 | 완료 | GitHub 분석, 진단, 로드맵 latest 기준을 `version desc, created_at desc`로 고정하고 인덱스와 테스트 추가 |
| GitHub 분석 재조회/보정 | 완료 | 저장된 `analysis_payload` 조회, `userCorrections`/`finalTechProfile` 보정 저장, 보정 규칙 문서화 |
| 진단 결과 재조회 | 완료 | 저장된 `capability_diagnoses` payload를 소유권 기준으로 조회 |
| 로드맵 상세/진도 | 완료 | 로드맵 원본 주차와 최신 progress snapshot 조회, progress append-only 저장 |
| 대시보드 snapshot | 완료 | 최신 프로필, GitHub 분석, 진단, 로드맵, 진도 요약과 GitHub 보정 요약 반환 |

## 3. 후속 작업 경계

J 역할 또는 후속 기능 작업에서 이어받을 항목은 아래 기준을 그대로 사용한다.

- 로그인/OAuth 구현은 `AuthenticatedUser.userId()`를 보호 API의 사용자 식별 기준으로 사용한다.
- GitHub 분석 생성은 새 실행마다 `github_analyses`에 새 `version` row를 추가한다.
- GitHub 분석 생성 payload는 `staticSignals`, `repoSummaries`, `techTags`, `depthEstimates`, `evidences`, `userCorrections`, `finalTechProfile` shape를 따른다.
- GitHub 보정 저장은 새 version을 만들지 않고 기존 payload의 `userCorrections`, `finalTechProfile`만 갱신한다.
- 진단 생성은 프로필과 GitHub 최종 분석 결과를 기준으로 새 `capability_diagnoses` version row를 만든다.
- 로드맵 생성은 새 `learning_roadmaps` version row와 `roadmap_weeks` 원본 row를 함께 만든다.
- 진도 변경은 `progress_logs` append-only로 저장하고 기존 row를 수정하지 않는다.
- 대시보드는 화면 편의용 snapshot이며, 각 결과의 상세 API와 원본 저장 구조를 대체하지 않는다.

## 4. 남은 의존성

| 영역 | 담당 성격 | 메모 |
| --- | --- | --- |
| 이메일 로그인/JWT 발급 | J 중심 | B 구현 API는 인증 principal이 준비됐다는 전제로 동작 |
| GitHub OAuth/저장소 수집 | J 중심 | B 저장 테이블과 조회 API는 준비됨 |
| GitHub 실제 분석 생성 | J 중심 | 생성 시 B가 정리한 payload/version 규칙 사용 |
| 역량 진단 생성 | J 중심 | 저장된 프로필과 GitHub 최종 분석 결과를 입력으로 사용 |
| 로드맵 생성 | J 중심 | 생성 결과는 `learning_roadmaps + roadmap_weeks` 원본 구조에 저장 |
| 프론트 화면 연결 | 프론트 후속 | OpenAPI 계약과 구현 API 기준으로 연결 |

## 5. Global standard 상태

`Global standard 클래스 전체 이식/정리`는 현재 MVP 기준으로 완료로 본다.

현재 구현된 기반:
- `global/response`: 공통 성공/에러 응답
- `global/exception`: 공통 에러 코드와 예외 처리
- `global/jpa`: 공통 Entity와 auditing 설정
- `global/security`: JWT 보안 골격, 인증 사용자, 소유권 검증
- `global/logging`: traceId logging filter

외부 프로젝트의 standard 클래스를 그대로 전체 이식하는 작업은 v1 필수 마감 조건이 아니다. 추후 코드 스타일이나 보안 정책을 더 엄격히 맞출 때 별도 리팩터링 후보로 다룬다.

## 6. 마감 판단

B 역할 v1 범위는 저장 구조, 결과 이력, 진도 상태, 재조회 API, 대시보드 snapshot 기준으로 마감 가능하다.

후속 작업자는 새 생성 로직을 붙일 때 이 문서의 경계와 `docs/03_api_spec_aligned.md`, `docs/08_db_physical_schema.md`, `docs/09_contract_appendix.md`의 계약을 우선 기준으로 사용한다.
