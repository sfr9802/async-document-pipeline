package com.asyncpipeline.api.document;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record JobSummary(
        @JsonProperty("jobId") UUID jobId,
        @JsonProperty("status") String status,
        @JsonProperty("attemptNumber") int attemptNumber,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("completedAt") Instant completedAt
) {
}
