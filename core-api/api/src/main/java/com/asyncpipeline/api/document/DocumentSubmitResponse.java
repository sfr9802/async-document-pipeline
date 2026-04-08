package com.asyncpipeline.api.document;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record DocumentSubmitResponse(
        @JsonProperty("documentId") UUID documentId,
        @JsonProperty("jobId") UUID jobId,
        @JsonProperty("status") String status
) {
}
