package com.back.coach.external.github;

import com.back.coach.external.github.dto.*;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class RestGithubApiClient implements GithubApiClient {

    private static final Logger log = LoggerFactory.getLogger(RestGithubApiClient.class);

    private static final int NOT_FOUND = 404;

    private final GithubApiProperties properties;
    private final RestClient apiClient;
    private final RestClient oauthClient;

    public RestGithubApiClient(GithubApiProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.apiClient = builder.clone()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.oauthClient = builder.clone()
                .baseUrl(properties.oauthBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // package-private for testing
    RestGithubApiClient(GithubApiProperties properties) {
        this(properties, RestClient.builder());
    }

    @Override
    public String exchangeCode(String code) {
        try {
            String body = "client_id=" + properties.connectionClientId()
                    + "&client_secret=" + properties.connectionClientSecret()
                    + "&code=" + code;

            String response = oauthClient.post()
                    .uri("/login/oauth/access_token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .onStatus(s -> s.isError(), (req, res) -> {
                        throw new ServiceException(ErrorCode.GITHUB_API_ERROR, "OAuth code exchange failed");
                    })
                    .body(String.class);

            return parseAccessToken(response);
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ServiceException(classify(e), e.getMessage());
        }
    }

    @Override
    public GithubUserInfoDto getUserInfo(String accessToken) {
        return get("/user", accessToken, GithubUserInfoDto.class);
    }

    @Override
    public List<GithubRepoDto> listUserRepos(String accessToken) {
        // affiliation에 organization_member 포함: collaborator로 명시 추가되지 않은 org repo도 노출.
        // per_page=100 + Link header rel="next"로 페이지 순회. 사용자별 ~1000 repo까지 안전 처리.
        URI uri = UriComponentsBuilder.fromUriString("/user/repos")
                .queryParam("affiliation", "owner,collaborator,organization_member")
                .queryParam("per_page", 100)
                .build().toUri();
        List<GithubRepoDto> all = new ArrayList<>();
        for (int page = 0; page < MAX_REPO_PAGES && uri != null; page++) {
            ResponseEntity<List<GithubRepoDto>> response = getListWithHeaders(
                    uri, accessToken, new ParameterizedTypeReference<>() {});
            List<GithubRepoDto> body = response.getBody();
            if (body != null) all.addAll(body);
            uri = nextPageUri(response.getHeaders().getFirst(HttpHeaders.LINK));
        }
        return all;
    }

    private static final int MAX_REPO_PAGES = 10;
    private static final Pattern NEXT_LINK_PATTERN =
            Pattern.compile("<([^>]+)>\\s*;\\s*rel=\"next\"");

    private static URI nextPageUri(String linkHeader) {
        if (linkHeader == null || linkHeader.isBlank()) return null;
        Matcher m = NEXT_LINK_PATTERN.matcher(linkHeader);
        return m.find() ? URI.create(m.group(1)) : null;
    }

    @Override
    public Optional<String> getReadme(String accessToken, String owner, String repo) {
        try {
            GithubReadmeDto dto = get("/repos/" + owner + "/" + repo + "/readme", accessToken, GithubReadmeDto.class);
            return Optional.of(decodeBase64Content(dto.content()));
        } catch (ServiceException e) {
            if (isNotFound(e)) return Optional.empty();
            throw e;
        }
    }

    @Override
    public Map<String, Long> getLanguages(String accessToken, String owner, String repo) {
        try {
            Map<String, Long> result = apiClient.get()
                    .uri("/repos/" + owner + "/" + repo + "/languages")
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .onStatus(s -> s.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
                            (req, res) -> { throw new ServiceException(ErrorCode.GITHUB_RATE_LIMITED); })
                    .onStatus(s -> s.value() == NOT_FOUND,
                            (req, res) -> { throw new ServiceException(ErrorCode.RESOURCE_NOT_FOUND); })
                    .onStatus(s -> s.isError(),
                            (req, res) -> { throw new ServiceException(ErrorCode.GITHUB_API_ERROR); })
                    .body(new ParameterizedTypeReference<>() {});
            return result == null ? Map.of() : result;
        } catch (ServiceException e) {
            if (isNotFound(e)) return Map.of();
            throw e;
        } catch (RuntimeException e) {
            throw new ServiceException(classify(e), e.getMessage());
        }
    }

    @Override
    public Optional<String> getFileContent(String accessToken, String owner, String repo, String path) {
        try {
            GithubFileContentDto dto = get(
                    "/repos/" + owner + "/" + repo + "/contents/" + path, accessToken, GithubFileContentDto.class);
            return Optional.of(decodeBase64Content(dto.content()));
        } catch (ServiceException e) {
            if (isNotFound(e)) return Optional.empty();
            throw e;
        }
    }

    @Override
    public List<GithubCommitDto> listCommits(String accessToken, String owner, String repo,
                                              String author, int perPage) {
        URI uri = UriComponentsBuilder.fromUriString("/repos/" + owner + "/" + repo + "/commits")
                .queryParam("author", author)
                .queryParam("per_page", perPage)
                .build().toUri();
        try {
            return getList(uri, accessToken, new ParameterizedTypeReference<>() {});
        } catch (ServiceException e) {
            if (ErrorCode.GITHUB_API_ERROR.equals(e.getErrorCode())) {
                log.debug("listCommits returned 409 or error for {}/{}, returning empty list", owner, repo);
                return List.of();
            }
            throw e;
        }
    }

    @Override
    public GithubCommitDetailDto getCommitDetail(String accessToken, String owner, String repo, String sha) {
        return get("/repos/" + owner + "/" + repo + "/commits/" + sha, accessToken, GithubCommitDetailDto.class);
    }

    @Override
    public List<GithubPrDto> listPullRequests(String accessToken, String owner, String repo) {
        URI uri = UriComponentsBuilder.fromUriString("/repos/" + owner + "/" + repo + "/pulls")
                .queryParam("state", "all")
                .queryParam("per_page", 50)
                .queryParam("sort", "created")
                .queryParam("direction", "asc")
                .build().toUri();
        return getList(uri, accessToken, new ParameterizedTypeReference<>() {});
    }

    @Override
    public List<GithubIssueDto> listIssues(String accessToken, String owner, String repo) {
        URI uri = UriComponentsBuilder.fromUriString("/repos/" + owner + "/" + repo + "/issues")
                .queryParam("state", "all")
                .queryParam("per_page", 50)
                .queryParam("sort", "created")
                .queryParam("direction", "asc")
                .build().toUri();
        return getList(uri, accessToken, new ParameterizedTypeReference<>() {});
    }

    private static final String CONTRIBUTED_REPOS_QUERY = """
            query($from: DateTime!, $to: DateTime!) {
              viewer {
                contributionsCollection(from: $from, to: $to) {
                  commitContributionsByRepository {
                    repository {
                      nameWithOwner
                      url
                      isPrivate
                      databaseId
                      primaryLanguage { name }
                    }
                    contributions { totalCount }
                  }
                }
              }
            }
            """;

    @Override
    public List<GithubContributedRepoDto> getContributedRepos(String accessToken, int yearsOffset) {
        // GitHub GraphQL contributionsCollection: from~to 범위는 최대 1년 (365일)
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime to = now.minusYears(yearsOffset);
        ZonedDateTime from = now.minusYears(yearsOffset + 1L);

        String fromStr = from.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String toStr = to.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        try {
            GraphQLResponse response = apiClient.post()
                    .uri("/graphql")
                    .headers(h -> h.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "query", CONTRIBUTED_REPOS_QUERY,
                            "variables", Map.of("from", fromStr, "to", toStr)
                    ))
                    .retrieve()
                    .onStatus(s -> s.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
                            (req, res) -> { throw new ServiceException(ErrorCode.GITHUB_RATE_LIMITED); })
                    .onStatus(s -> s.isError(),
                            (req, res) -> { throw new ServiceException(ErrorCode.GITHUB_API_ERROR); })
                    .body(GraphQLResponse.class);

            if (response == null
                    || response.data() == null
                    || response.data().viewer() == null
                    || response.data().viewer().contributionsCollection() == null) {
                return List.of();
            }

            List<GraphQLRepoContribution> contributions =
                    response.data().viewer().contributionsCollection().commitContributionsByRepository();
            if (contributions == null) return List.of();

            return contributions.stream()
                    .filter(c -> c.repository() != null && !c.repository().isPrivate())
                    .map(c -> new GithubContributedRepoDto(
                            c.repository().databaseId(),
                            c.repository().nameWithOwner(),
                            c.repository().url(),
                            c.repository().primaryLanguage() != null
                                    ? c.repository().primaryLanguage().name() : null,
                            c.contributions() != null ? c.contributions().totalCount() : 0
                    ))
                    .toList();
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("GraphQL contributionsCollection call failed (yearsOffset={}): {}",
                    yearsOffset, e.getMessage());
            throw new ServiceException(classify(e), e.getMessage());
        }
    }

    // GraphQL contributionsCollection 응답 파싱용 내부 record
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphQLResponse(GraphQLData data) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphQLData(GraphQLViewer viewer) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphQLViewer(GraphQLContributions contributionsCollection) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphQLContributions(
            List<GraphQLRepoContribution> commitContributionsByRepository
    ) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphQLRepoContribution(
            GraphQLRepository repository,
            GraphQLContributionCount contributions
    ) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphQLRepository(
            String nameWithOwner,
            String url,
            boolean isPrivate,
            Long databaseId,
            GraphQLLanguage primaryLanguage
    ) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphQLLanguage(String name) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphQLContributionCount(int totalCount) {}

    private <T> T get(String path, String accessToken, Class<T> type) {
        try {
            return apiClient.get()
                    .uri(path)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .onStatus(s -> s.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
                            (req, res) -> { throw new ServiceException(ErrorCode.GITHUB_RATE_LIMITED); })
                    .onStatus(s -> s.value() == NOT_FOUND,
                            (req, res) -> { throw new ServiceException(ErrorCode.RESOURCE_NOT_FOUND); })
                    .onStatus(s -> s.isError(),
                            (req, res) -> { throw new ServiceException(ErrorCode.GITHUB_API_ERROR); })
                    .body(type);
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("GitHub API call failed path={} error={}", path, e.getMessage());
            throw new ServiceException(classify(e), e.getMessage());
        }
    }

    private <T> ResponseEntity<List<T>> getListWithHeaders(
            URI uri, String accessToken, ParameterizedTypeReference<List<T>> type) {
        try {
            return apiClient.get()
                    .uri(uri)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .onStatus(s -> s.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
                            (req, res) -> { throw new ServiceException(ErrorCode.GITHUB_RATE_LIMITED); })
                    .onStatus(s -> s.value() == NOT_FOUND,
                            (req, res) -> { throw new ServiceException(ErrorCode.RESOURCE_NOT_FOUND); })
                    .onStatus(s -> s.isError(), (req, res) -> {
                        log.warn("GitHub API list error: status={} uri={}", res.getStatusCode(), uri);
                        throw new ServiceException(ErrorCode.GITHUB_API_ERROR);
                    })
                    .toEntity(type);
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("GitHub API list call failed uri={} error={}", uri, e.getMessage());
            throw new ServiceException(classify(e), e.getMessage());
        }
    }

    private <T> List<T> getList(URI uri, String accessToken, ParameterizedTypeReference<List<T>> type) {
        try {
            List<T> result = apiClient.get()
                    .uri(uri)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .onStatus(s -> s.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
                            (req, res) -> { throw new ServiceException(ErrorCode.GITHUB_RATE_LIMITED); })
                    .onStatus(s -> s.value() == NOT_FOUND,
                            (req, res) -> { throw new ServiceException(ErrorCode.RESOURCE_NOT_FOUND); })
                    .onStatus(s -> s.isError(), (req, res) -> {
                        log.warn("GitHub API list error: status={} uri={}", res.getStatusCode(), uri);
                        throw new ServiceException(ErrorCode.GITHUB_API_ERROR);
                    })
                    .body(type);
            return result == null ? List.of() : result;
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("GitHub API list call failed uri={} error={}", uri, e.getMessage());
            throw new ServiceException(classify(e), e.getMessage());
        }
    }

    private static String decodeBase64Content(String encoded) {
        if (encoded == null) return "";
        String cleaned = encoded.replaceAll("\\s", "");
        return new String(Base64.getDecoder().decode(cleaned), StandardCharsets.UTF_8);
    }

    private static String parseAccessToken(String responseBody) {
        if (responseBody == null) {
            throw new ServiceException(ErrorCode.GITHUB_API_ERROR, "Empty OAuth response");
        }
        // Form-encoded: access_token=ghp_xxx&token_type=bearer&scope=repo
        for (String part : responseBody.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && "access_token".equals(kv[0])) {
                return kv[1];
            }
        }
        // JSON fallback: {"access_token":"ghp_xxx"}
        if (responseBody.contains("access_token")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> map = new ObjectMapper().readValue(responseBody,
                        new TypeReference<Map<String, String>>() {});
                String token = map.get("access_token");
                if (token != null && !token.isBlank()) return token;
            } catch (Exception ignored) {}
        }
        throw new ServiceException(ErrorCode.GITHUB_API_ERROR, "access_token not found in OAuth response");
    }

    private static boolean isNotFound(ServiceException e) {
        return e.getErrorCode() == ErrorCode.RESOURCE_NOT_FOUND;
    }

    private static ErrorCode classify(RuntimeException e) {
        if (e instanceof ResourceAccessException) return ErrorCode.GITHUB_API_ERROR;
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (msg.contains("rate") && msg.contains("limit")) return ErrorCode.GITHUB_RATE_LIMITED;
        return ErrorCode.GITHUB_API_ERROR;
    }
}
