package com.back.coach.domain.job.service;

import com.back.coach.global.code.JobStatus;

import java.time.Instant;

public record JobHistoryEntry(
        String jobId,
        String jobType,
        JobStatus status,
        String currentStep,
        String error,
        String resultId,
        Instant recordedAt
) {
}
