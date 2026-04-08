package com.asyncpipeline.job.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_callback_inbox")
public class JobCallbackInbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "callback_id", nullable = false)
    private UUID callbackId;

    @Column(name = "payload_hash", nullable = false)
    private String payloadHash;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "result")
    private String result;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected JobCallbackInbox() {
        // JPA requires a no-arg constructor
    }

    public JobCallbackInbox(UUID jobId, UUID callbackId, String payloadHash, String status) {
        this.jobId = jobId;
        this.callbackId = callbackId;
        this.payloadHash = payloadHash;
        this.status = status;
    }

    @PrePersist
    void onPrePersist() {
        this.createdAt = Instant.now();
    }

    // --- Getters ---

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getCallbackId() {
        return callbackId;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public String getStatus() {
        return status;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getResult() {
        return result;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // --- Setters ---

    public void setStatus(String status) {
        this.status = status;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
