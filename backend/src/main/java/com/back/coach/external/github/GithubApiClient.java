package com.back.coach.external.github;

import com.back.coach.external.github.dto.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface GithubApiClient {

    String exchangeCode(String code);

    GithubUserInfoDto getUserInfo(String accessToken);

    List<GithubRepoDto> listUserRepos(String accessToken);

    /**
     * GraphQL contributionsCollection으로 사용자가 커밋을 기여한 public repo를 조회.
     * @param yearsOffset 0=최근 1년, 1=1~2년 전, 2=2~3년 전 (GitHub API는 1년 단위만 허용)
     */
    List<GithubContributedRepoDto> getContributedRepos(String accessToken, int yearsOffset);

    Optional<String> getReadme(String accessToken, String owner, String repo);

    Map<String, Long> getLanguages(String accessToken, String owner, String repo);

    Optional<String> getFileContent(String accessToken, String owner, String repo, String path);

    List<GithubCommitDto> listCommits(String accessToken, String owner, String repo, String author, int perPage);

    GithubCommitDetailDto getCommitDetail(String accessToken, String owner, String repo, String sha);

    List<GithubPrDto> listPullRequests(String accessToken, String owner, String repo);

    List<GithubIssueDto> listIssues(String accessToken, String owner, String repo);
}
