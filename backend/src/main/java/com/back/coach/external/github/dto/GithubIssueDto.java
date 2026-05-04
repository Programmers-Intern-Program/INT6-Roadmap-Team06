package com.back.coach.external.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubIssueDto(
        @JsonProperty("number") int number,
        @JsonProperty("title") String title,
        @JsonProperty("body") String body,
        @JsonProperty("state") String state,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("user") User user,
        @JsonProperty("pull_request") Object pullRequest
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(@JsonProperty("login") String login) {}

    public boolean isPullRequest() {
        return pullRequest != null;
    }
}
