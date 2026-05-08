package com.back.coach.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // 인증 / 인가
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    INVALID_OAUTH_STATE(HttpStatus.BAD_REQUEST, "OAuth 인증 state가 올바르지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "해당 리소스에 접근할 권한이 없습니다."),

    // 사용자
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // 공통
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값을 다시 확인해주세요."),
    CONFLICT(HttpStatus.CONFLICT, "현재 상태와 충돌하는 요청입니다."),
    UNSUPPORTED_FILE_FORMAT(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "외부 서비스에 일시적인 문제가 발생했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다."),

    // 분석 / 로드맵 (외부 노출 코드 — docs/03_api_spec_aligned.md)
    ANALYSIS_FAILED(HttpStatus.BAD_GATEWAY, "분석에 실패했습니다. 잠시 후 다시 시도해주세요."),
    ROADMAP_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "로드맵 생성에 실패했습니다. 잠시 후 다시 시도해주세요."),

    // LLM 내부 코드 — 호출 지점에서 ANALYSIS_FAILED / ROADMAP_GENERATION_FAILED / RATE_LIMIT_EXCEEDED 로 매핑
    LLM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "LLM 응답 시간이 초과되었습니다."),
    LLM_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "LLM 호출 제한에 도달했습니다."),
    LLM_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "LLM 응답을 해석할 수 없습니다."),
    LLM_TRIAGE_FAILED(HttpStatus.BAD_GATEWAY, "코드 분석에 실패했습니다."),

    // GitHub API
    GITHUB_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "GitHub API 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."),
    GITHUB_API_ERROR(HttpStatus.BAD_GATEWAY, "GitHub API 오류가 발생했습니다."),

    // Pattern Detector
    PATTERN_DETECTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "패턴 감지 중 오류가 발생했습니다."),

    // Coach
    SNAPSHOT_NOT_FOUND(HttpStatus.NOT_FOUND, "활성화된 컨텍스트 스냅샷이 없습니다. 프로필과 로드맵을 먼저 생성해주세요."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),
    SESSION_CLOSED(HttpStatus.CONFLICT, "이미 종료된 세션입니다."),
    PROPOSAL_NOT_FOUND(HttpStatus.NOT_FOUND, "재계획 제안을 찾을 수 없습니다."),
    PROPOSAL_EXPIRED(HttpStatus.GONE, "재계획 제안이 만료됐거나 이미 처리됐습니다."),
    PLANNER_FAILED(HttpStatus.BAD_GATEWAY, "로드맵 재생성에 실패했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
