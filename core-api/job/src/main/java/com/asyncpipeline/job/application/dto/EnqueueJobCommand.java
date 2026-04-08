package com.asyncpipeline.job.application.dto;

public record EnqueueJobCommand(
        String fileName,
        String contentType,
        String storagePath
) {
}
