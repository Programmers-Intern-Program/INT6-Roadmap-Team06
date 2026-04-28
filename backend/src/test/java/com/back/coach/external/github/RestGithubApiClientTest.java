package com.back.coach.external.github;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestGithubApiClientTest {

    private WireMockServer wireMock;
    private RestGithubApiClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        client = new RestGithubApiClient(
                new GithubApiProperties("http://127.0.0.1:" + wireMock.port())
        );
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void blankAccessToken_throwsInvalidInput() {
        assertThatThrownBy(() -> client.listAuthenticatedUserRepositories(""))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThatThrownBy(() -> client.listAuthenticatedUserRepositories(null))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void listAuthenticatedUserRepositories_callsGithubAndMapsRepositoryMetadata() {
        wireMock.stubFor(get(urlPathEqualTo("/user/repos"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {
                                    "node_id": "R_kgDO123",
                                    "full_name": "team06/coach",
                                    "html_url": "https://github.com/team06/coach",
                                    "language": "Java",
                                    "default_branch": "main"
                                  }
                                ]
                                """)));

        List<GithubRepositoryMetadata> result = client.listAuthenticatedUserRepositories("token-123");

        assertThat(result).containsExactly(new GithubRepositoryMetadata(
                "R_kgDO123",
                "team06/coach",
                "https://github.com/team06/coach",
                "Java",
                "main"
        ));
        wireMock.verify(getRequestedFor(urlPathEqualTo("/user/repos"))
                .withHeader("Authorization", equalTo("Bearer token-123"))
                .withHeader("Accept", equalTo("application/vnd.github+json"))
                .withQueryParam("per_page", equalTo("100"))
                .withQueryParam("sort", equalTo("updated"))
                .withQueryParam("affiliation", equalTo("owner,collaborator,organization_member")));
    }

    @Test
    void unauthorizedResponse_throwsUnauthorized() {
        wireMock.stubFor(get(urlPathEqualTo("/user/repos"))
                .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> client.listAuthenticatedUserRepositories("bad-token"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void forbiddenResponse_throwsForbidden() {
        wireMock.stubFor(get(urlPathEqualTo("/user/repos"))
                .willReturn(aResponse().withStatus(403)));

        assertThatThrownBy(() -> client.listAuthenticatedUserRepositories("token-123"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void rateLimitedResponse_throwsRateLimitExceeded() {
        wireMock.stubFor(get(urlPathEqualTo("/user/repos"))
                .willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> client.listAuthenticatedUserRepositories("token-123"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    @Test
    void serverErrorResponse_throwsExternalServiceUnavailable() {
        wireMock.stubFor(get(urlPathEqualTo("/user/repos"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.listAuthenticatedUserRepositories("token-123"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE);
    }

    @Test
    void properties_rejectsBlankBaseUrl() {
        assertThatThrownBy(() -> new GithubApiProperties(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("github.api.base-url must not be blank");
    }
}
