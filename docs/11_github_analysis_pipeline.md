# GitHub 분석 파이프라인 아키텍처 (Slice 2)

## 0. 선행 슬라이스 (Slice 1, 1.5)

Slice 2가 가정하는 전제 — `github_connections.access_token` 이 항상 유효한 값으로
존재한다는 점 — 은 아래 두 슬라이스에서 충족된다. 본 문서의 본문(§1 이하)은
Slice 2 의 파이프라인 설계만 다루며, 인증 측 결정은 여기에 짧게 요약한다.

### 0.1 Slice 1 — GitHub OAuth 로그인 + JWT 발급 (✅ 완료)

**목적.** v1 모든 AI 기능이 의존하는 `github_connections.access_token` 영속화의
전제 조건. GitHub OAuth2 로그인 성공 시 `User` / `GithubConnection` 을 업서트하고
JWT access/refresh 토큰을 HttpOnly 쿠키로 발급한다.

**핵심 컴포넌트.**
- `AuthService.loginWithGithub(GithubUserInfo, githubAccessToken)` — 신규/기존 사용자 분기 + 토큰 갱신
- `CoachOAuth2UserService` / `GithubUserInfoMapper` — userinfo 매핑 검증
- `CoachOAuth2LoginSuccessHandler` — 쿠키 설정 + state 기반 리다이렉트
- `JwtTokenProvider` — `type` 클레임으로 access/refresh 분리 (참조 프로젝트의 동일-파싱 결함 차단)
- `JwtAuthenticationFilter` — `Authorization: Bearer` 와 `accessToken` 쿠키 둘 다 허용
- `V3__add_github_connections_access_token.sql` — `access_token VARCHAR(500)`
  (TODO(security): 평문 저장은 v1 MVP 한정, 후속 슬라이스에서 암호화)

**위임된 결정.**
- 토큰 무효화(revocation) 는 v1 범위 외. 짧은 access TTL (15분) + refresh TTL (24시간) 로 완화.
- 이메일 로그인은 v1 비포함.

### 0.2 Slice 1.5 — 인증 보강 (✅ 완료)

**목적.** Slice 1 만으로는 실제 브라우저(특히 Next.js + cross-origin) 환경에서
OAuth 플로우가 동작하지 않거나 보안 결함을 노출한다. 참조 프로젝트
`NBE8-10-final-Team02` 운영 중 드러난 사각지대를 사전에 닫는다.

**핵심 컴포넌트.**
- `CookieManager` — `ResponseCookie` 로 `HttpOnly; Secure; SameSite=Lax; Domain` 일관 적용
  (`jakarta.servlet.http.Cookie` 는 SameSite 미지원이라 cross-origin 에서 깨짐)
- `CoachOAuth2AuthorizationRequestResolver` — 프론트의 `?redirectUrl=` 을 `OAuth2State` 로
  인코딩해 state 에 실음. 콜백에서 디코딩해 원래 페이지로 복귀
  (Slice 1 에는 디코더만 있고 인코더가 빠져 있어 항상 기본 URL 로 fallback 됐음)
- `CookieOAuth2AuthorizationRequestRepository` — STATELESS 정책 하 OAuth2 인증 요청을
  쿠키에 보관. HMAC-SHA256 서명 검증 후에만 역직렬화하여 `ObjectInputStream` RCE 차단
  (참조 프로젝트의 무서명 직렬화 대비 보안 강화)
- `CoachOAuth2LoginFailureHandler` — 인증 취소/실패 시 백엔드 `/login?error` 가 아닌
  `${FRONTEND_URL}/login?error=<msg>` 로 리다이렉트
- CORS `setAllowCredentials(true)` — Next.js cross-origin 쿠키 전송 허용
- `OAuth2LoginSecurityConfig` — 위 컴포넌트들을 OAuth2 필터 체인에 배선

이로써 Slice 2 가 가정하는 "유효한 `github_connections.access_token` 이 항상 존재한다"
는 전제가 실제 브라우저 흐름에서도 성립한다.

---

## 1. 문서 목적

이 문서는 Slice 2 (GitHub 분석) 설계 세션에서 확정된 파이프라인 아키텍처 결정을 기록한다.
기능 명세(04), 아키텍처 문서(05)는 GitHub 분석을 개념 수준에서 다루며, 구체 파이프라인 설계는 이 문서를 기준으로 한다.

포함 범위
- 3-Stage LLM 파이프라인 구조 및 각 단계의 입출력 계약
- Context 캡(byte 한도) 상수 일람
- Diff 전처리 규칙
- Champion Triage 폴백 전략
- 슬라이스 경계 (Slice 2 / 2.5 / 3 / 4)
- 채택하지 않은 설계 대안과 그 근거
- 후속 슬라이스로 명시적으로 미룬 항목

이 문서는 구현 계약(enum, JSONB shape)을 새로 정의하지 않는다. enum은 계약 부록(09), JSONB shape는 아래 §4에서 확인한다.

---

## 2. 설계 결정 요약

이번 세션에서 변경된 핵심 결정 두 가지.

| 항목 | 이전 방향 | 확정된 방향 | 변경 이유 |
|------|-----------|-------------|-----------|
| 분석 입력 단위 | 저장소 전체 메타데이터 + 역할 기반 요약 | 사용자 본인 커밋/PR/Issue 기반 3-Stage LLM | 실제 기여 근거 중심으로 신뢰도 향상 |
| 후보 선별 방식 | 룰베이스 ImpactScore + Significance Check 점수 컷 | Stage 1 Triage LLM에 위임 | 커밋 메시지 품질이 낮아 룰 효과 대비 복잡도가 큼 |

---

## 3. 파이프라인 개요

```
사용자 요청 (POST /api/github-analyses)
    │
    ▼
[GithubAnalysisService — orchestrator]
    │
    ├─ 연결 소유권 확인 (existsByIdAndUserId → FORBIDDEN)
    ├─ GithubProject 목록 로드 (selected / core 필터)
    │
    ├─ StaticSignalAggregator
    │     languageBytes 합산 비율, activeRepos 수,
    │     commitFrequency/contributionPattern (하드코딩, Slice 3에서 실값으로 교체)
    │
    ├─ [core repo 순회]
    │     │
    │     ├─ Stage 1 — Champion Triage (Haiku/경량 LLM)
    │     │     입력: 메타데이터만 (subject, paths, +/- lines) + README 발췌 ≤ 1KB
    │     │     출력: champion 6~9개 { kind, ref, reason }
    │     │     폴백: 최신 본인 커밋 6개 (meta.triageFallback = true)
    │     │
    │     └─ Stage 2 — Per-repo Summary (Sonnet/Opus)
    │           입력: champion 본문 (diff/PR body/issue thread) — DiffPreprocessor 적용 후 byte cap
    │           출력: RepoSummary { repoId, repoName, summary, highlights[] }
    │
    └─ Stage 3 — Synthesis (Sonnet/Opus)
          입력: StaticSignals + 모든 RepoSummary
          출력: { techTags[], depthEstimates[], evidences[], finalTechProfile }
              → AnalysisPayload 조립 → github_analyses 저장
```

처리 방식: v1 동기 호출 (docs §6.5 기준). 비동기 확장은 Slice 4 이후.

---

## 4. metadata_payload JSONB 형태

`github_projects.metadata_payload` 컬럼은 Slice 3 HTTP fetcher가 채운다.
Slice 2는 이 컬럼이 아래 형태로 pre-populated 되어 있다고 가정하고 fixture로 테스트한다.

```json
{
  "readmeExcerpt": "...",
  "languageBytes": { "Java": 12345, "TypeScript": 6789 },
  "dependencyFiles": [
    { "path": "pom.xml", "contentExcerpt": "..." }
  ],
  "commits": [
    {
      "sha": "...", "subject": "...", "bodyExcerpt": "...",
      "paths": ["..."], "additions": 0, "deletions": 0, "diffExcerpt": "..."
    }
  ],
  "pullRequests": [
    { "number": 0, "title": "...", "bodyExcerpt": "...", "state": "MERGED", "additions": 0, "deletions": 0 }
  ],
  "issues": [
    { "number": 0, "title": "...", "bodyExcerpt": "...", "state": "CLOSED", "commentExcerpts": ["..."] }
  ]
}
```

Slice 3에서 이 컬럼을 채울 때 사용할 GitHub API 출처:
- `readmeExcerpt`: `repo.getReadme().getContent()` — 1KB 초과 시 `[…truncated]`
- `languageBytes`: `repo.listLanguages()` — byte 단위 맵 (비율 계산이 `primary_language` 카운트보다 정확)
- `dependencyFiles`: `repo.getFileContent(path)` — 탐색 대상: `pom.xml`, `build.gradle(.kts)`, `package.json`, `requirements.txt`, `pyproject.toml`, `Cargo.toml`, `go.mod`, `Gemfile`. 없으면 skip.

---

## 5. Stage별 입출력 계약 및 캡

모든 캡 상수는 각 PromptBuilder 클래스의 `public static final` 상수로 한곳에서 관리한다.

### 5.1 Stage 1 — Champion Triage

**입력**

| 섹션 | 내용 | 제한 |
|------|------|------|
| Header | repo 이름/URL + `readmeExcerpt` | ≤ 1KB (README는 drop 대상이 아님) |
| Candidates | 본인 최근 커밋 + PR + Issue 메타데이터 | 커밋 100개, PR 50개, Issue 50개 |
| 전체 프롬프트 | — | ≤ 20KB. 초과 시 가장 오래된 candidate부터 drop, WARN 로그 |

각 candidate 표현: subject/title + 변경 파일 경로 일부 + +/- lines (1~3줄). diff 본문 없음.

README를 Header에 포함하는 이유: diff와 커밋 메시지만으로는 LLM이 저장소의 기술적 맥락을 잘못 해석할 수 있다. README 발췌는 champion 선별 정확도를 위한 framing 컨텍스트이며, 1KB 이하로 제한해 프롬프트 예산 영향을 최소화한다.

**출력 schema (`ai/schema/champion-triage.schema.json`)**

```json
{
  "champions": [
    { "kind": "COMMIT|PR|ISSUE", "ref": "<sha or number>", "reason": "<한 줄>" }
  ]
}
```

kind enum 강제, 배열 길이 1~9. 0개이거나 9개 초과이면 스키마 위반으로 처리한다.

**LLM 모델**: Haiku 4.5 (경량, triage 비용 절감 목적)

### 5.2 Stage 2 — Per-repo Summary

**입력**

| 항목 | 제한 |
|------|------|
| 항목당 champion 본문 | ≤ 8KB (`[…truncated]` 마커) |
| Stage 2 프롬프트 전체 | ≤ 24KB |

champion 종류별 본문:
- `COMMIT` → diff (DiffPreprocessor 적용 후)
- `PR` → PR body + 핵심 review comment
- `ISSUE` → thread 발췌 (bodyExcerpt + commentExcerpts)

**출력 schema (`ai/schema/repo-summary.schema.json`)**

```json
{
  "repoId": "...",
  "repoName": "...",
  "summary": "...",
  "highlights": ["..."]
}
```

모든 필드 required.

**LLM 모델**: Sonnet/Opus

### 5.3 Stage 3 — Synthesis

**입력**

| 항목 | 제한 |
|------|------|
| 프롬프트 전체 | ≤ 16KB. 초과 시 각 RepoSummary의 highlights를 앞 3개로 압축 |
| 내용 | StaticSignals + 모든 per-repo RepoSummary |

**출력 schema (`ai/schema/synthesis.schema.json`)**

```json
{
  "techTags": ["..."],
  "depthEstimates": [{ "skillName": "...", "level": "INTRO|APPLIED|PRACTICAL|DEEP", "reason": "..." }],
  "evidences": [{ "repoName": "...", "type": "README|CODE|CONFIG|REPO_METADATA|COMMIT", "source": "...", "summary": "..." }],
  "finalTechProfile": "..."
}
```

모든 필드 required. `level` 및 `type` 값은 계약 부록 §3.3 enum 강제.

**LLM 모델**: Sonnet/Opus

---

## 6. Diff 전처리 (Slice 2 범위)

`DiffPreprocessor.clean(String unifiedDiff)` — 파일 경로 블랙리스트 기반 hunk 통째 drop 후 byte cap 적용.

**블랙리스트 경로 패턴 (`DiffPreprocessor.BLACKLISTED_PATH_PATTERNS`)**

| 범주 | 경로/패턴 예시 |
|------|---------------|
| 잠금 파일 | `package-lock.json`, `yarn.lock`, `pnpm-lock.yaml`, `Cargo.lock`, `Gemfile.lock`, `go.sum`, `poetry.lock`, `gradle.lockfile` |
| 생성/번들 파일 | `*.min.js`, `*.min.css`, `*.map`, `dist/`, `build/`, `*.snap` |
| 바이너리 | `*.png`, `*.jpg`, `*.gif`, `*.ico`, `*.pdf` |
| 벤더드 | `vendor/`, `node_modules/` |

블랙리스트에 해당하는 hunk는 전부 drop한다. 나머지에 항목당 8KB cap 적용.

**미룬 항목 (Slice 4)**: Hunk 단위 cleanup (import-only / whitespace-only / `@generated` 마커 hunk drop), Java AST 압축. OSS 참고: OpenBMB/RepoAgent (AST + LLM 역할 분리 패턴), Repomix (tree-sitter 기반 압축).

---

## 7. Champion Triage 폴백

Triage가 다음 중 하나에 해당할 때 폴백을 실행한다:
- champion 배열이 비어 있음 (0개 선정)
- 스키마 위반 (kind enum 오류, 길이 범위 위반)
- LLM 타임아웃

폴백 동작: 시간순 최신 본인 커밋 6개를 champion으로 사용하고 `analysis_payload.meta.triageFallback = true`를 기록한다.

이렇게 결정한 이유: LLM 일시 장애로 분석 파이프라인 전체가 중단되는 사태를 막기 위함이다. Reversal/품질 문제는 Slice 4에서 다룰 영역으로, 최소한의 결과를 반환하는 것이 더 낫다.

극단적 케이스(폴백조차 실패)에만 `LLM_TRIAGE_FAILED` 오류를 surface한다.

---

## 8. 채택하지 않은 설계 대안

### 8.1 룰베이스 ImpactScore / Significance Check

**내용**: NBE8-10-final-Team02 프로젝트에서 차용한 방식. 커밋 메시지 패턴, 변경량 등으로 점수를 계산하고 컷을 적용.

**채택하지 않은 이유**: 인턴 프로젝트 커밋 메시지 품질이 낮아 "fix", "update" 같은 무의미한 메시지가 다수다. 점수 효과가 복잡도를 정당화하지 못한다. 대신 Stage 1 Triage LLM이 맥락을 이해하며 선별한다.

### 8.2 Bounded Agentic Stage 2

**내용**: Stage 2를 단순 pre-digest(한 번 호출)가 아니라 LLM이 도구(read_file, grep, list_files, read_commit_diff 등)를 사용해 최대 10회 이내로 자율 탐색하는 방식.

**채택하지 않은 이유**:
- 토큰 비용 3~17배 증가
- 비결정적 경로 — 테스트 및 디버깅 어려움
- Slice 2 목표("분석 흐름이 한 번 끝까지 통과")와 맞지 않음

**미룬 대상**: Slice 4에서 pre-digest와 A/B 비교 실험. OSS 참고: OpenBMB/RepoAgent.

### 8.3 Blobless Clone + JavaParser AST + Call Graph PageRank

**내용**: 저장소를 로컬에 clone해 AST 기반 호출 그래프를 구성하고 PageRank로 중요 파일을 선별.

**채택하지 않은 이유**: 운영 인프라 복잡도가 크고 Slice 2 범위를 벗어난다.

### 8.4 비동기 처리 (@Async + Redis 상태 추적)

**채택하지 않은 이유**: v1 외부 계약은 동기 처리(docs §6.5). 비동기 확장은 아키텍처 문서 §3.4 원칙에 따라 나중에 추가 가능하다.

---

## 9. 슬라이스 경계

| 슬라이스 | 범위 |
|----------|------|
| **Slice 1** ✅ | GitHub OAuth 로그인 + JWT 발급 + `User` / `github_connections` 업서트 (§0.1) |
| **Slice 1.5** ✅ | 인증 보강 — 쿠키 SameSite, OAuth state 라운드트립, 실패 핸들러, CORS allowCredentials, RCE-safe 쿠키 저장소 (§0.2) |
| **Slice 2** | pre-digest 파이프라인 전체 + GithubAnalysisController (POST/GET). fixture 기반 테스트. GitHub HTTP fetcher 제외. PATCH /corrections 제외. |
| **Slice 2.5** | `PATCH /api/github-analyses/{id}/corrections` |
| **Slice 3** | GitHub HTTP fetcher — GraphQL `contributionsCollection` + REST commits/PRs/issues로 `metadata_payload` 실값 채우기. `repo.listLanguages()`, 의존성 파일, README 수집. rate limit 처리. |
| **Slice 4** | 품질 강화 — Decision Reversal 처리, hunk 단위 diff cleanup, bounded agentic Stage 2 A/B 실험, 멀티 프로바이더 라우팅, 실데이터 기반 캡 튜닝. |
| **Slice 5** | 역량 진단 (`analysis_payload.finalTechProfile` 소비) |
| **Slice 6** | 학습 로드맵 |

---

## 10. Decision Reversal 문제 (Slice 4로 명시적 이연)

**문제**: 사용자가 커밋 A에서 특정 결정을 내렸다가 이후 커밋 B에서 번복한 경우, 현재 파이프라인은 A를 champion으로 선정해 해당 기술을 잘못 크레딧할 수 있다.

**Slice 2에서 허용하는 이유**: 분석 흐름을 end-to-end로 먼저 통과시키는 것이 목표다. 번복 감지 없이도 기본 분석은 동작한다.

**Slice 4 계획**:
1. Triage에 전체 타임라인 시야 + arc(의사결정 묶음) 그루핑 추가
2. Stage 2에 champion 이후 커밋 subject를 컨텍스트로 주입
3. `RepoSummary.highlights[].currentStatus = ADOPTED | EVOLVED | REVERSED` 필드 추가
4. HEAD 시점 핵심 파일 샘플링으로 현재 상태 검증

---

## 11. 정적 신호 계산 규칙

`StaticSignalAggregator.aggregate(List<GithubProject>, List<RepoMetadata>)` → `StaticSignals`

| 신호 | 계산 방법 |
|------|-----------|
| 언어 비율 | selected repo 전체의 `languageBytes` 합산 후 비율. `languageBytes`가 없으면 `primary_language` 카운트로 fallback. |
| `activeRepos` | selected repo 수 |
| `commitFrequency` | `"WEEKLY"` 하드코딩 (Slice 3에서 실값으로 교체할 TODO 남김) |
| `contributionPattern` | `"CONSISTENT"` 하드코딩 (동일) |

---

## 12. 버전 및 저장 규칙

- 같은 트랜잭션에서 `findMaxVersionByUserId().orElse(0) + 1`로 version 결정.
- `DataIntegrityViolationException` → `ANALYSIS_FAILED`. retry 없음.
- `userCorrections`는 생성 시 빈 배열. POST는 corrections을 받지 않는다 (Slice 2.5에서 PATCH로 추가).
- 모든 read/write는 `userId` 필터링. 연결 소유권은 `existsByIdAndUserId`로 확인 후 `FORBIDDEN` 반환 (NOT_FOUND 아님 — ID 노출 방지).

---

## 13. LLM 프로바이더

개발 단계: GLM (무료 지원). `AiGatewayLlmClient`가 추상화하므로 `application.yml` 모델 키만 교체하면 된다.
단계별 모델 (triage = 경량, deep/synthesis = 상위)은 GLM 라인업 안에서 properties로 지정한다.

멀티 프로바이더 라우팅 (Anthropic/OpenAI fallback)은 Slice 4로 미룬다.

---

## 14. 관련 문서

- 기능 명세 §4 (04_function_spec_aligned.md) — GitHub 분석 기능 입출력 및 단계 구분
- 아키텍처 문서 §3.3 (05_architecture_aligned.md) — GitHub 분석 처리 흐름 위치
- 계약 부록 §3.3 (09_contract_appendix.md) — `github_depth_level`, `github_evidence_type` enum
- DB 물리 스키마 (08_db_physical_schema.md) — `github_analyses`, `github_projects` 테이블 DDL
- API 명세 §3.3.1 (03_api_spec_aligned.md) — POST/GET 응답 형태
- OSS 참고 목록 (agent-memory `github_analysis_oss_references.md`) — specfy/stack-analyser, RepoAgent, Repomix 등
