package com.back.coach.domain.user.dto;

import java.time.Instant;

public record ProfileSaveResponse(
        String profileId,
        Instant savedAt,
        String message
) {

    public static ProfileSaveResponse from(ProfileSaveResult result) {
        return new ProfileSaveResponse(
                String.valueOf(result.profileId()),
                result.savedAt(),
                "프로필이 저장되었습니다."
        );
    }
}
