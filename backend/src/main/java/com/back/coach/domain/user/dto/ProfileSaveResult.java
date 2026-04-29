package com.back.coach.domain.user.dto;

import java.time.Instant;

public record ProfileSaveResult(
        Long profileId,
        Instant savedAt
) {
}
