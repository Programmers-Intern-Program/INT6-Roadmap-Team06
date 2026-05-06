package com.back.coach.domain.github.service;

import com.back.coach.domain.github.entity.GithubConnection;
import com.back.coach.domain.github.entity.GithubProject;
import com.back.coach.domain.github.repository.GithubConnectionRepository;
import com.back.coach.domain.github.repository.GithubProjectRepository;
import com.back.coach.domain.github.service.fetcher.GithubMetadataFetcher;
import com.back.coach.external.github.GithubApiClient;
import com.back.coach.external.github.dto.GithubRepoDto;
import com.back.coach.external.github.dto.GithubUserInfoDto;
import com.back.coach.global.code.GithubAccessType;
import com.back.coach.domain.github.service.RepoMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class GithubConnectionServiceTest {

    GithubApiClient apiClient;
    GithubConnectionRepository connectionRepo;
    GithubProjectRepository projectRepo;
    GithubMetadataFetcher metadataFetcher;
    GithubConnectionService service;

    static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        apiClient = mock(GithubApiClient.class);
        connectionRepo = mock(GithubConnectionRepository.class);
        projectRepo = mock(GithubProjectRepository.class);
        metadataFetcher = mock(GithubMetadataFetcher.class);
        service = new GithubConnectionService(apiClient, connectionRepo, projectRepo, metadataFetcher);
    }

    @Test
    @DisplayName("신규 연결: GithubConnection + GithubProject 행 생성 + metadata fetch")
    void connect_newConnection_createsConnectionAndProjects() {
        given(apiClient.exchangeCode("code-abc")).willReturn("ghp_token");
        given(apiClient.getUserInfo("ghp_token")).willReturn(new GithubUserInfoDto(42L, "testuser"));
        given(connectionRepo.findByUserIdAndGithubUserId(USER_ID, "42")).willReturn(Optional.empty());

        GithubConnection savedConnection = savedConnection(10L, USER_ID, "42", "testuser", "ghp_token");
        given(connectionRepo.save(any())).willReturn(savedConnection);

        List<GithubRepoDto> repos = List.of(
                new GithubRepoDto("N1", "testuser/repo-a", "https://github.com/testuser/repo-a", "Java", "main", false),
                new GithubRepoDto("N2", "testuser/repo-b", "https://github.com/testuser/repo-b", "Python", "main", false)
        );
        given(apiClient.listUserRepos("ghp_token")).willReturn(repos);
        given(projectRepo.findByUserIdAndRepoFullName(eq(USER_ID), anyString())).willReturn(Optional.empty());
        GithubProject savedProject = GithubProject.create(USER_ID, 10L, "N1", "testuser/repo-a",
                "https://github.com/testuser/repo-a", "Java", "main");
        ReflectionTestUtils.setField(savedProject, "id", 100L);
        given(projectRepo.save(any())).willReturn(savedProject);
        given(projectRepo.saveAndFlush(any())).willReturn(savedProject);
        given(metadataFetcher.fetch(anyString(), anyString(), anyString(), anyString()))
                .willReturn(sampleMetadata());

        GithubConnectionService.ConnectResult result = service.connect(USER_ID, "code-abc");

        assertThat(result.connectionId()).isEqualTo(10L);
        assertThat(result.githubLogin()).isEqualTo("testuser");
        verify(connectionRepo).save(any(GithubConnection.class));
        verify(metadataFetcher, times(2)).fetch(eq("ghp_token"), eq("testuser"), anyString(), eq("testuser"));
        // 2 repos: saveAndFlush(create) + save(metadata update) each
        verify(projectRepo, times(2)).saveAndFlush(any(GithubProject.class));
        verify(projectRepo, times(2)).save(any(GithubProject.class));
    }

    @Test
    @DisplayName("기존 연결 재연결: access_token 업데이트, 프로젝트 upsert")
    void connect_existingConnection_updatesToken() {
        given(apiClient.exchangeCode("code-abc")).willReturn("ghp_new_token");
        given(apiClient.getUserInfo("ghp_new_token")).willReturn(new GithubUserInfoDto(42L, "testuser"));

        GithubConnection existing = savedConnection(10L, USER_ID, "42", "testuser", "ghp_old_token");
        given(connectionRepo.findByUserIdAndGithubUserId(USER_ID, "42")).willReturn(Optional.of(existing));
        given(connectionRepo.save(existing)).willReturn(existing);
        given(apiClient.listUserRepos("ghp_new_token")).willReturn(List.of());

        GithubConnectionService.ConnectResult result = service.connect(USER_ID, "code-abc");

        assertThat(result.connectionId()).isEqualTo(10L);
        verify(connectionRepo, never()).save(argThat(c -> c != existing));
        assertThat(existing.getAccessToken()).isEqualTo("ghp_new_token");
    }

    @Test
    @DisplayName("저장소 목록 조회 — 연결 소유권 확인 후 DB에서 반환")
    void listRepositories_ownershipCheck() {
        GithubConnection conn = savedConnection(10L, USER_ID, "42", "testuser", "token");
        given(connectionRepo.findByIdAndUserId(10L, USER_ID)).willReturn(Optional.of(conn));
        GithubProject p = GithubProject.create(USER_ID, 10L, "N1", "testuser/repo", "https://...", "Java", "main");
        ReflectionTestUtils.setField(p, "id", 200L);
        given(projectRepo.findByGithubConnectionIdAndUserId(10L, USER_ID)).willReturn(List.of(p));

        List<GithubProject> repos = service.listRepositories(USER_ID, 10L);

        assertThat(repos).hasSize(1);
        assertThat(repos.get(0).getRepoFullName()).isEqualTo("testuser/repo");
    }

    // ── helpers ──

    private static GithubConnection savedConnection(Long id, Long userId, String githubUserId,
                                                     String login, String token) {
        GithubConnection c = GithubConnection.connect(userId, githubUserId, login, GithubAccessType.OAUTH, token);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private static RepoMetadata sampleMetadata() {
        return new RepoMetadata("# readme", Map.of("Java", 1000L), List.of(), List.of(), List.of(), List.of());
    }
}
