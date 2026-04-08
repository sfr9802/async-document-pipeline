package com.asyncpipeline.job.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HandleCallbackCommand(
        String requestId,
        UUID callbackId,
        UUID jobId,
        UUID workerRunId,
        String callbackStatus,
        int attemptNumber,
        String pipelineVersion,
        List<ArtifactMetadata> artifacts,
        String summary,
        Instant completedAt,
        String errorMessage,
        String payloadHash
) {

    public record ArtifactMetadata(
            String artifactType,
            String objectKey,
            String contentType,
            String fileName,
            long sizeBytes,
            String checksumSha256
    ) {
    }
}
