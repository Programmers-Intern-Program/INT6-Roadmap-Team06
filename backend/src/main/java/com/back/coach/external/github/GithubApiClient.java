package com.back.coach.external.github;

import com.back.coach.external.github.dto.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface GithubApiClient {

    String exchangeCode(String code);

    GithubUserInfoDto getUserInfo(String accessToken);

    List<GithubRepoDto> listUserRepos(String accessToken);

    Optional<String> getReadme(String accessToken, String owner, String repo);

    Map<String, Long> getLanguages(String accessToken, String owner, String repo);

    Optional<String> getFileContent(String accessToken, String owner, String repo, String path);

    List<GithubCommitDto> listCommits(String accessToken, String owner, String repo, String author, int perPage);

    GithubCommitDetailDto getCommitDetail(String accessToken, String owner, String repo, String sha);

    List<GithubPrDto> listPullRequests(String accessToken, String owner, String repo);

    List<GithubIssueDto> listIssues(String accessToken, String owner, String repo);
}
