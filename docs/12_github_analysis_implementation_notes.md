# GitHub 분석 파이프라인 — 구현 메모 (Slice 2 TDD Steps 1-13)

> **위치**: 이 문서는 `11_github_analysis_pipeline.md`(아키텍처 설계) 의 **구현 보조 문서**다.
> 설계 원문을 수정하지 않고, TDD 구현 과정에서 드러난 **설계 대비 실제 결정 사항**만 기록한다.

---

## 결정 요약표

| # | 대상 | 설계 원문 | 실제 구현 | 핵심 이유 |
|---|------|-----------|-----------|-----------|
| 1 | `StaticSignalAggregator.aggregate()` 시그니처 | `(List<GithubProject>, List<RepoMetadata>)` | `(List<RepoSignalInput>)` | JPA 엔티티 의존 제거 → 단위 테스트 격리 |
| 2 | `ResolvedChampion` 중간 타입 | 없음 (미정의) | `record ResolvedChampion(Kind, ref, headline, body)` 신설 | 프롬프트 빌더에서 `RepoMetadata` 의존 제거 |
| 3 | `ChampionTriageService.triage()` 반환형 | `List<Champion>` | `TriageResult(champions, fallback)` | `triageFallback` 플래그를 JSONB에 기록하려면 반환형에 포함 필요 |
| 4 | `LLM_TRIAGE_FAILED` 에러코드 | 언급 없음 | `BAD_GATEWAY` 신설 | 폴백조차 불가능한 극단 케이스 표면화 |
| 5 | `GithubAnalysis` 엔티티 생성 | 미정의 | `GithubAnalysis.create(...)` 정적 팩토리 신설 | Slice 1 패턴(`User.signupFromOAuth`) 일관성 + 생성 규칙 중앙화 |
| 6 | Champion 우선순위 불변식 | 암묵적 | **"목록 순서 = 우선순위"** 공개 불변식으로 명문화 | Stage 2 오버플로우 시 후미(낮은 우선순위) 제거 기준 |
| 7 | `RepoMetadata` 목록 정렬 방향 | 암묵적 | **"목록 끝 = 최신"** 공개 불변식으로 명문화 | Triage 캡 drop 및 폴백 최신 N개 선택이 이 정렬에 의존 |
| 8 | Diff 블랙리스트 매칭 방식 | "경로 패턴" 수준 | `Predicate<String>` 규칙 3종(정확 파일명, suffix, prefix) 명시 | unified diff `diff --git a/<path>` 헤더 기반 블록 단위 drop |
| 9 | Synthesis 캡 초과 복구 전략 | "압축" 수준 | `highlights` 앞 3개로 재렌더링 (`COMPRESSED_HIGHLIGHTS_PER_SUMMARY = 3`) | 저장소 전체 누락보다 highlight 압축이 컨텍스트 손실 최소화 |
| 10 | WireMock 통합 테스트 설정 | 미정의 | `@DynamicPropertySource` + `@DirtiesContext` + JSONPath stub 3종 | 단일 엔드포인트로 Stage 1/2/3 응답 구분 |
| 11 | POST 검증 깊이 | 미정의 | DTO는 `@NotNull` 만, "core ⊆ selected" 검사는 서비스 레이어 | 400 출처가 bean validation / 서비스 두 곳임을 문서화 |
| 12 | dev 브랜치 머지 영향 | — | dashboard/roadmap-progress 패키지, `OwnershipValidator`, `JacksonConfig` 합류 | Slice 2 영역과 충돌 없음; `OwnershipValidator` Slice 4+ 리팩토링 후보 |

---

## 결정 1. `StaticSignalAggregator.aggregate()` 시그니처 변경

**결정** `aggregate(List<GithubProject>, List<RepoMetadata>)` → `aggregate(List<RepoSignalInput>)`

```java
record RepoSignalInput(String primaryLanguage, RepoMetadata metadata) {}
```

**이유** 원안은 JPA 엔티티 `GithubProject`를 직접 받아 집계 로직이 영속성 레이어에 의존한다. `RepoSignalInput`으로 래핑하면 집계 로직이 순수 함수가 되어 `GithubProject` 팩토리 없이 단위 테스트를 작성할 수 있다. 엔티티 → 입력 매핑은 오케스트레이터(`GithubAnalysisService`)가 담당한다.

**위치** `domain/github/service/RepoSignalInput.java`, `domain/github/service/StaticSignalAggregator.java`

> 설계 문서 §11의 시그니처 서술은 원문 유지. 실제 시그니처는 이 문서 기준.

---

## 결정 2. `ResolvedChampion` 중간 타입 신설

**결정** `Champion { kind, ref, reason }` (Triage LLM 출력) 과 프롬프트 빌더 입력 사이에 `ResolvedChampion` 레코드를 삽입한다.

```java
record ResolvedChampion(
    Champion.Kind kind,
    String ref,        // commit sha 또는 PR/Issue 번호
    String headline,   // commit subject / PR title / Issue title
    String body        // diff 전처리 후 본문 / PR body / Issue thread
) {}
```

**이유** 프롬프트 빌더(`RepoSummaryPromptBuilder`)가 `RepoMetadata`를 알아야 본문을 조회할 수 있다면 빌더 단위 테스트에 `RepoMetadata` fixture가 필요해진다. 오케스트레이터가 `Champion` → `ResolvedChampion` 변환(sha로 커밋 조회, 번호로 PR/Issue 조회)을 수행하고, 빌더는 `ResolvedChampion` 목록만 받는다.

**위치** `domain/github/service/summary/ResolvedChampion.java`

---

## 결정 3. `ChampionTriageService.triage()` 반환형 변경

**결정** `List<Champion>` → `TriageResult(List<Champion> champions, boolean fallback)`

```java
record TriageResult(List<Champion> champions, boolean fallback) {}
```

**이유** 폴백 경로로 진입했을 때 `analysis_payload.meta.triageFallback = true`를 기록해야 한다. 이 플래그가 반환형에 없으면 오케스트레이터가 폴백 여부를 알 방법이 없다. 폴백 플래그는 관찰자(모니터링, 품질 리뷰)가 분석이 퇴화 경로를 탔음을 판단하는 근거다.

**위치** `domain/github/service/triage/TriageResult.java`, `domain/github/service/triage/ChampionTriageService.java`

---

## 결정 4. `LLM_TRIAGE_FAILED` 에러코드 신설

**결정** 폴백 자체도 불가능한 극단 케이스(커밋 0개 AND LLM 실패)에 `LLM_TRIAGE_FAILED`를 반환한다.

```
ErrorCode.LLM_TRIAGE_FAILED
  HTTP status : BAD_GATEWAY (502)
  message     : "GitHub 분석을 시작할 활동이 부족합니다."
```

**이유** 설계 문서 §7은 폴백을 기술하지만 폴백 실패 케이스의 에러 식별자를 정의하지 않는다. 폴백도 실패하면 분석 자체가 불가능하므로 클라이언트가 재시도 불가 상태임을 구분할 에러코드가 필요하다.

**위치** `global/exception/ErrorCode.java` — `LLM_INVALID_RESPONSE` 바로 다음에 위치

---

## 결정 5. `GithubAnalysis.create(...)` 정적 팩토리 신설

**결정** 엔티티에 `static GithubAnalysis create(Long userId, Long connectionId, int version, ...)` 팩토리를 추가한다.

```java
public static GithubAnalysis create(Long userId, Long connectionId, int version, ...) {
    Assert.notNull(userId, "userId must not be null");
    Assert.notNull(connectionId, "connectionId must not be null");
    Assert.isTrue(version >= 1, "version must be >= 1");
    // ...
}
```

**이유** Slice 1의 `User.signupFromOAuth` 패턴과 일관성을 맞춘다. 생성 제약(userId/connectionId 비null, version ≥ 1)을 팩토리 한 곳에서 강제해 오케스트레이터가 유효성을 직접 검사하지 않아도 된다.

테스트 내 엔티티 ID 설정은 리플렉션 생성자 + `ReflectionTestUtils.setField`를 사용한다(`protected` no-arg 생성자는 JPA 전용).

**위치** `domain/github/entity/GithubAnalysis.java`

---

## 결정 6. Champion 우선순위 불변식 명문화

**결정** `ChampionTriageService.triage()`가 반환하는 `champions` 목록의 **순서가 곧 우선순위**임을 공개 불변식으로 선언한다. 인덱스 0 = 최우선.

**이유** `RepoSummaryPromptBuilder`가 24KB 캡을 초과하면 **후미(낮은 우선순위) champion 부터 제거**한다. 이 동작이 정확하려면 목록 정렬 의미가 명확해야 한다. Slice 4의 bounded agentic Stage 2 확장 시 동일 계약을 유지할 수 있도록 지금 불변식을 문서화한다.

**위치** `domain/github/service/triage/ChampionTriageService.java` Javadoc, `domain/github/service/summary/RepoSummaryPromptBuilder.java` 상수 주석

---

## 결정 7. `RepoMetadata` 목록 정렬 불변식 명문화

**결정** `RepoMetadata.commits`, `pullRequests`, `issues` 목록은 **"목록 끝 = 최신"** (시간순 오름차순)임을 공개 불변식으로 선언한다.

**이유** 두 곳이 이 정렬에 의존한다.

1. `ChampionTriagePromptBuilder` — 20KB 캡 초과 시 **앞(오래된 항목)부터** 제거.
2. `ChampionTriageService` 폴백 — 커밋 목록 **끝 N개**를 "최신 커밋"으로 사용.

정렬이 반대이면 폴백이 가장 오래된 커밋을 선택하고, 캡 초과 시 최신 항목을 잃는다. **Slice 3 HTTP fetcher는 이 정렬 보장을 반드시 지켜야 한다.**

**위치** `domain/github/service/RepoMetadata.java` 레코드/클래스 Javadoc

---

## 결정 8. Diff 블랙리스트 매칭 — `Predicate<String>` 3종 규칙

**결정** `DiffPreprocessor.BLACKLISTED_PATH_PATTERNS`(public)을 `List<Predicate<String>>`으로 구현하며 세 종류의 규칙을 적용한다.

| 규칙 종류 | 예시 | 구현 |
|-----------|------|------|
| 정확 파일명 | `package-lock.json`, `yarn.lock`, `go.sum` | `path.endsWith("/lockfile") \|\| path.equals("lockfile")` |
| suffix | `.png`, `.min.js`, `.map` | `path.endsWith(".min.js")` |
| prefix | `vendor/`, `node_modules/` | `path.startsWith("vendor/")` |

**이유** 설계 문서 §6은 "블랙리스트 경로 패턴"을 나열하지만 매칭 구현 방식을 지정하지 않는다. unified diff를 `diff --git a/<path>` 헤더로 분리하고 경로를 추출해 `Predicate` 체인으로 검사 후 블록 전체를 drop한다. 정규식 대신 `Predicate`를 사용해 규칙 추가/제거를 코드 변경 없이 목록 편집으로 처리한다.

**위치** `domain/github/service/summary/DiffPreprocessor.java`

---

## 결정 9. Synthesis 캡 초과 복구 — "highlight 압축" 전략

**결정** Stage 3 프롬프트가 16KB를 초과하면 저장소를 통째로 제거하는 대신, 모든 `RepoSummary`의 `highlights`를 **앞 3개**로 재렌더링한다(`COMPRESSED_HIGHLIGHTS_PER_SUMMARY = 3`).

**이유** 설계 문서 §5.3은 "각 RepoSummary의 highlights를 앞 3개로 압축"이라 기술하지만 상수 이름과 "drop summaries" 대신 "compress highlights"를 선택한 이유를 명시하지 않는다. 저장소 전체를 제거하면 synthesis가 해당 저장소를 전혀 모르는 상태로 진행된다. highlight 압축은 저장소별 요약(`summary` 필드)은 유지하므로 컨텍스트 손실이 최소화된다.

**위치** `domain/github/service/synthesis/SynthesisPromptBuilder.java` — `COMPRESSED_HIGHLIGHTS_PER_SUMMARY = 3`

---

## 결정 10. WireMock 통합 테스트 설정

**결정** `AiGatewayLlmClient`를 실제로 호출하는 통합 테스트에서 아래 설정을 적용한다.

```java
@DynamicPropertySource
static void overrideAiGatewayUrl(DynamicPropertyRegistry registry) {
    registry.add("ai.gateway.base-url",
        () -> "http://127.0.0.1:" + wireMock.getPort());
}
```

- `@DirtiesContext` — 포트가 각 테스트 클래스마다 다르므로 컨텍스트 공유 방지.
- Stub 구분은 요청 body의 `$.prompt` JSONPath 정규식으로 수행.

| Stage | 매칭 패턴 |
|-------|-----------|
| Stage 1 (Triage) | `"Candidates"` 포함 |
| Stage 2 (Summary) | `"repoId:"` 포함 |
| Stage 3 (Synthesis) | `"Static Signals"` 포함 |

**이유** 세 Stage가 동일 `AiGateway` 엔드포인트를 사용하므로 단일 WireMock 서버로 세 응답을 구분해야 한다. JSONPath 매칭은 요청 body 구조 변경에 강하며, 세 프롬프트에 각각만 등장하는 문자열을 앵커로 사용해 오매칭을 방지한다.

**위치** `test/.../domain/github/service/GithubAnalysisFlowIntegrationTest.java`

---

## 결정 11. POST 요청 검증 깊이 분리

**결정** `GithubAnalysisRequest.coreRepositoryIds`는 `@NotNull`만 적용(빈 리스트 허용). "core ⊆ selected" 제약 검사는 서비스 레이어에서 수행하고 `INVALID_INPUT` 에러를 반환한다.

```java
// DTO
@NotNull
private List<Long> coreRepositoryIds;   // 빈 리스트 허용

// GithubAnalysisService
if (!selectedIds.containsAll(coreIds)) {
    throw new BusinessException(ErrorCode.INVALID_INPUT);
}
```

**이유** "core ⊆ selected"는 두 필드 간 교차 제약이므로 단일 필드 어노테이션으로 표현하기 어렵다. DTO에 넣으면 커스텀 `ConstraintValidator`가 필요하고 서비스 규칙이 DTO에 누출된다. 결과적으로 400 응답의 출처가 두 곳이 된다는 점을 명시해 기여자가 이 검사를 DTO로 옮기지 않도록 한다.

**위치** `domain/github/dto/GithubAnalysisRequest.java`, `domain/github/service/GithubAnalysisService.java`

---

## 결정 12. dev 브랜치 머지 영향 기록

**결정** 구현 중 `git pull`로 dev 브랜치에 합류한 패키지/클래스를 기록한다.

| 합류 항목 | Slice 2 충돌 여부 | 향후 활용 가능성 |
|-----------|------------------|-----------------|
| dashboard 패키지 | 없음 | — |
| roadmap-progress 패키지 | 없음 | — |
| `OwnershipValidator` | 없음 | Slice 4+에서 연결 소유권 확인 리팩토링 후보 (`existsByIdAndUserId` 직접 호출 대체) |
| `JacksonConfig` | 없음 | Synthesis JSONB 직렬화에 재사용 가능 |

**이유** Slice 2 구현 완료 시점의 코드베이스 상태를 스냅샷해 이후 슬라이스 담당자가 충돌 여부와 재사용 가능 컴포넌트를 빠르게 파악할 수 있도록 한다.

**위치** 코드베이스 전반 — 본 항목은 변경 기록이며 특정 파일 위치 없음.

---

## 관련 문서

- `11_github_analysis_pipeline.md` — Slice 2 아키텍처 설계 원문 (이 문서의 기반)
- `09_contract_appendix.md` — `Champion.Kind`, `DepthLevel`, `EvidenceType` enum 계약
- `08_db_physical_schema.md` — `github_analyses` 테이블 DDL
