package com.asyncpipeline.api.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record ClaimJobRequest(
        @JsonProperty("requestId") String requestId,
        @JsonProperty("jobId") UUID jobId,
        @JsonProperty("attemptNumber") int attemptNumber,
        @JsonProperty("pipelineVersion") String pipelineVersion,
        @JsonProperty("idempotencyKey") String idempotencyKey
) {
}
