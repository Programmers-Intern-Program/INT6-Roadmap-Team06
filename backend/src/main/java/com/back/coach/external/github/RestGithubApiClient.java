package com.back.coach.external.github;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class RestGithubApiClient implements GithubApiClient {

    private static final Logger log = LoggerFactory.getLogger(RestGithubApiClient.class);
    private static final ParameterizedTypeReference<List<RepositoryResponse>> REPOSITORY_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final GithubApiProperties properties;
    private final RestClient restClient;

    @Autowired
    public RestGithubApiClient(GithubApiProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .requestFactory(new SimpleClientHttpRequestFactory())
                .baseUrl(properties.baseUrl())
                .build();
    }

    RestGithubApiClient(GithubApiProperties properties) {
        this(properties, RestClient.builder());
    }

    @Override
    public List<GithubRepositoryMetadata> listAuthenticatedUserRepositories(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "GitHub access token is empty");
        }
        try {
            List<RepositoryResponse> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user/repos")
                            .queryParam("per_page", 100)
                            .queryParam("sort", "updated")
                            .queryParam("affiliation", "owner,collaborator,organization_member")
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> setHeaders(headers, accessToken))
                    .retrieve()
                    .onStatus(status -> status.value() == HttpStatus.UNAUTHORIZED.value(),
                            (request, responseEntity) -> {
                                throw new ServiceException(ErrorCode.UNAUTHORIZED);
                            })
                    .onStatus(status -> status.value() == HttpStatus.FORBIDDEN.value(),
                            (request, responseEntity) -> {
                                throw new ServiceException(ErrorCode.FORBIDDEN);
                            })
                    .onStatus(status -> status.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
                            (request, responseEntity) -> {
                                throw new ServiceException(ErrorCode.RATE_LIMIT_EXCEEDED);
                            })
                    .onStatus(status -> status.isError(),
                            (request, responseEntity) -> {
                                throw new ServiceException(ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE);
                            })
                    .body(REPOSITORY_LIST_TYPE);

            if (response == null) {
                throw new ServiceException(ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE);
            }
            return response.stream()
                    .map(RepositoryResponse::toMetadata)
                    .toList();
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            ErrorCode mapped = classify(e);
            log.warn("GitHub API call failed (baseUrl={}, code={}, type={}, message={})",
                    properties.baseUrl(), mapped, e.getClass().getSimpleName(), e.getMessage());
            throw new ServiceException(mapped, mapped.getDefaultMessage());
        }
    }

    private void setHeaders(HttpHeaders headers, String accessToken) {
        headers.setBearerAuth(accessToken);
        headers.set(HttpHeaders.ACCEPT, "application/vnd.github+json");
    }

    private ErrorCode classify(RuntimeException e) {
        if (e instanceof ResourceAccessException) {
            return ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE;
        }
        return ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE;
    }

    private record RepositoryResponse(
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("language") String language,
            @JsonProperty("default_branch") String defaultBranch
    ) {

        private GithubRepositoryMetadata toMetadata() {
            return new GithubRepositoryMetadata(nodeId, fullName, htmlUrl, language, defaultBranch);
        }
    }
}
