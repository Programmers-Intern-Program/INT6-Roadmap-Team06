package com.back.coach.domain.coach.dto;

public record ReplanResponse(
        String newRoadmapId,
        Integer newRoadmapVersion,
        String message,
        Boolean dismissed
) {
    public static ReplanResponse confirmed(String roadmapId, Integer version) {
        return new ReplanResponse(
                String.valueOf(roadmapId),
                version,
                "새 로드맵이 생성됐습니다. 현재 세션은 기존 버전을 기준으로 유지됩니다.",
                null
        );
    }

    public static ReplanResponse deferred() {
        return new ReplanResponse(null, null, "재계획을 보류했습니다.", true);
    }
}
