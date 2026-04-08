package com.asyncpipeline.job.application.service;

import com.asyncpipeline.job.application.dto.JobDispatchRequest;

import java.util.UUID;

public record JobDispatchEvent(
        UUID jobId,
        JobDispatchRequest request
) {
}
