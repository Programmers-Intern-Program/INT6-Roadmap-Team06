package com.back.coach.service.github;

// Triage가 고른 champion 후보 1개. ref는 commit이면 sha, PR/issue면 number string.
public record Champion(Kind kind, String ref, String reason) {

    public enum Kind { COMMIT, PR, ISSUE }
}
