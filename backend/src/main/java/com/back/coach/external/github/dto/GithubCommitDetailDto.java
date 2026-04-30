package com.back.coach.external.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubCommitDetailDto(
        @JsonProperty("sha") String sha,
        @JsonProperty("commit") CommitInfo commit,
        @JsonProperty("stats") Stats stats,
        @JsonProperty("files") List<FileChange> files
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommitInfo(
            @JsonProperty("message") String message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stats(
            @JsonProperty("additions") int additions,
            @JsonProperty("deletions") int deletions
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FileChange(
            @JsonProperty("filename") String filename,
            @JsonProperty("patch") String patch
    ) {}
}
