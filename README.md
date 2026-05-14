# 깃길 (Gitgil)

> 내 GitHub 활동에 맞춘 주차별 학습 로드맵을 만들고, AI 코치가 진도를 함께 따라가는 성장 코칭 서비스

[![Live](https://img.shields.io/badge/demo-parkbongsu.site-2ea44f)](https://parkbongsu.site/)
![Java](https://img.shields.io/badge/java-17-blue)
![Spring Boot](https://img.shields.io/badge/spring--boot-4.0.3-green)
![Next.js](https://img.shields.io/badge/next.js-16.2-black)

---

## 깃길이 해주는 것

목표 직무는 정했는데 *지금 내 위치가 어디인지*, *이번 주에 뭘 해야 하는지* 막막한 사람을 위한 서비스입니다.

1. **내 GitHub를 연결합니다.** 어떤 저장소에서 어떤 기술을 실제로 다뤘는지를 AI가 읽습니다 — 자기 평가 설문이 아니라 *실제 코드*가 진단의 근거가 됩니다.
2. **부족한 부분과 강점을 보여줍니다.** "Spring을 자주 쓰지만 트랜잭션 다루는 깊이가 얕음" 같이, 코드 증거와 함께 진단을 받습니다.
3. **주차별 학습 로드맵이 자동으로 생깁니다.** 한 주는 한 걸음. 목표 시간, 추천 자료, 해볼 만한 과제가 주차별로 정리됩니다.
4. **AI 코치가 매 페이지에 떠 있습니다.** 모르는 개념을 물어보거나, "이번 주는 못했어요" 같은 말을 하면 코치가 흐름을 파악해 **로드맵을 다시 짤지 먼저 제안**합니다.

🌐 직접 써보기: **https://parkbongsu.site/**

---

## 데모

<!-- TODO: 핵심 user journey GIF (GitHub 연결 → 분석 진행 → 로드맵 → 코치 채팅), 약 20초 -->
![데모 GIF 자리](docs/assets/demo.gif)

| 대시보드 | 로드맵 | 코치 위젯 |
|---|---|---|
| ![dashboard](docs/assets/dashboard.png) | ![roadmap](docs/assets/roadmap.png) | ![coach](docs/assets/coach.png) |

---

## 시스템 구조

깃길의 핵심은 *느린 LLM 호출을 사용자가 기다리지 않게* 하고, *대화가 길어져도 처음 본 진단과 일관성*을 유지하는 것입니다. 이 두 가지가 아키텍처 전반의 결정 기준이었습니다.


### 1) 분석은 끝까지 비동기로

분석 한 번이 LLM 호출 여러 단계를 거쳐 1분~수 분이 걸립니다. 처음에는 동기로 처리했는데, 그러면 두 가지가 동시에 깨졌습니다.

- 프론트는 응답이 올 때까지 화면이 막힘
- 백엔드는 LLM이 응답할 때까지 **JPA 커넥션을 hold** 하다 PostgreSQL idle-in-transaction timeout(5분)에 끊김

그래서 LLM이 들어가는 모든 경로를 `@Async` + `AFTER_COMMIT` 으로 옮기고, 잡 상태는 Redis에 두고 프론트가 **3초 폴링**으로 받아갑니다. (SSE를 고려했지만 EC2 무중단 배포 중 끊김과 프록시 호환성 때문에 폴링 채택.) 사용자 화면은 요청 즉시 진행 패널로 전환됩니다.

```
[클라이언트]                  [백엔드]                       [Redis]
    │ POST /github-analyses/async │                              │
    │────────────────────────────►│ jobId 생성, RUNNING 마킹    │
    │      202 + jobId            │─────────────────────────────►│
    │◄────────────────────────────│                              │
    │                             │ @Async 워커: LLM 다단계 호출 │
    │ GET /jobs/{jobId} (3s)      │                              │
    │────────────────────────────►│ ─── 상태 조회 ──────────────►│
    │   RUNNING / SUCCEEDED       │                              │
```

### 2) LLM이 핵심을 만드는 6개 지점

분석/진단/로드맵/코치는 단일 LLM 호출이 아니라 단계가 나눠진 파이프라인입니다. 각 단계는 **JSON 구조화 출력** + **PromptBuilder 단위 테스트** + **Parser 회귀 fixture**로 응답 깨짐을 막습니다.

| 단계 | 역할 | 출력 |
|---|---|---|
| ① Triage | 분석할 가치 있는 저장소를 선별 | champion repo 6–9개 |
| ② RepoSummary | 저장소별 사용 기술·하이라이트 추출 | summary, highlights |
| ③ Synthesis | 전체 기술 프로필 합성, depth 추정 | techTags, evidences |
| ④ Diagnosis | 부족 기술·강점·우선순위 진단 | missingSkills, strengths |
| ⑤ Roadmap | 주차별 학습 계획 생성 (최대 8주) | weeks[].tasks, materials |
| ⑥ Coach | 의도 라우팅 + 응답 생성 | route, replanProposal? |

모델은 prod 기준 `GLM-4.7` (Grepp AI Gateway · LiteLLM 프록시 · OpenAI 호환). 모든 호출이 **system role + user role 분리** 패턴이며 `max_tokens=2000`. 측정 기록은 [`docs/27_llm_latency_investigation_2026-05-11.md`](docs/27_llm_latency_investigation_2026-05-11.md), [`docs/32_prompt_tuning_log.md`](docs/32_prompt_tuning_log.md).

### 3) 코치가 처음 본 진단과 일관성을 유지하는 방법

사용자가 코치와 대화하는 동안에도 분석/로드맵은 갱신될 수 있습니다. 그때마다 답변 기준이 바뀌면 사용자가 혼란스러워집니다. 그래서:

- 분석/진단/로드맵 결과가 의미 있게 바뀔 때 **UserContextSnapshot**을 versioning 해서 발행
- 코치 세션은 시작 시점의 `profileVersion` / `roadmapVersion` 을 **pin** 해서 그 스냅샷만 봄
- "컨텍스트 새로고침" 액션으로 사용자가 명시적으로 최신 스냅샷에 다시 붙음

대화 중 진행 패턴이 흔들리는 신호(반복 미완, 연속 지연)는 별도 SQL 배치 디텍터가 잡아서 코치 프롬프트에 주입합니다. 코치는 이 신호를 보고 **재계획을 제안**할지 결정합니다 (`route=REPLAN_SUGGEST`). 사용자가 수락하면 새 버전 로드맵, 거절하면 기존 흐름 유지.

### 4) GitHub 데이터 수집

저장소 메타만으로는 "fork 떠서 PR 보낸 OSS 기여" 같은 활동이 안 잡힙니다. 그래서:

- **REST** (`/user/repos?affiliation=...`) 로 소유·협업 저장소
- **GraphQL** `contributionsCollection` 로 PR/이슈/리뷰 기여 (1년 윈도우 × 3회 호출로 3년 커버)

토큰은 DB에 **AES-256-GCM 자동 암호화** 컬럼으로 저장됩니다 (JPA `AttributeConverter`). 코드 레벨에서는 평문처럼 다루지만 디스크에는 항상 암호문.

### 5) 데이터 모델 한 줄 요약

- 사용자·진단·로드맵·코치 세션은 **정규화 테이블**로 관리
- LLM이 만든 가변 결과(`analysisPayload`, `diagnosisPayload`, `roadmapPayload`)는 **JSONB 컬럼**에 version 이력과 함께 저장
- Flyway V1–V7 (v2 확장: snapshot, chat session, agent event, detected pattern)

전체 ERD: [dbdiagram.io](https://dbdiagram.io/d/6a048a8f7a923b9472a3f8d7) · 자세한 모델: [`docs/02_data_model_aligned.md`](docs/02_data_model_aligned.md)

---

## 기술 스택

| 영역 | 사용 기술 |
|---|---|
| Frontend | Next.js 16.2 · React 19.2 · TypeScript 5.9 · Tailwind · react-markdown |
| Backend | Java 17 · Spring Boot 4.0 · Spring Security · Spring Data JPA · Flyway |
| Data | PostgreSQL 16 · Redis |
| AI | Zhipu GLM-4.7 via Grepp AI Gateway (LiteLLM, OpenAI 호환) |
| Infra | AWS EC2 Blue-Green · Terraform · Nginx + Let's Encrypt · GitHub Actions · GHCR |
| Test | JUnit 5 · MockMvc · Testcontainers |

---

## 시작하기

### 사전 요구사항
Java 17+, Node.js 20+, Docker Desktop, PowerShell

### 1. 환경 변수
```powershell
Copy-Item .env.example .env
# .env 에 GitHub OAuth secret, AI Gateway API key, JWT secret, 토큰 암호화 키 채우기
```

### 2. 인프라 (PostgreSQL + Redis)
```powershell
docker compose -f docker/docker-compose.yml up -d
```
- PostgreSQL: `localhost:5433` (db `coachdb`, user `coach`)
- Redis: `localhost:6380`

### 3. 백엔드
```powershell
cd backend
.\gradlew.bat bootRun --args="--spring.profiles.active=local,oauth"
```

### 4. 프론트엔드
```powershell
cd frontend
npm install
npm run dev
```
브라우저: http://localhost:3000

> OAuth App 분리(로그인용 / 저장소 연결용), 두 번째 client_id, frontend `.env.local` 등 상세 설정은 [`docs/SETUP.md`](docs/SETUP.md) 참고.

---

## 테스트

```powershell
cd backend
.\gradlew.bat test               # 단위 + 빠른 테스트 (Docker 불필요)
.\gradlew.bat prVerification     # PR 게이트: test + prIntegrationTest
.\gradlew.bat fullVerification   # 전체: + integrationTest + JaCoCo (Docker 필요)
```

| 태스크 | 범위 | Docker |
|---|---|---|
| `test` | 단위 + 빠른 테스트 (`integration` 태그 제외) | ❌ |
| `prIntegrationTest` | `pr-gate` 태그 (PR CI 기준) | ❌ |
| `prVerification` | `test` + `prIntegrationTest` | ❌ |
| `integrationTest` | Testcontainers 기반 통합 테스트 | ✅ |
| `fullVerification` | 전부 + JaCoCo (dev/main push CI) | ✅ |

---

## 프로젝트 구조

```
backend/      Spring Boot · 헥사고날 (domain / application / adapter/in|out)
frontend/     Next.js App Router · RSC
docker/       로컬 인프라 (PostgreSQL · Redis)
docs/         설계 문서, ADR, 트러블슈팅 로그
.github/      CI workflows · PR template
```

---

## 팀

| 이름 | 역할 |
|---|---|
| **양지니** | Full-stack · AI 파이프라인 / 코치 (분석, 코치 대화, 진도 패턴 감지, 컨텍스트 스냅샷, GitHub GraphQL, JWT, 회귀 테스트, 프론트 다수 화면) |
| **박봉수** | Full-stack · 인프라 / 데이터 (로드맵, 진단 프론트 연결, 프론트 스캐폴드, OAuth 안정화, Redis 잡 인프라, AWS Terraform + Blue-Green, CI/CD) |

**기간**: 2026-04-20 ~ 2026-05-15 (프로그래머스 AI 인턴 1기 · Team6)
