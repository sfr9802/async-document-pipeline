package com.asyncpipeline.job.application.port.out;

public record StoredObjectMeta(
        boolean exists,
        long sizeBytes,
        String contentType,
        String checksum
) {
}
