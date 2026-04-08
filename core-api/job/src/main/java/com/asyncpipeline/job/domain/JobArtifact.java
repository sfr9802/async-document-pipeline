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
@Table(name = "job_artifacts")
public class JobArtifact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "artifact_type", nullable = false)
    private String artifactType;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected JobArtifact() {
        // JPA requires a no-arg constructor
    }

    public JobArtifact(UUID jobId, String artifactType, String objectKey,
                       String contentType, String fileName, long sizeBytes, String checksum) {
        this.jobId = jobId;
        this.artifactType = artifactType;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
        this.checksum = checksum;
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

    public String getArtifactType() {
        return artifactType;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFileName() {
        return fileName;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getChecksum() {
        return checksum;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
