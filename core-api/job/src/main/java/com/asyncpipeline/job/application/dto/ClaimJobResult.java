package com.asyncpipeline.job.application.dto;

import com.asyncpipeline.job.domain.JobStatus;

import java.util.UUID;

public record ClaimJobResult(
        boolean claimGranted,
        UUID workerRunId,
        JobStatus currentStatus,
        String noopReason
) {

    public static ClaimJobResult granted(UUID workerRunId, JobStatus status) {
        return new ClaimJobResult(true, workerRunId, status, null);
    }

    public static ClaimJobResult denied(JobStatus currentStatus, String reason) {
        return new ClaimJobResult(false, null, currentStatus, reason);
    }
}
