package com.asyncpipeline.job.application.port.out;

import com.asyncpipeline.job.domain.Document;
import com.asyncpipeline.job.domain.Job;
import com.asyncpipeline.job.domain.JobArtifact;
import com.asyncpipeline.job.domain.JobCallbackInbox;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobPersistencePort {

    Document saveDocument(Document document);

    Optional<Document> findDocumentById(UUID id);

    Job saveJob(Job job);

    Optional<Job> findJobById(UUID id);

    /**
     * Finds a job by ID using a pessimistic write lock for safe concurrent updates.
     */
    Optional<Job> findJobByIdForUpdate(UUID id);

    void saveArtifact(JobArtifact artifact);

    List<JobArtifact> findArtifactsByJobId(UUID jobId);

    /**
     * Inserts a callback inbox entry for idempotent callback processing.
     *
     * @return {@code true} if the entry was inserted, {@code false} if a duplicate already exists
     */
    boolean insertCallbackInbox(JobCallbackInbox entry);

    void updateDocument(Document document);

    /**
     * Finds the most recently created job for a given document.
     */
    Optional<Job> findLatestJobByDocumentId(UUID documentId);
}
