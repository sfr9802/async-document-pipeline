package com.asyncpipeline.job.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber = 1;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "dispatch_token")
    private UUID dispatchToken;

    @Column(name = "worker_run_id")
    private UUID workerRunId;

    @Column(name = "pipeline_version", nullable = false, length = 20)
    private String pipelineVersion = "v1";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Job() {
        // JPA requires a no-arg constructor
    }

    public Job(UUID documentId, JobStatus status, String idempotencyKey, UUID dispatchToken, String pipelineVersion) {
        this.documentId = documentId;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.dispatchToken = dispatchToken;
        this.pipelineVersion = pipelineVersion;
    }

    @PrePersist
    void onPrePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onPreUpdate() {
        this.updatedAt = Instant.now();
    }

    // --- Getters ---

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public JobStatus getStatus() {
        return status;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getDispatchToken() {
        return dispatchToken;
    }

    public UUID getWorkerRunId() {
        return workerRunId;
    }

    public String getPipelineVersion() {
        return pipelineVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    // --- Setters ---

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public void setDispatchToken(UUID dispatchToken) {
        this.dispatchToken = dispatchToken;
    }

    public void setWorkerRunId(UUID workerRunId) {
        this.workerRunId = workerRunId;
    }

    public void setPipelineVersion(String pipelineVersion) {
        this.pipelineVersion = pipelineVersion;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
