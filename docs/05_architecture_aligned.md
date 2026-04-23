# AI 개발자 성장 코치 서비스 아키텍처 문서

## 1. 문서 목적

이 문서는 팀 구현 기준에서 아키텍처 책임과 원본 데이터 기준을 명확히 하기 위한 정렬본이다.
상세 컬럼 계약은 DB 물리 스키마 문서를 따르고, enum, JSONB shape, validation, 상태 전이는 계약 부록 문서를 따른다.

## 2. 아키텍처 목표

- v1은 페이지 기반 MVP를 빠르게 구현할 수 있어야 한다.
- v2는 v1 결과를 재사용하는 상위 확장 구조여야 한다.
- 원본 데이터와 캐시, 결과 이력의 책임을 분리해야 한다.
- 팀원이 같은 저장 원본과 상태 전이를 기준으로 구현할 수 있어야 한다.

## 3. v1 아키텍처

### 3.1 구조 개요

v1은 모노리스 기반의 페이지 + API 파이프라인 구조다.

구성 요소
- 프론트엔드
  - 역량 진단 입력/결과 화면
  - GitHub 분석 화면
  - 코딩테스트 분석 화면
  - 로드맵 화면
- 백엔드 API 서버
  - 프로필 처리
  - 역량 진단
  - GitHub 분석
  - 코딩테스트 분석
  - 로드맵 생성
  - 진도 저장
- PostgreSQL
  - 영속 원본 데이터 저장
- Redis
  - 세션 캐시
  - 조회 캐시
  - 임시 작업 상태 저장
- LLM 연동 모듈
  - 진단 요약 생성
  - GitHub 분석 요약
  - 로드맵 생성

### 3.2 저장 원본 기준

v1 저장 원본 기준은 다음과 같다.

- PostgreSQL이 시스템의 원본 저장소다.
- Redis는 캐시와 임시 상태 저장용 보조 계층이다.
- 로드맵 원본은 `learning_roadmaps + roadmap_weeks + progress_logs`다.
- `roadmap_payload`는 결과 보관 및 v2 snapshot 조립용이다.
- 진단, GitHub 분석, 코딩테스트 분석, 로드맵은 결과 이력 관리를 위해 version을 유지한다.

### 3.3 처리 흐름

1. 사용자가 프로필 입력
2. 역량 진단 수행 및 결과 저장
3. GitHub 분석 수행 및 결과 저장
4. 코딩테스트 분석 수행 및 결과 저장
5. 로드맵 생성 및 `learning_roadmaps`, `roadmap_weeks` 저장
6. 진도 체크 시 `progress_logs` 저장
7. 기본 조회는 각 결과의 최신 version 기준으로 반환

### 3.4 동기/비동기 기준

- v1 기본 구현은 동기 처리다.
- GitHub 분석, 대용량 업로드 분석, 장시간 집계 작업은 이후 비동기 확장 가능하다.
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
- Event System

### 4.2 컴포넌트 역할

#### Analyzer
- 프로필, GitHub, 외부 데이터 기반 분석 수행
- 진단 계열 결과 갱신

#### Planner
- 진단 결과와 약점 결과를 기반으로 로드맵 생성 또는 재계획

#### Coach
- 사용자와의 대화 처리
- 학습 실행 유도
- 필요 시 재분석/재계획 요청

#### Pattern Detector
- 반복 실패, 연속 미달성 같은 패턴 감지

#### Context Manager
- v1 원본 데이터와 결과 이력을 조합해 snapshot 생성
- 세션 단위로 읽을 version 고정

#### Event System
- 각 컴포넌트 간 비동기 연결
- 이벤트 payload는 최소 메타데이터만 전달

## 5. Context Manager 기준

### 5.1 목적

- 에이전트가 같은 사용자 상태를 읽게 한다.
- 세션 중간에 읽는 결과 기준이 바뀌지 않게 한다.
- 필요한 컨텍스트만 조립해 비용을 줄인다.

### 5.2 조립 원칙

- 원본은 PostgreSQL에서 읽는다.
- 정규화 데이터와 JSONB 결과를 함께 읽는다.
- snapshot은 읽기 최적화된 조립 결과이며 원본을 대체하지 않는다.
- `ChatSession.profileVersion`, `planVersion`은 세션 시작 시 고정한다.

## 6. version 관리 원칙

- `v1`, `v2`는 서비스 단계 구분이다.
- 각 결과의 `version`은 결과 이력 관리용이다.
- 재실행 시 새 row와 새 version을 만든다.
- 기본 조회는 최신 version을 반환한다.
- 필요 시 과거 version 조회가 가능해야 한다.

## 7. 이벤트 설계 원칙

핵심 이벤트 예시
- `analysis.completed`
- `plan.updated`
- `pattern.detected`
- `coach.requested_reanalysis`
- `coach.requested_replan`

원칙
- 이벤트 payload에는 최소 식별자와 타입만 담는다.
- 실제 데이터는 Context Manager가 pull 한다.
- 큰 변경은 사용자 확인 후 반영한다.

## 8. 운영 및 관측

### 8.1 v1
- 기본 로그
- 예외 추적
- 주요 API 응답 시간 측정

### 8.2 v2
- 에이전트별 호출 수
- 입력/출력 토큰
- 응답 시간
- 비용 추적
- 캐시 히트율
- snapshot 조회 성능

## 9. 문서 우선순위

아키텍처 문서는 구조 책임과 원본 기준을 설명한다.
세부 구현 계약은 아래 문서를 우선 적용한다.

1. DB 물리 스키마 문서
2. 계약 부록 문서
3. API 명세 문서
4. 기능 명세 / 화면 설계 문서
5. 본 아키텍처 문서

## 10. 최종 정리

- v1은 PostgreSQL 원본 기반의 모노리스 MVP다.
- Redis는 보조 계층이다.
- 로드맵 원본은 `learning_roadmaps + roadmap_weeks + progress_logs`다.
- 결과 version은 이력 관리용으로 유지한다.
- v2는 v1을 대체하지 않고, v1 결과를 읽는 상위 확장 구조다.
