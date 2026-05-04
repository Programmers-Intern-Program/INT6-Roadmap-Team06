package com.back.coach.external.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubFileContentDto(
        @JsonProperty("content") String content,
        @JsonProperty("encoding") String encoding
) {}
