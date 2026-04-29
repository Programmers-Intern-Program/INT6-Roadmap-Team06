package com.back.coach.api.github.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GithubAnalysisRequest(
        @NotNull Long githubConnectionId,
        @NotEmpty List<Long> selectedRepositoryIds,
        // 비어 있으면 selected 전체를 core로 간주하지 않고, 명시적으로 빈 list를 허용 (분석 거의 무의미하지만 service가 INVALID_INPUT으로 막음).
        @NotNull List<Long> coreRepositoryIds
) {}
