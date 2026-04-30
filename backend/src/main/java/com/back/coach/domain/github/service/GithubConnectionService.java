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
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class GithubConnectionService {

    private static final Logger log = LoggerFactory.getLogger(GithubConnectionService.class);

    private final GithubApiClient apiClient;
    private final GithubConnectionRepository connectionRepo;
    private final GithubProjectRepository projectRepo;
    private final GithubMetadataFetcher metadataFetcher;
    private final ObjectMapper objectMapper;

    public GithubConnectionService(GithubApiClient apiClient,
                                   GithubConnectionRepository connectionRepo,
                                   GithubProjectRepository projectRepo,
                                   GithubMetadataFetcher metadataFetcher) {
        this.apiClient = apiClient;
        this.connectionRepo = connectionRepo;
        this.projectRepo = projectRepo;
        this.metadataFetcher = metadataFetcher;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public ConnectResult connect(Long userId, String authorizationCode) {
        String accessToken = apiClient.exchangeCode(authorizationCode);
        GithubUserInfoDto userInfo = apiClient.getUserInfo(accessToken);

        GithubConnection connection = upsertConnection(userId, userInfo, accessToken);
        syncRepos(userId, connection, userInfo.login(), accessToken);

        return new ConnectResult(connection.getId(), connection.getGithubLogin(), connection.getConnectedAt());
    }

    public List<GithubProject> listRepositories(Long userId, Long connectionId) {
        connectionRepo.findByIdAndUserId(connectionId, userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));
        return projectRepo.findByGithubConnectionIdAndUserId(connectionId, userId);
    }

    private GithubConnection upsertConnection(Long userId, GithubUserInfoDto userInfo, String accessToken) {
        return connectionRepo.findByUserIdAndGithubUserId(userId, userInfo.id())
                .map(existing -> {
                    existing.updateAccessToken(accessToken);
                    existing.updateLogin(userInfo.login());
                    return connectionRepo.save(existing);
                })
                .orElseGet(() -> connectionRepo.save(
                        GithubConnection.connect(userId, userInfo.id(), userInfo.login(),
                                GithubAccessType.OAUTH, accessToken)));
    }

    private void syncRepos(Long userId, GithubConnection connection, String login, String accessToken) {
        List<GithubRepoDto> repos = apiClient.listUserRepos(accessToken);
        for (GithubRepoDto repo : repos) {
            String[] parts = repo.fullName().split("/", 2);
            String owner = parts[0];
            String repoName = parts.length > 1 ? parts[1] : parts[0];

            GithubProject project = upsertProject(userId, connection.getId(), repo);
            fetchAndUpdateMetadata(project, accessToken, owner, repoName, login);
        }
    }

    private GithubProject upsertProject(Long userId, Long connectionId, GithubRepoDto repo) {
        return projectRepo.findByUserIdAndRepoFullName(userId, repo.fullName())
                .map(existing -> existing)
                .orElseGet(() -> {
                    GithubProject p = GithubProject.create(userId, connectionId,
                            repo.nodeId(), repo.fullName(), repo.htmlUrl(),
                            repo.language(), repo.defaultBranch());
                    return projectRepo.save(p);
                });
    }

    private void fetchAndUpdateMetadata(GithubProject project, String accessToken,
                                         String owner, String repo, String login) {
        try {
            RepoMetadata metadata = metadataFetcher.fetch(accessToken, owner, repo, login);
            String json = objectMapper.writeValueAsString(metadata);
            project.updateMetadataPayload(json);
            projectRepo.save(project);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize metadata for {}/{}: {}", owner, repo, e.getMessage());
        } catch (ServiceException e) {
            log.warn("GitHub API error fetching metadata for {}/{}: {} {}", owner, repo,
                    e.getErrorCode(), e.getMessage());
        }
    }

    public record ConnectResult(Long connectionId, String githubLogin, Instant connectedAt) {}
}
