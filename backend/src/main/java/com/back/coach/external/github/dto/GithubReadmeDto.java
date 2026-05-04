package com.back.coach.external.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubReadmeDto(
        @JsonProperty("content") String content,
        @JsonProperty("encoding") String encoding
) {}
