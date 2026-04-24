# AGENTS.md

이 문서는 이 저장소에서 작업하는 AI 에이전트와 개발자가 공유할 기본 작업 지침이다.

## 언어와 문서 기준

- 사용자-facing 문서, 이슈 정리, 설계 설명은 한국어로 작성한다.
- 코드 식별자, API 필드명, DB 컬럼명은 팀 규칙이 없으면 영어를 우선한다.
- 커밋 메시지는 `<type>(<scope>): <summary>` 형식을 사용한다.
- 커밋 메시지의 `type`, `scope`는 영어로 작성하고, `summary`는 한글로 작성한다.
- 커밋은 가능한 한 작업 단위별로 바로 남긴다.
- 커밋은 되돌리기 쉽고 리뷰하기 쉬운 의미 단위로 나눈다.
- CI, 문서, 템플릿, 기능 구현처럼 성격이 다른 변경은 가능하면 분리 커밋한다.
- 단, 템플릿 파일 하나마다 따로 커밋하는 식으로 과하게 잘게 쪼개지는 않는다.
- 커밋 메시지 예시: `chore(ci): 백엔드 CI 워크플로우와 GitHub 템플릿 추가`
- 기능을 설명할 때는 `v1 필수`, `v1 확장`, `v2 확장` 범위를 명확히 구분한다.
- 구현 범위가 애매하면 `v1 필수`를 먼저 닫고, 확장 기능은 별도 작업으로 분리한다.

## 공통 문서와 개인 문서

`docs/`는 팀 공통 설계 문서 공간이다. 구현, API 계약, DB 스키마, 화면 흐름을 판단할 때는 먼저 `docs/00_bundle_index.md`의 권장 읽기 순서를 따른다.

공통 문서 목록은 다음과 같다.

- `docs/00_bundle_index.md`: 문서 묶음 안내와 권장 읽기 순서
- `docs/01_service_design_aligned.md`: 서비스 범위, 단계 전략, 문서 우선순위
- `docs/02_data_model_aligned.md`: 논리 데이터 모델
- `docs/03_api_spec_aligned.md`: 외부 API 계약
- `docs/04_function_spec_aligned.md`: 기능별 처리 책임
- `docs/05_architecture_aligned.md`: 아키텍처와 저장 원본 기준
- `docs/06_requirements_aligned.md`: 요구사항과 성공 기준
- `docs/07_user_flow_screen_design_aligned.md`: 사용자 흐름과 화면 설계
- `docs/08_db_physical_schema.md`: DB 물리 스키마
- `docs/09_contract_appendix.md`: enum, JSONB shape, validation, 상태 전이

`.local/`은 개인 문서, 임시 메모, 실험용 초안 공간이다. `.local/`의 내용은 git에 올리지 않는다. 팀 전체가 공유해야 하는 결정은 `.local/`에 남기지 말고 `docs/`, `AGENTS.md`, 또는 `README.md`로 옮긴다.

## 문서 우선순위

공통 문서가 충돌할 경우 아래 기준을 따른다.

1. 서비스 범위와 단계 전략: `docs/01_service_design_aligned.md`
2. 요구사항: `docs/06_requirements_aligned.md`
3. 저장 구조: `docs/08_db_physical_schema.md`
4. enum, JSONB shape, validation, 상태 전이: `docs/09_contract_appendix.md`
5. 외부 인터페이스: `docs/03_api_spec_aligned.md`
6. 기능별 처리 책임: `docs/04_function_spec_aligned.md`
7. 화면 입력과 흐름: `docs/07_user_flow_screen_design_aligned.md`

상위 문서는 방향과 범위를 정하고, 하위 문서는 구현 계약을 닫는다. 구현 충돌이 발생하면 더 구체적인 계약 문서를 우선한다.

## 서비스 개요

이 프로젝트는 AI 개발자 성장 코치 서비스다. 사용자의 현재 역량, 목표 직무, GitHub 활동, 선택 자료를 바탕으로 부족 역량을 진단하고 주차별 학습 로드맵을 제공한다.

핵심 흐름은 다음과 같다.

1. 목표 직무, 현재 수준, 기술 스택, 선택 자료 입력
2. GitHub OAuth 연동 및 저장소 선택
3. GitHub 정적 분석과 핵심 repo 요약
4. 사용자 보정과 GitHub 최종 분석 생성
5. GitHub 최종 분석 기반 역량 진단
6. 진단 결과 기반 학습 로드맵 생성
7. 결과 저장, 재조회, 진도 체크

## 단계별 범위

v1은 페이지 기반 MVP다. 사용자가 입력하고 결과를 저장, 조회, 갱신하는 기능 흐름을 먼저 완성한다.

- 프로필 저장
- GitHub OAuth 연동과 저장소 선택
- GitHub 분석과 사용자 보정
- 역량 진단 입력/결과
- 학습 로드맵 생성
- 진도 체크와 결과 재조회

v1 확장은 MVP가 안정된 뒤 선택적으로 다룬다.

- 마이페이지 또는 대시보드
- GitHub 근거 카드 고도화
- 포트폴리오 / 프로젝트 기술서 초안 작성 도우미
- 추천 근거 시각화 강화

v2는 v1 결과를 재사용하는 멀티 에이전트 확장이다. v1 구현 중에는 v2 구조를 과도하게 선반영하지 않는다.

- Analyzer, Planner, Coach 역할 분리
- Context Manager 기반 상태 관리
- Event System 기반 피드백 루프
- Pattern Detector, Summarizer
- Function Calling 기반 검증 자료 결합
- Daily Quest, Streak, Activity Heatmap
- 코딩테스트 분석 draft

## 기술 스택 방향

- Frontend: Next.js, React, TypeScript
- Backend: Java 17+, Spring Boot
- Database/Cache: PostgreSQL, Redis
- External API: GitHub API
- AI: GLM API 또는 동급 LLM API, Structured Output, 필요 시 Function Calling
- v1 async: 단순 API 파이프라인과 작업 상태 조회
- v2 async: 이벤트 시스템과 멀티 에이전트 확장

## 아키텍처 원칙

- v1은 모노리스 기반의 페이지 + API 파이프라인 구조로 시작한다.
- 초기에 이벤트 구조나 멀티 에이전트 프레임워크를 과하게 넣지 않는다.
- PostgreSQL을 원본 저장소로 두고, Redis는 캐시와 임시 작업 상태 저장에 사용한다.
- 로드맵 원본은 `learning_roadmaps`, `roadmap_weeks`, `progress_logs` 기준으로 관리한다.
- 진단, GitHub 분석, 로드맵은 결과 이력 관리를 위해 `version`을 유지한다.
- 장시간 분석은 처음부터 복잡한 이벤트 구조로 만들지 말고, 작업 상태 조회 또는 비동기 처리로 확장 가능하게 둔다.
- v2의 Context Manager는 v1 원본 데이터와 결과 이력을 조합해 snapshot을 만든다. snapshot은 원본을 대체하지 않는다.

## 도메인 기준

주요 도메인은 다음 경계를 유지한다.

- Profile: 목표 직무, 현재 수준, 기술 스택, 관심 분야, 학습 가능 시간
- GitHub Analysis: 저장소 메타데이터, 실제 사용 기술, 근거, 분석 결과
- Diagnosis: 직무 기준 대비 부족 기술과 우선순위
- Roadmap: 주차별 학습 계획, 추천 자료, 진도 체크
- Coach/Agent: v2 이후 대화, 패턴 감지, 재분석, 재계획
- Coding Test Analysis: v2 후순위 draft

## API 기준

- 인증은 JWT 또는 OAuth 기반 액세스 토큰을 전제로 한다.
- 요청/응답은 JSON을 기본으로 한다.
- 공통 에러 응답은 `code`, `message`, `details`를 포함한다.
- 공개 API와 내부 이벤트를 혼동하지 않는다.
- API 계약을 바꾸면 관련 화면, DTO, 테스트, 문서를 함께 갱신한다.

## 구현 우선순위

1. 공통 엔티티, DTO, API 계약 합의
2. GitHub 연동 / 분석 흐름
3. 역량 진단 입력/결과 흐름
4. 학습 로드맵 생성 및 진도 체크
5. 저장/재조회, 예외 처리, 기본 테스트
6. v1 확장 또는 v2 준비 작업
7. 코딩테스트 draft 검토

## 프론트엔드 지침

- 첫 화면은 실제 사용 흐름으로 진입하게 만들고, 마케팅 랜딩 페이지처럼 구성하지 않는다.
- 진단, 분석, 로드맵 화면은 사용자가 반복적으로 확인하는 작업 도구처럼 밀도 있게 구성한다.
- 입력, 결과, 근거, 다음 행동이 화면에서 명확히 분리되어야 한다.
- 화면 텍스트는 기능 설명보다 사용자의 현재 상태와 다음 행동을 드러내는 데 집중한다.
- API 응답 스키마와 화면 상태를 느슨하게 추측하지 말고 계약을 기준으로 구현한다.

## 백엔드 지침

- 서비스 계층은 GitHub 분석, 진단, 로드맵 생성을 분리한다.
- 외부 API, LLM 호출, 파일 업로드는 인터페이스 뒤에 둔다.
- 분석 결과는 재조회와 버전 관리를 고려해 저장한다.
- 민감 정보는 로그, 예외 메시지, 응답에 노출하지 않는다.
- 선택 자료 업로드가 있으면 크기, 형식, 파싱 실패를 검증한다.

## AI/LLM 지침

- LLM 응답은 가능하면 Structured Output 형태로 검증한다.
- 프롬프트는 도메인 규칙과 출력 스키마를 분리해 관리한다.
- LLM 결과를 그대로 신뢰하지 말고 필수 필드, enum, 범위 값을 검증한다.
- 사용자의 학습 상태나 추천 근거를 만들 때는 입력 데이터와 추론 결과를 구분한다.

## 테스트와 검증

- 핵심 도메인 계산, API 계약, 외부 연동 어댑터에는 테스트를 둔다.
- GitHub API, LLM API, 파일 업로드처럼 실패 가능성이 높은 경계는 실패 케이스를 함께 검증한다.
- v1 MVP에서는 넓은 E2E보다 핵심 흐름의 통합 테스트와 계약 테스트를 우선한다.
- 변경 후 가능한 범위에서 빌드, 타입 체크, 테스트를 실행하고 결과를 공유한다.

## 작업 방식

- 기존 변경 사항을 임의로 되돌리지 않는다.
- 기능 추가와 리팩터링은 가능하면 분리한다.
- 문서와 구현이 충돌하면 구현 전에 어느 쪽을 기준으로 삼을지 명확히 한다.
- `docs/` 변경은 공통 계약 변경으로 보고 관련 구현, 테스트, 화면, API 문서를 함께 확인한다.
- `.local/`의 개인 문서는 자유롭게 바꿀 수 있지만, 팀이 공유해야 하는 결정은 추적 대상 문서로 옮긴다.
