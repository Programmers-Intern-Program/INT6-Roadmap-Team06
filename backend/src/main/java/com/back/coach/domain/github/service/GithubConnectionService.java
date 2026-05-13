package com.back.coach.domain.github.service;

import com.back.coach.domain.github.entity.GithubConnection;
import com.back.coach.domain.github.entity.GithubProject;
import com.back.coach.domain.github.repository.GithubConnectionRepository;
import com.back.coach.domain.github.repository.GithubProjectRepository;
import com.back.coach.external.github.GithubApiClient;
import com.back.coach.external.github.dto.GithubContributedRepoDto;
import com.back.coach.external.github.dto.GithubRepoDto;
import com.back.coach.external.github.dto.GithubUserInfoDto;
import com.back.coach.global.code.GithubAccessType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class GithubConnectionService {

    private static final Logger log = LoggerFactory.getLogger(GithubConnectionService.class);

    /** GraphQL contributionsCollection을 몇 년치까지 가져올지 (0, 1, 2 → 최근 3년). */
    private static final int CONTRIBUTED_YEARS_RANGE = 3;

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
        // 1) REST /user/repos: 사용자가 소유하거나 명시적 collaborator로 등록된 repo
        List<GithubRepoDto> repos = apiClient.listUserRepos(accessToken);
        Set<String> processed = new HashSet<>();
        for (GithubRepoDto repo : repos) {
            String ownerType = repo.owner() != null && login.equals(repo.owner().login())
                    ? "owner"
                    : "collaborator";
            upsertProject(userId, connection.getId(), repo, ownerType);
            processed.add(repo.fullName());
        }

        // 2) GraphQL contributionsCollection: 사용자가 커밋을 기여한 public repo (fork/PR 포함)
        //    REST 결과에 이미 포함된 repo는 건너뛴다.
        //    GraphQL은 1년 단위 호출만 허용하므로 yearsOffset=0..2 (최근 3년)로 분할 호출.
        for (int offset = 0; offset < CONTRIBUTED_YEARS_RANGE; offset++) {
            List<GithubContributedRepoDto> contributed;
            try {
                contributed = apiClient.getContributedRepos(accessToken, offset);
            } catch (RuntimeException e) {
                // GraphQL 일부 실패는 sync 전체를 중단시키지 않는다.
                log.warn("getContributedRepos failed yearsOffset={}: {}", offset, e.getMessage());
                continue;
            }
            for (GithubContributedRepoDto repo : contributed) {
                if (repo.fullName() == null || processed.contains(repo.fullName())) continue;
                upsertContributedProject(userId, connection.getId(), repo);
                processed.add(repo.fullName());
            }
        }
    }

    private GithubProject upsertProject(Long userId, Long connectionId, GithubRepoDto repo, String ownerType) {
        return projectRepo.findByUserIdAndRepoFullName(userId, repo.fullName())
                .map(existing -> {
                    existing.setOwnerType(ownerType);
                    return projectRepo.save(existing);
                })
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

    /**
     * GraphQL로 가져온 contributed repo를 저장. 항상 ownerType="collaborator".
     * nodeId / defaultBranch는 GraphQL 응답에 없어 null로 저장하고 분석 시점에 보강한다.
     */
    private GithubProject upsertContributedProject(Long userId, Long connectionId, GithubContributedRepoDto repo) {
        return projectRepo.findByUserIdAndRepoFullName(userId, repo.fullName())
                .map(existing -> {
                    existing.setOwnerType("collaborator");
                    return projectRepo.save(existing);
                })
                .orElseGet(() -> {
                    try {
                        GithubProject p = GithubProject.create(userId, connectionId,
                                null, repo.fullName(), repo.htmlUrl(),
                                repo.primaryLanguage(), null, "collaborator");
                        return projectRepo.saveAndFlush(p);
                    } catch (DataIntegrityViolationException e) {
                        return projectRepo.findByUserIdAndRepoFullName(userId, repo.fullName())
                                .orElseThrow(() -> new ServiceException(ErrorCode.GITHUB_API_ERROR,
                                        "contributed repo upsert failed: " + repo.fullName()));
                    }
                });
    }

    public record ConnectResult(Long connectionId, String githubLogin, Instant connectedAt) {}
}
