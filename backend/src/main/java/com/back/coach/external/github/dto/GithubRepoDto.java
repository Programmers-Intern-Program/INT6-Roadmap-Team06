package com.back.coach.external.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubRepoDto(
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("language") String language,
        @JsonProperty("default_branch") String defaultBranch,
        @JsonProperty("fork") boolean fork
) {}
