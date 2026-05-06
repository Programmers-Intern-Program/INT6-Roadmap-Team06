# v2 3-Tier Context 조립 기준

## 1. 목적

이 문서는 v2 Context Manager가 use-case 템플릿별로 어느 정도의 사용자 상태를 읽을지 정리한다.

3-Tier는 `COACH_*`, `ANALYZER_*`, `PLANNER_*` 템플릿을 대체하지 않는다. 템플릿은 "처리 상황"을 고르고, Tier는 "읽는 정보량과 캐시 무게"를 고른다.

## 2. 관계 정리

| 기준 | 역할 | 예시 |
| --- | --- | --- |
| use-case 템플릿 | 어떤 상황인지 결정 | `COACH_LIGHTWEIGHT`, `PLANNER_REPLAN` |
| 3-Tier | 얼마나 많은 상태를 읽을지 결정 | Tier 1, Tier 2, Tier 3 |
| Context Snapshot | 조립된 상태를 고정해 저장 | `PROFILE`, `PLAN`, `CONVERSATION` |

규칙
- 호출자는 먼저 처리 상황에 맞는 use-case 템플릿을 선택한다.
- Context Manager는 템플릿의 기본 Tier를 기준으로 필요한 snapshot과 슬롯을 조립한다.
- 템플릿 전용 입력은 Tier와 별도로 붙일 수 있다. 예: Analyzer의 저장소 메타데이터, Planner의 검증 자료 후보.

## 3. Tier 정의

| Tier | 목적 | 기본 포함 정보 | 캐시 무게 |
| --- | --- | --- | --- |
| Tier 1 | 가벼운 기본 상태 | 프로필, 현재 주차, 최근 진도 | 가볍게 캐시 가능 |
| Tier 2 | 계획과 진단까지 보는 상태 | Tier 1 + 전체 로드맵, 진단 요약, 주차별 진도 요약 | 중간 캐시 가능 |
| Tier 3 | 판단 맥락까지 보는 상태 | Tier 2 + 최근 대화, 활성 신호, GitHub 요약 | 짧게 캐시하거나 매번 조회 |

### Tier 1

사용 상황
- 사용자가 오늘 할 일, 현재 주차, 간단한 조언을 묻는 경우
- Analyzer나 Planner가 사용자의 기본 목표와 수준만 필요로 하는 경우

기본 데이터
- `PROFILE` snapshot의 `profile`, `skills`, `diagnosisSummary.topMissingSkills`
- `PLAN` snapshot의 `currentWeek`, 최근 progress 상태

무효화 기준
- 프로필 저장
- GitHub 분석 보정 저장
- 신규 진단 생성
- 현재 주차 progress 변경

### Tier 2

사용 상황
- 사용자가 전체 로드맵 흐름이나 진도 현황을 묻는 경우
- Planner가 최초 로드맵 생성이나 계획 보강을 수행하는 경우

기본 데이터
- Tier 1 전체
- `PLAN` snapshot의 `roadmap`, `weeks`, `progressSummary`
- `PROFILE` snapshot의 `diagnosisSummary`

무효화 기준
- 신규 로드맵 생성
- `progress_logs` insert
- 신규 진단 생성

### Tier 3

사용 상황
- Coach가 재분석, 재계획, 자율 트리거 제안을 판단하는 경우
- 최근 대화, 활성 신호, GitHub 요약을 함께 봐야 하는 경우

기본 데이터
- Tier 2 전체
- `CONVERSATION` snapshot의 최근 대화 요약
- 활성 신호 요약
- `PROFILE` snapshot의 GitHub 최종 기술 요약

무효화 기준
- 새 대화 메시지 저장
- 대화 요약 갱신
- 활성 신호 상태 변경
- GitHub 분석 보정 저장

주의
- 활성 신호의 원본 저장소 명칭은 별도 신호 계약에서 확정한다.
- Tier 3을 읽는 것만으로 재분석이나 재계획을 실행하지 않는다.

## 4. 템플릿별 기본 Tier

| 템플릿 | 기본 Tier | 예외 기준 |
| --- | --- | --- |
| `COACH_LIGHTWEIGHT` | Tier 1 | 사용자가 근거, 이전 대화, 재계획 가능성을 물으면 Tier 2 또는 Tier 3로 올린다. |
| `COACH_PROGRESS_CHECK` | Tier 2 | 활성 신호나 최근 대화 해석이 필요하면 Tier 3로 올린다. |
| `COACH_FULL_CONTEXT` | Tier 3 | 낮추지 않는다. 재분석/재계획 판단에는 전체 판단 맥락이 필요하다. |
| `ANALYZER_GITHUB_SUMMARY` | Tier 1 | 저장소 메타데이터, README, 파일 구조는 템플릿 전용 슬롯으로 별도 추가한다. |
| `ANALYZER_DIAGNOSIS` | Tier 1 | GitHub 정적 분석 결과와 직무 기준표는 템플릿 전용 슬롯으로 별도 추가한다. |
| `PLANNER_INITIAL` | Tier 2 | 코딩테스트 분석 draft나 검증 자료 후보는 템플릿 전용 슬롯으로 별도 추가한다. |
| `PLANNER_REPLAN` | Tier 3 | 사용자 확인 없는 자동 재계획 실행은 하지 않는다. |

## 5. 승격과 축소 기준

Tier 승격
- 사용자가 전체 로드맵, 진단 이유, 주차별 흐름을 물으면 Tier 1에서 Tier 2로 올린다.
- 사용자가 재계획, 반복 실패, 최근 대화 맥락, GitHub 근거를 물으면 Tier 3로 올린다.
- 활성 신호가 있는 상태에서 Coach가 제안 여부를 판단해야 하면 Tier 3를 사용한다.

Tier 축소
- 단순 설명, 오늘 할 일 확인, 현재 주차 상태 확인은 Tier 1로 유지한다.
- 전체 로드맵 요약만 필요하면 Tier 2에서 멈춘다.
- 최근 대화와 활성 신호가 필요하지 않으면 Tier 3를 읽지 않는다.

## 6. 캐시 기준

| Tier | 권장 TTL | 캐시 기준 |
| --- | --- | --- |
| Tier 1 | 최대 24시간 | 프로필과 기본 진단 요약은 자주 바뀌지 않는다. |
| Tier 2 | 최대 6시간 | 로드맵과 진도는 학습 진행 중 바뀔 수 있다. |
| Tier 3 | 캐싱 없음 또는 최대 1시간 | 대화와 활성 신호는 매 turn 기준이 중요하다. |

공통 규칙
- `progress_logs` insert 이후에는 현재 주차와 진도 요약 캐시를 무효화한다.
- 새 진단이나 새 로드맵이 생성되면 관련 Tier 캐시를 무효화한다.
- 세션에서 snapshot version을 고정한 경우, 캐시보다 세션 고정 version을 우선한다.

## 7. 예시

오늘 할 일 확인
- 템플릿: `COACH_LIGHTWEIGHT`
- 기본 Tier: Tier 1
- 이유: 현재 주차와 최근 진도만 있으면 답할 수 있다.

진도 점검
- 템플릿: `COACH_PROGRESS_CHECK`
- 기본 Tier: Tier 2
- 이유: 전체 로드맵과 주차별 진도 요약이 필요하다.

재계획 제안 판단
- 템플릿: `COACH_FULL_CONTEXT`
- 기본 Tier: Tier 3
- 이유: 최근 대화, 활성 신호, GitHub 요약까지 함께 봐야 한다.

## 8. 제외 범위

- Context Manager 구현
- Redis key naming 구현
- DB migration, Entity, Repository, API 구현
- Pattern Detector 신호 테이블명 정리
