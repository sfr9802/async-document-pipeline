package com.asyncpipeline.api.document;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ArtifactSummary(
        @JsonProperty("artifactType") String artifactType,
        @JsonProperty("fileName") String fileName,
        @JsonProperty("sizeBytes") long sizeBytes,
        @JsonProperty("createdAt") Instant createdAt
) {
}
