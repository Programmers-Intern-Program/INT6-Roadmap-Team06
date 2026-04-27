# AI 개발자 성장 코치 서비스 아키텍처 문서

## 1. 문서 목적

이 문서는 팀 구현 기준에서 아키텍처 책임, 저장 원본, v1/v2 경계를 명확히 하기 위한 정렬본이다.
상세 컬럼 계약은 DB 물리 스키마 문서를 따르고, enum, JSONB shape, validation, 상태 전이는 계약 부록 문서를 따른다.

## 2. 아키텍처 목표

- v1은 GitHub 중심 MVP 흐름을 빠르게 구현할 수 있어야 한다.
- v2는 v1 결과를 재사용하는 상위 확장 구조여야 한다.
- 원본 데이터, 캐시, 결과 이력, 대화용 snapshot 책임을 분리해야 한다.
- 팀원이 같은 저장 원본과 상태 전이를 기준으로 구현할 수 있어야 한다.

## 3. v1 아키텍처

### 3.1 구조 개요

v1은 모노리스 기반의 페이지 + API 파이프라인 구조다.

구성 요소
- 프론트엔드
  - 프로필 / 이력서 / 포트폴리오 자료 입력 화면
  - GitHub OAuth 연동 및 저장소 선택 화면
  - GitHub 분석 결과 및 사용자 보정 화면
  - 역량 진단 결과 화면
  - 로드맵 생성 / 조회 / 진도 관리 화면
- 백엔드 API 서버
  - 프로필 처리
  - GitHub 연동 및 저장소 메타데이터 수집
  - GitHub 정적 분석
  - 핵심 repo 요약
  - 역량 진단
  - 로드맵 생성
  - 진도 저장
- PostgreSQL
  - 영속 원본 데이터 저장
- Redis
  - 세션 캐시
  - 조회 캐시
  - 임시 작업 상태 저장
- LLM 연동 모듈
  - 핵심 repo 요약
  - 역량 진단 요약
  - 로드맵 생성
- Context Manager (최소 형태)
  - LLM 호출에 필요한 컨텍스트 조립 및 전달만 담당

v1 백엔드는 Coach, Pattern Detector, Event System을 포함하지 않는다. 이들은 모두 v2에서 추가되는 컴포넌트다.

Context Manager는 v1에서 최소 형태로 도입할 수 있다. v1에서는 별도 로직 없이 LLM 호출에 필요한 컨텍스트(프로필, 진단 결과, 로드맵 등)를 조립해 전달하는 단순 조립 클래스로 구현한다. v2에서 Tier 구조, 캐싱, 버전 관리를 얹는 방식으로 점진적으로 확장한다.

### 3.2 저장 원본 기준

v1 저장 원본 기준은 다음과 같다.

- PostgreSQL이 시스템의 원본 저장소다.
- Redis는 캐시와 임시 상태 저장용 보조 계층이다.
- 로드맵 원본은 `learning_roadmaps + roadmap_weeks + progress_logs`다.
- `roadmap_payload`는 결과 보관 및 v2 snapshot 조립용이다.
- 역량 진단, GitHub 분석, 로드맵은 결과 이력 관리를 위해 version을 유지한다.
- GitHub 분석 결과는 정적 신호, 핵심 repo 요약, 사용자 보정값을 함께 보관하되 원본 저장소는 PostgreSQL 기준으로 유지한다.
- 코딩테스트 분석은 v1 원본 구조에서 제외하고 v2 후순위 draft로만 다룬다.

### 3.3 처리 흐름

1. 사용자가 프로필과 선택 자료를 입력한다.
2. 사용자가 GitHub OAuth 연동을 완료하고 분석할 저장소를 선택한다.
3. 시스템이 전체 GitHub 정적 분석과 메타데이터 수집을 수행한다.
4. 사용자가 핵심 repo를 선택하면 시스템이 LLM 기반 요약을 생성한다.
5. 시스템이 기술 태그, 사용 깊이 후보, 근거, 사용자 보정값을 묶어 GitHub 최종 분석 결과를 생성한다.
6. 시스템이 프로필과 GitHub 최종 분석 결과를 기준으로 역량 진단을 수행하고 저장한다.
7. 시스템이 역량 진단 결과를 바탕으로 로드맵을 생성하고 `learning_roadmaps`, `roadmap_weeks`를 저장한다.
8. 사용자가 진도 체크를 수행하면 `progress_logs`를 저장한다.
9. 기본 조회는 각 결과의 최신 version 기준으로 반환한다.

### 3.4 동기/비동기 기준

- v1 기본 구현은 단순 API 파이프라인 기반이다.
- GitHub 분석, 핵심 repo 요약, 장시간 자료 탐색은 이후 비동기 확장 가능하다.
- 비동기 확장 시에도 저장 원본과 결과 version 규칙은 유지한다.

## 4. v2 아키텍처

### 4.1 구조 개요

v2는 v1 결과를 읽는 상위 확장 구조다.

핵심 컴포넌트
- Analyzer
- Planner
- Coach
- Pattern Detector
- Context Manager
- Event System (명시 요청 기반 무거운 작업에만 제한적 도입)

v2의 Coach는 v1에서 생성된 진단/로드맵 결과를 읽기 전용으로 참조한다. Pattern Detector가 재분석/재계획을 트리거할 때만 새 version의 결과 row가 생성되며, v1 데이터 모델은 변경되지 않는다.

Analyzer/Planner 재실행 같은 무거운 작업의 비동기 처리는 별도 job 패턴으로 제한적으로 도입한다. 이벤트 버스 기반 전면 비동기 구조는 채택하지 않는다.

### 4.2 컴포넌트 역할

#### Analyzer
- 사용자의 현재 상태를 가장 정확하게 설명하는 분석 계층이다.
- 기본적으로 세 개의 파이프라인으로 동작한다.
  - GitHub 정적 분석
  - 핵심 repo 요약
  - 역량 진단
- 각 결과는 독립적으로 갱신 가능해야 하며, 최종적으로 진단과 로드맵에 재사용되어야 한다.
- 코딩테스트 약점 분석은 별도 v2 후순위 draft 파이프라인으로만 남긴다.

#### Planner
- 역량 진단 결과를 입력으로 받아 로드맵을 설계하거나 재계획한다.
- v2에서는 `학습 주제 구조 생성 -> 검증된 자료 결합`의 2단계 원칙을 따른다.
- LLM은 주차별 주제 구조와 우선순위를 설계하는 데 집중하고, 실제 자료 선택은 검증된 소스 조회 결과를 조합하는 방향으로 확장한다.
- Function Calling의 함수 시그니처와 반환 shape는 아키텍처 문서에서 고정하지 않는다.

#### Coach
- 사용자와의 대화 처리 및 학습 실행 유도
- 사용자 발화 의도를 기준으로 4가지 처리 경로를 분기한다:
  - 단순 질문/조언 → `COACH_LIGHTWEIGHT` 템플릿으로 컨텍스트 조립 후 자체 응답
  - 진도 점검 / 캐시 조회 → `COACH_PROGRESS_CHECK` 템플릿으로 조립 후 응답
  - 명시적 재분석/재계획 요청 → `COACH_FULL_CONTEXT` 템플릿 + 사용자 확인 후 Analyzer/Planner 동기 호출
  - 자율 트리거 → `COACH_FULL_CONTEXT` 템플릿으로 `user_signals` 신호 + 사용자 발화를 종합해 재계획 제안 여부 판단
- Coach는 수치 기반 패턴(연속 미달성, 반복 실패 등)을 직접 감지하지 않는다. 이는 Pattern Detector의 책임이며, Coach는 감지된 신호를 사용자 발화와 종합해 언어적으로 해석하는 역할만 담당한다.
- Coach는 사용자 동의(명시적 요청 또는 Coach 제안에 대한 확인) 없이 Analyzer 또는 Planner를 호출하지 않는다.

#### Pattern Detector
- 반복 실패, 연속 미달성, 관심사 변화 같은 수치 기반 패턴을 SQL로 감지한다.
- LLM을 호출하지 않는다. 매일 정기 배치(@Scheduled)로 SQL 기반 카운팅·임계치 검사를 수행하고, 감지된 신호를 `user_signals` 테이블에 row로 insert한다.
- 신호 insert 후 후속 처리는 없다. 이벤트를 발행하거나 Coach를 직접 트리거하지 않는다.
- LLM 비용·지연이 불필요한 수치 패턴은 SQL로, 언어적 해석(왜 힘들어 보이는지, 어떻게 제안할지)은 Coach LLM이 담당한다.
- 구체 트리거 기준은 별도 정책 문서에서 정의하며, 임계치는 운영 중 조정 가능하도록 설정을 분리한다.

#### Context Manager
- 책임: use-case 템플릿 기반 컨텍스트 조립, 슬롯별 캐싱, 버전 관리 (순수 인프라 레이어)
- snapshot 생성, 활성 버전 관리, 캐시 무효화, 압축 오케스트레이션을 담당한다.
- 책임 외: LLM 직접 호출, 비즈니스 판단, 이벤트 발행. Context Manager는 데이터를 읽고 조립할 뿐이며, 어떤 결정도 하지 않는다.

#### Event System
- 컴포넌트 간 느슨한 연결을 담당한다.
- 이벤트는 "무엇이 갱신되었는가"에 대한 최소 메타데이터만 전달한다.
- 실제 컨텍스트는 각 구독자가 Context Manager를 통해 pull 한다.

#### Summarizer(optional)
- 긴 대화나 오래된 활동을 요약하는 보조 컴포넌트다.
- Context Manager는 요약이 필요할 때 Summarizer에 위임할 수 있다.
- Spring AI Session API를 통한 구현을 검토한다 (인큐베이션 단계이므로 GLM 호환성 확인 필요).

## 5. Context Manager 기준

### 5.1 목적

- 에이전트가 같은 사용자 상태를 읽게 한다.
- 세션 중간에 읽는 결과 기준이 바뀌지 않게 한다.
- 필요한 컨텍스트만 조립해 비용을 줄인다.
- 오래된 대화와 활동 로그를 압축해도 대화 품질을 유지한다.

### 5.2 3-Tier Context 구조

#### Tier 1. Profile Context
- 상대적으로 변화가 적은 사용자 기본 맥락이다.
- 목표 직무, 현재 수준, 주요 기술 스택, 최신 진단 요약, gap 요약을 포함한다.
- Analyzer와 Planner, Coach가 공통으로 참조한다.

#### Tier 2. Plan Context
- 현재 활성 로드맵과 실행 계획 중심의 맥락이다.
- 주차별 계획 요약, 이번 주 목표, 최근 학습 활동 요약을 포함한다.
- Planner와 Coach가 주로 사용한다.

#### Tier 3. Conversation Context
- 현재 대화 세션에 가까운 짧은 주기 맥락이다.
- 최근 대화, 오늘의 활동 데이터, Pattern Detector 감지 이벤트를 포함한다.
- Coach 전용에 가깝고 가장 자주 갱신된다.

### 5.3 에이전트별 로딩 패턴

- `Analyzer.loadContext()`는 기본적으로 Tier 1을 읽는다.
- `Planner.loadContext()`는 Tier 1과 Tier 2를 읽는다.
- `Coach.loadContext()`는 Tier 1, Tier 2, Tier 3을 모두 읽는다.

### 5.4 세션 일관성 원칙

- Coach 세션이 시작될 때 현재 활성 `profileVersion`, `roadmapVersion`을 세션에 기록한다.
- 대화 세션이 유지되는 동안에는 같은 버전을 계속 읽는다.
- 세션 중간에 새 로드맵이 생성되어도 진행 중인 대화는 자동으로 기준 버전을 바꾸지 않는다.
- 새 버전 반영은 다음 대화 진입 또는 명시적 사용자 확인 이후에 처리한다.

3-Tier 구조 및 Tier별 토큰 예산 / 캐싱 정책

| Tier | 내용                              | 권장 토큰 예산 | Redis TTL | 무효화 기준 |
|------|---------------------------------|----------|-----------|-------------|
| Tier 1 | 프로필, (+진단 요약, GitHub 분석 요약 등)   | ~1000    | 24h | `analysis.completed` 이벤트 |
| Tier 2 | 로드맵, 주차 계획, 진도 상태               | ~1000    | 1h + PostgreSQL 원본 | `roadmap.updated` 이벤트 |
| Tier 3 | 최근 대화 이력, `user_signals` 미처리 신호 | ~1500    | 1h | 매 턴 갱신 |

토큰 예산은 기준값이며 운영 중 비용·품질 trade-off를 관찰하며 조정한다.

### 5.3 세션 일관성 원칙

- 세션 생성 시점의 활성 `profileVersion`, `planVersion`을 고정해 읽는다.
- 세션 진행 중 새 version의 결과가 생성되어도 기존 세션 기준 버전은 자동 변경하지 않는다.
- 새 version 반영 UX는 다음 두 가지 중 선택한다:
  - (a) 다음 대화 진입 시점에 시스템 메시지로 알림 + 사용자가 적용 여부 선택 (기본값)
  - (b) 세션 종료 후 다음 세션부터 자동 적용

## 6. version 관리 원칙

- `v1`, `v2`는 서비스 단계 구분이다.
- 각 결과의 `version`은 결과 이력 관리용이다.
- 재실행 시 새 row와 새 version을 만든다.
- 기본 조회는 최신 version을 반환한다.
- 필요 시 과거 version 조회가 가능해야 한다.
- snapshot version은 원본 결과 version을 대체하지 않고, 특정 시점 컨텍스트를 고정하기 위한 읽기 기준이다.

## 7. 신호 및 이벤트 설계

### 7.1 Pattern Detector 신호 (user_signals 테이블)

Pattern Detector는 이벤트를 발행하지 않는다. 감지된 신호는 `user_signals` 테이블에 row로 insert되며, Coach가 매 turn 진입 시 미처리 신호(processed_at IS NULL)를 Context Manager(Tier 3)를 통해 조회한다.

Coach는 사용자 발화와 누적 신호를 종합해 판단한다:
- "오늘 사용자가 '힘들다' + 3일 미달성 신호 있음 → 재계획 제안"
- "오늘 사용자가 '재밌다' + DP 5회 실패 신호 있음 → 도전 욕구로 해석, 신호 dismiss"

Coach가 신호를 처리한 후 `processed_at`을 마킹한다.

### 7.2 명시 요청 이벤트 (AgentEvent 테이블)

무거운 작업(Analyzer/Planner 재실행) 트리거와 완료 알림에만 사용한다.

핵심 이벤트 예시
- `user.portfolio.updated` — 포트폴리오/GitHub 변경 → Analyzer 재실행 트리거
- `analysis.completed` — Analyzer 완료
- `roadmap.updated` — Planner 완료 (DB 테이블명 `learning_roadmaps`에 맞춰 `plan.updated` 대신 사용)
- `coach.requested_reanalysis` — 사용자 명시 재분석 요청
- `coach.requested_replan` — 사용자 명시 재계획 요청

원칙
- 이벤트 payload에는 최소 식별자와 타입만 담는다.
- 실제 데이터는 Context Manager가 pull 한다.
- Planner가 Coach에게 직접 메시지를 보내지 않고, 다음 턴 로딩 시 새 버전을 읽게 한다.
- 동일 사용자에 대한 재분석, 재계획은 쿨다운과 변경 임계치를 둔다.
- 큰 변경은 사용자 확인 후 반영한다.
- `pattern.detected` 이벤트는 사용하지 않는다. Pattern Detector의 자율 감지 신호는 `user_signals` 테이블 row로 처리한다.

## 8. 운영 및 관측

### 8.1 v1
- 기본 로그
- 예외 추적
- 주요 API 응답 시간 측정
- GitHub 분석과 로드맵 생성 처리 시간 측정

### 8.2 v2
- 에이전트별 호출 수
- 입력/출력 토큰
- 응답 시간
- 비용 추적
- 캐시 히트율
- snapshot 조회 성능
- 재분석 / 재계획 트리거 빈도
- 사용자당 일일/주간 `user_signals` 누적 수 (Pattern Detector 활성도 지표)
- Coach의 재계획 제안 → 사용자 거절 비율 (Pattern Detector 정확도 지표)
- 동일 사용자 재계획 → 즉시 재재계획 발생 빈도 (안정성 지표)

## 9. 문서 우선순위

아키텍처 문서는 구조 책임과 원본 기준을 설명한다.
세부 구현 계약은 아래 문서를 우선 적용한다.

1. DB 물리 스키마 문서
2. 계약 부록 문서
3. API 명세 문서
4. 기능 명세 / 화면 설계 문서
5. 본 아키텍처 문서

## 10. 기술 스택 및 Spring AI 도입 원칙

### 11.1 LLM 호출 계층

LLM 호출 계층은 Spring AI 기반으로 구현한다. GLM 등 비표준 모델은 Custom ChatModel 어댑터를 구현하거나, OpenAI 호환 endpoint 활용 여부를 사전 spike test로 검증한다.

### 11.2 v1에서 사용할 Spring AI 기능

- `ChatClient` — LLM 호출 추상화
- `MessageWindowChatMemory` — 대화 이력 관리 (v1 범위에서 충분)
- 구조화 출력 (JSON 모드) — 진단, 로드맵 생성 결과 파싱

### 11.3 v2에서 검토할 Spring AI 기능

- `ChatClient` + `Advisor` 패턴 — Coach 응답 파이프라인
- 도구 호출 (Function Calling) — Planner 2단계 생성(구조 먼저 → 검증된 자료 결합)에 활용 가능
- `ChatMemory` — v1에서 충분. `Session API`는 인큐베이션 단계(1.0 미만, GLM 호환 미검증)이므로 후순위

### 11.4 채택하지 않는 Spring AI 기능 (근거 명시)

- `AutoMemoryTools`: 단일 사용자 CLI용 설계, multi-user SaaS에 보안 위험. 본 서비스의 JSONB 기반 Context Manager + `user_signals` 구조가 더 적합.
- `Subagent Orchestration` (spring-ai-agent-utils, org.springaicommunity): v0.4.x community org 라이브러리. GLM 어댑터 호환 미검증. 핵심 경로 도입 전 spike test 필요. 본 서비스의 신호 기반 구조와 패러다임 차이 있음.
- `Orchestrator-Workers 동기 패턴`: Coach를 동기 Orchestrator로 구성하면 "사용자 개입 없는 자율 피드백 루프"가 불가능해짐. Coach는 동기 Orchestrator가 아니라 turn-time 판단 허브다.

### 11.5 원칙

Spring AI 기능 목록은 구현 전 팀이 직접 조사해서 결정한다. 이 섹션의 내용은 현재 시점에서의 초기 평가이며, 실제 spike test와 GLM 호환성 검증 후 내용이 바뀔 수 있다. 어떤 기능을 쓸지, 직접 구현할지는 팀이 결정한다.

---

## 11. 최종 정리

- v1은 GitHub 중심 파이프라인을 가진 모노리스 MVP다.
- Redis는 보조 계층이며 PostgreSQL이 원본 저장소다.
- 로드맵 원본은 `learning_roadmaps + roadmap_weeks + progress_logs`다.
- 결과 version은 이력 관리용으로 유지한다.
- v2는 v1을 대체하지 않고, v1 결과를 읽는 상위 확장 구조다.
- Context Manager는 인프라 레이어이고, Planner는 구조 설계와 검증된 자료 결합 원칙을 따른다.
