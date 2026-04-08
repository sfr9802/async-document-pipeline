package com.asyncpipeline.job.application.dto;

import java.util.UUID;

public record ClaimJobCommand(
        String requestId,
        UUID jobId,
        int attemptNumber,
        String pipelineVersion,
        String idempotencyKey
) {
}
