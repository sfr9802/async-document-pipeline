package com.asyncpipeline.job.application.dto;

import java.util.UUID;

public record JobDispatchRequest(
        String requestId,
        UUID jobId,
        UUID documentId,
        String pipelineVersion,
        int attemptNumber,
        String idempotencyKey,
        UUID dispatchToken,
        String callbackUrl,
        String inputStoragePath
) {
}
