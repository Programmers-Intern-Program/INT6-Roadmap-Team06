package com.back.coach.external.github;

import java.util.List;

public interface GithubApiClient {

    List<GithubRepositoryMetadata> listAuthenticatedUserRepositories(String accessToken);
}
