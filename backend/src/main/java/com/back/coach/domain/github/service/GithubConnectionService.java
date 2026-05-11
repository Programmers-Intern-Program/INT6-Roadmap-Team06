package com.back.coach.domain.github.service;

import com.back.coach.domain.github.entity.GithubConnection;
import com.back.coach.domain.github.entity.GithubProject;
import com.back.coach.domain.github.repository.GithubConnectionRepository;
import com.back.coach.domain.github.repository.GithubProjectRepository;
import com.back.coach.external.github.GithubApiClient;
import com.back.coach.external.github.dto.GithubRepoDto;
import com.back.coach.external.github.dto.GithubUserInfoDto;
import com.back.coach.global.code.GithubAccessType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class GithubConnectionService {

    private final GithubApiClient apiClient;
    private final GithubConnectionRepository connectionRepo;
    private final GithubProjectRepository projectRepo;

    public GithubConnectionService(GithubApiClient apiClient,
                                   GithubConnectionRepository connectionRepo,
                                   GithubProjectRepository projectRepo) {
        this.apiClient = apiClient;
        this.connectionRepo = connectionRepo;
        this.projectRepo = projectRepo;
    }

    @Transactional
    public ConnectResult connect(Long userId, String authorizationCode) {
        String accessToken = apiClient.exchangeCode(authorizationCode);
        GithubUserInfoDto userInfo = apiClient.getUserInfo(accessToken);

        GithubConnection connection = upsertConnection(userId, userInfo, accessToken);
        syncRepos(userId, connection, userInfo.login(), accessToken);

        return new ConnectResult(connection.getId(), connection.getGithubLogin(), connection.getConnectedAt());
    }

    @Transactional(readOnly = true)
    public GithubConnection findLatestConnection(Long userId) {
        return connectionRepo.findFirstByUserIdOrderByConnectedAtDesc(userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public List<GithubProject> listRepositories(Long userId, Long connectionId) {
        connectionRepo.findByIdAndUserId(connectionId, userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));
        return projectRepo.findByGithubConnectionIdAndUserId(connectionId, userId);
    }

    private GithubConnection upsertConnection(Long userId, GithubUserInfoDto userInfo, String accessToken) {
        String githubUserId = String.valueOf(userInfo.id());
        return connectionRepo.findByUserIdAndGithubUserId(userId, githubUserId)
                .map(existing -> {
                    existing.updateAccessToken(accessToken);
                    existing.updateLogin(userInfo.login());
                    return connectionRepo.save(existing);
                })
                .orElseGet(() -> connectionRepo.save(
                        GithubConnection.connect(userId, githubUserId, userInfo.login(),
                                GithubAccessType.OAUTH, accessToken)));
    }

    private void syncRepos(Long userId, GithubConnection connection, String login, String accessToken) {
        List<GithubRepoDto> repos = apiClient.listUserRepos(accessToken);
        for (GithubRepoDto repo : repos) {
            String ownerType = repo.owner() != null && login.equals(repo.owner().login())
                    ? "owner"
                    : "collaborator";
            upsertProject(userId, connection.getId(), repo, ownerType);
        }
    }

    private GithubProject upsertProject(Long userId, Long connectionId, GithubRepoDto repo, String ownerType) {
        return projectRepo.findByUserIdAndRepoFullName(userId, repo.fullName())
                .orElseGet(() -> {
                    try {
                        GithubProject p = GithubProject.create(userId, connectionId,
                                repo.nodeId(), repo.fullName(), repo.htmlUrl(),
                                repo.language(), repo.defaultBranch(), ownerType);
                        return projectRepo.saveAndFlush(p);
                    } catch (DataIntegrityViolationException e) {
                        return projectRepo.findByUserIdAndRepoFullName(userId, repo.fullName())
                                .orElseThrow(() -> new ServiceException(ErrorCode.GITHUB_API_ERROR,
                                        "repo upsert failed: " + repo.fullName()));
                    }
                });
    }

    public record ConnectResult(Long connectionId, String githubLogin, Instant connectedAt) {}
}
