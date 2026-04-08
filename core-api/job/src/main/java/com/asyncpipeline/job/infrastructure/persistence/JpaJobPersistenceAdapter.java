package com.asyncpipeline.job.infrastructure.persistence;

import com.asyncpipeline.job.application.port.out.JobPersistencePort;
import com.asyncpipeline.job.domain.Document;
import com.asyncpipeline.job.domain.Job;
import com.asyncpipeline.job.domain.JobArtifact;
import com.asyncpipeline.job.domain.JobCallbackInbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaJobPersistenceAdapter implements JobPersistencePort {

    private static final Logger log = LoggerFactory.getLogger(JpaJobPersistenceAdapter.class);

    private final JpaJobRepository jobRepository;
    private final JpaDocumentRepository documentRepository;
    private final JpaJobArtifactRepository artifactRepository;
    private final JpaJobCallbackInboxRepository callbackInboxRepository;

    public JpaJobPersistenceAdapter(JpaJobRepository jobRepository,
                                    JpaDocumentRepository documentRepository,
                                    JpaJobArtifactRepository artifactRepository,
                                    JpaJobCallbackInboxRepository callbackInboxRepository) {
        this.jobRepository = jobRepository;
        this.documentRepository = documentRepository;
        this.artifactRepository = artifactRepository;
        this.callbackInboxRepository = callbackInboxRepository;
    }

    @Override
    public Document saveDocument(Document document) {
        return documentRepository.save(document);
    }

    @Override
    public Optional<Document> findDocumentById(UUID id) {
        return documentRepository.findById(id);
    }

    @Override
    public Job saveJob(Job job) {
        return jobRepository.save(job);
    }

    @Override
    public Optional<Job> findJobById(UUID id) {
        return jobRepository.findById(id);
    }

    @Override
    public Optional<Job> findJobByIdForUpdate(UUID id) {
        return jobRepository.findByIdForUpdate(id);
    }

    @Override
    public void saveArtifact(JobArtifact artifact) {
        artifactRepository.save(artifact);
    }

    @Override
    public List<JobArtifact> findArtifactsByJobId(UUID jobId) {
        return artifactRepository.findByJobId(jobId);
    }

    @Override
    public boolean insertCallbackInbox(JobCallbackInbox entry) {
        try {
            callbackInboxRepository.save(entry);
            return true;
        } catch (DataIntegrityViolationException ex) {
            log.debug("Duplicate callback inbox entry for job={} callback={}",
                    entry.getJobId(), entry.getCallbackId());
            return false;
        }
    }

    @Override
    public void updateDocument(Document document) {
        documentRepository.save(document);
    }

    @Override
    public Optional<Job> findLatestJobByDocumentId(UUID documentId) {
        return jobRepository.findFirstByDocumentIdOrderByCreatedAtDesc(documentId);
    }
}
