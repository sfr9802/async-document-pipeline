package com.asyncpipeline.job.domain;

public enum JobStatus {
    ENQUEUING,
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    DEAD,
    CANCELLED
}
