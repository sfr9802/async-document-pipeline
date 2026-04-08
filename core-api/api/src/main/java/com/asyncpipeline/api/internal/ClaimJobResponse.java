package com.asyncpipeline.api.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record ClaimJobResponse(
        @JsonProperty("claimGranted") boolean claimGranted,
        @JsonProperty("workerRunId") UUID workerRunId,
        @JsonProperty("currentStatus") String currentStatus,
        @JsonProperty("noopReason") String noopReason
) {
}
