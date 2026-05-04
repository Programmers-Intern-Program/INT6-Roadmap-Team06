package com.back.coach.domain.github.dto;

import jakarta.validation.constraints.NotBlank;

public record GithubConnectionRequest(
        @NotBlank String authorizationCode
) {}
