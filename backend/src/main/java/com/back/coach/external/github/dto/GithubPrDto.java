package com.back.coach.external.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubPrDto(
        @JsonProperty("number") int number,
        @JsonProperty("title") String title,
        @JsonProperty("body") String body,
        @JsonProperty("state") String state,
        @JsonProperty("additions") Integer additions,
        @JsonProperty("deletions") Integer deletions,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("user") User user
) {
    public GithubPrDto {
        additions = additions == null ? 0 : additions;
        deletions = deletions == null ? 0 : deletions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(@JsonProperty("login") String login) {}
}
