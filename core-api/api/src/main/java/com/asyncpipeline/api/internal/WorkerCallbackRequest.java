package com.asyncpipeline.api.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkerCallbackRequest(
        @JsonProperty("requestId") String requestId,
        @JsonProperty("callbackId") UUID callbackId,
        @JsonProperty("jobId") UUID jobId,
        @JsonProperty("workerRunId") UUID workerRunId,
        @JsonProperty("status") String status,
        @JsonProperty("attemptNumber") int attemptNumber,
        @JsonProperty("pipelineVersion") String pipelineVersion,
        @JsonProperty("artifacts") List<ArtifactPayload> artifacts,
        @JsonProperty("summary") String summary,
        @JsonProperty("completedAt") Instant completedAt,
        @JsonProperty("error") ErrorPayload error,
        @JsonProperty("executionMetadata") ExecutionMetadataPayload executionMetadata
) {

    public record ArtifactPayload(
            @JsonProperty("artifactType") String artifactType,
            @JsonProperty("objectKey") String objectKey,
            @JsonProperty("contentType") String contentType,
            @JsonProperty("fileName") String fileName,
            @JsonProperty("sizeBytes") long sizeBytes,
            @JsonProperty("checksumSha256") String checksumSha256
    ) {
    }

    public record ErrorPayload(
            @JsonProperty("code") String code,
            @JsonProperty("message") String message
    ) {
    }

    public record ExecutionMetadataPayload(
            @JsonProperty("workerRunId") UUID workerRunId,
            @JsonProperty("providerName") String providerName,
            @JsonProperty("durationMs") long durationMs
    ) {
    }
}
