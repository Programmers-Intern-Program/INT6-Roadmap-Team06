package com.back.coach.global.response;

import java.util.LinkedHashMap;
import java.util.Map;

public record ApiResponse<T>(
        T data,
        Map<String, Object> meta
) {

    public ApiResponse {
        meta = normalizeMeta(meta);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> success(T data, Map<String, Object> meta) {
        return new ApiResponse<>(data, meta);
    }

    private static Map<String, Object> normalizeMeta(Map<String, Object> meta) {
        return (meta == null || meta.isEmpty()) ? Map.of() : new LinkedHashMap<>(meta);
    }
}
