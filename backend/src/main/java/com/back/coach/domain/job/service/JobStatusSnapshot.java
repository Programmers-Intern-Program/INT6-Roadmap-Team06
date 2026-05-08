package com.back.coach.domain.job.service;

import com.back.coach.global.code.JobStatus;

public record JobStatusSnapshot(
        String jobId,
        JobStatus status,
        String currentStep,
        String error
) {
}
