package com.asyncpipeline.job.application.dto;

import com.asyncpipeline.job.domain.JobStatus;

import java.util.UUID;

public record EnqueueJobResult(
        UUID documentId,
        UUID jobId,
        JobStatus status
) {
}
