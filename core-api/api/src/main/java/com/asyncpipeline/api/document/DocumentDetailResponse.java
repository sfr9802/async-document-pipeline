package com.asyncpipeline.api.document;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record DocumentDetailResponse(
        @JsonProperty("documentId") UUID documentId,
        @JsonProperty("fileName") String fileName,
        @JsonProperty("status") String status,
        @JsonProperty("job") JobSummary job,
        @JsonProperty("artifacts") List<ArtifactSummary> artifacts
) {
}
