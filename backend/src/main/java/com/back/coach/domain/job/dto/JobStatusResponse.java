package com.back.coach.domain.job.dto;

import com.back.coach.domain.job.service.JobStatusSnapshot;
import com.back.coach.global.code.JobStatus;

public record JobStatusResponse(
        String jobId,
        JobStatus status,
        String currentStep,
        String error
) {

    public static JobStatusResponse from(JobStatusSnapshot snapshot) {
        return new JobStatusResponse(
                snapshot.jobId(),
                snapshot.status(),
                snapshot.currentStep(),
                snapshot.error()
        );
    }
}
