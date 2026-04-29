package com.back.coach.service.github.triage;

import com.back.coach.external.llm.LlmClient;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.service.github.Champion;
import com.back.coach.service.github.RepoMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ChampionTriageServiceTest {

    LlmClient llmClient;
    ChampionTriageService service;

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        service = new ChampionTriageService(
                llmClient,
                new ChampionTriagePromptBuilder(),
                new ChampionTriageResponseParser()
        );
    }

    @Test
    @DisplayName("정상 LLM 응답 → champion 그대로 반환, fallback=false")
    void triage_validResponse_returnsChampions() {
        given(llmClient.complete(anyString())).willReturn("""
                {"champions":[
                  {"kind":"COMMIT","ref":"abc","reason":"OAuth"},
                  {"kind":"PR","ref":"42","reason":"라우팅"}
                ]}
                """);

        ChampionTriageService.TriageResult result = service.triage("user/r", "https://u", metadataWithCommits(10));

        assertThat(result.fallback()).isFalse();
        assertThat(result.champions()).hasSize(2);
        assertThat(result.champions().get(0).ref()).isEqualTo("abc");
    }

    @Test
    @DisplayName("LLM 호출이 LLM_TIMEOUT throw → 최신 6개 commit으로 fallback, fallback=true")
    void triage_timeout_fallsBackToLatest6Commits() {
        given(llmClient.complete(anyString()))
                .willThrow(new ServiceException(ErrorCode.LLM_TIMEOUT));

        ChampionTriageService.TriageResult result = service.triage("r", "u", metadataWithCommits(10));

        assertThat(result.fallback()).isTrue();
        assertThat(result.champions()).hasSize(6);
        assertThat(result.champions()).allMatch(c -> c.kind() == Champion.Kind.COMMIT);
        // 가장 최신 (sha9, sha8, ..., sha4) 6개
        assertThat(result.champions()).extracting(Champion::ref)
                .containsExactly("sha9", "sha8", "sha7", "sha6", "sha5", "sha4");
    }

    @Test
    @DisplayName("스키마 위반 응답 → fallback")
    void triage_schemaViolation_fallsBack() {
        given(llmClient.complete(anyString())).willReturn("""
                {"champions":[]}
                """);

        ChampionTriageService.TriageResult result = service.triage("r", "u", metadataWithCommits(10));

        assertThat(result.fallback()).isTrue();
        assertThat(result.champions()).hasSize(6);
    }

    @Test
    @DisplayName("commit이 6개 미만이면 있는 만큼만 fallback")
    void triage_fallback_withFewerThanSixCommits() {
        given(llmClient.complete(anyString()))
                .willThrow(new ServiceException(ErrorCode.LLM_RATE_LIMITED));

        ChampionTriageService.TriageResult result = service.triage("r", "u", metadataWithCommits(3));

        assertThat(result.fallback()).isTrue();
        assertThat(result.champions()).hasSize(3);
    }

    @Test
    @DisplayName("commit이 0개이고 LLM도 실패하면 LLM_TRIAGE_FAILED throw")
    void triage_noCommitsAndLlmFails_throws() {
        given(llmClient.complete(anyString()))
                .willThrow(new ServiceException(ErrorCode.LLM_TIMEOUT));

        RepoMetadata empty = new RepoMetadata(null, Map.of(), List.of(), List.of(), List.of(), List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.triage("r", "u", empty))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_TRIAGE_FAILED);
    }

    private static RepoMetadata metadataWithCommits(int n) {
        // 시간순 (오래된 → 최신). 끝이 최신 (Step 4 invariant).
        List<RepoMetadata.CommitItem> commits = IntStream.range(0, n)
                .mapToObj(i -> new RepoMetadata.CommitItem(
                        "sha" + i, "subject " + i, "", List.of("p"), 1, 0, ""
                ))
                .toList();
        return new RepoMetadata(null, Map.of(), List.of(), commits, List.of(), List.of());
    }
}
