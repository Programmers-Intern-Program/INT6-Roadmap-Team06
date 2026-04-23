package com.back.coach.global.response;

public record FieldErrorDetail(
        String field,
        String reason
) {
}
