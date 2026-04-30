package com.back.coach.external.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubCommitDto(
        @JsonProperty("sha") String sha,
        @JsonProperty("commit") CommitInfo commit
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommitInfo(
            @JsonProperty("message") String message,
            @JsonProperty("committer") Committer committer
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Committer(
            @JsonProperty("date") String date
    ) {}
}
