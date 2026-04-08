package com.asyncpipeline.job.application.dto;

import com.asyncpipeline.job.domain.JobStatus;

import java.util.UUID;

public record HandleCallbackResult(
        CallbackOutcome outcome,
        UUID jobId,
        JobStatus finalStatus
) {

    public enum CallbackOutcome {
        APPLIED,
        DUPLICATE_CALLBACK,
        CONFLICT,
        JOB_NOT_FOUND,
        INVALID_STATE
    }
}
