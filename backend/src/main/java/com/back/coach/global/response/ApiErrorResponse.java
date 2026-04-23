package com.back.coach.global.response;

import com.back.coach.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Error response shape per docs/03_api_spec_aligned.md:
 *   { "code": "...", "message": "...", "details": { ... } }
 *
 * details may carry: traceId, fieldErrors, or any error-specific context.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String code,
        String message,
        Map<String, Object> details
) {

    public static ApiErrorResponse of(ErrorCode errorCode, String message) {
        return new ApiErrorResponse(errorCode.name(), message, null);
    }

    public static ApiErrorResponse of(ErrorCode errorCode, String message, Map<String, Object> details) {
        Map<String, Object> normalized = (details == null || details.isEmpty()) ? null : new LinkedHashMap<>(details);
        return new ApiErrorResponse(errorCode.name(), message, normalized);
    }

    public static ApiErrorResponse withFieldErrors(ErrorCode errorCode, String message, List<FieldErrorDetail> fieldErrors) {
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            return of(errorCode, message);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fieldErrors", List.copyOf(fieldErrors));
        return new ApiErrorResponse(errorCode.name(), message, details);
    }

    /**
     * Returns a copy with traceId added under details. Null/blank traceId returns this unchanged.
     */
    public ApiErrorResponse withTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return this;
        }
        Map<String, Object> next = (details == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
        next.put("traceId", traceId);
        return new ApiErrorResponse(code, message, next);
    }
}
