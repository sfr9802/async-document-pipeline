package com.asyncpipeline.support;

import java.util.UUID;

/**
 * Utility class for generating identifiers used throughout the pipeline.
 */
public final class IdGenerator {

    private IdGenerator() {
        // utility class
    }

    /**
     * Generates a new random UUID.
     */
    public static UUID newId() {
        return UUID.randomUUID();
    }

    /**
     * Builds a deterministic idempotency key from a document ID and pipeline version.
     * Format: {@code doc:{documentId}:pipeline:{pipelineVersion}}
     */
    public static String newIdempotencyKey(UUID documentId, String pipelineVersion) {
        return "doc:" + documentId + ":pipeline:" + pipelineVersion;
    }
}
