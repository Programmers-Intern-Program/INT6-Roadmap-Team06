package com.back.coach.domain.job.dto;

import com.back.coach.domain.job.service.JobHistoryEntry;
import com.back.coach.global.code.JobStatus;

import java.time.Instant;

public record JobHistoryResponse(
        String jobId,
        String jobType,
        JobStatus status,
        String currentStep,
        String error,
        String resultId,
        Instant recordedAt
) {

    public static JobHistoryResponse from(JobHistoryEntry entry) {
        return new JobHistoryResponse(
                entry.jobId(),
                entry.jobType(),
                entry.status(),
                entry.currentStep(),
                entry.error(),
                entry.resultId(),
                entry.recordedAt()
        );
    }
}
