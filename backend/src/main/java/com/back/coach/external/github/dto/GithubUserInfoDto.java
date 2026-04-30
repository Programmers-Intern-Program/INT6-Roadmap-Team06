package com.back.coach.external.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubUserInfoDto(
        @JsonProperty("id") Long id,    // GitHub API returns integer
        @JsonProperty("login") String login
) {}
