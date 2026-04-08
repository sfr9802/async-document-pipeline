package com.asyncpipeline.api.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record CallbackResponse(
        @JsonProperty("outcome") String outcome,
        @JsonProperty("jobId") UUID jobId,
        @JsonProperty("message") String message
) {
}
