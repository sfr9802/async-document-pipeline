package com.asyncpipeline.job.application.service;

import com.asyncpipeline.job.application.dto.ClaimJobCommand;
import com.asyncpipeline.job.application.dto.ClaimJobResult;
import com.asyncpipeline.job.application.dto.EnqueueJobCommand;
import com.asyncpipeline.job.application.dto.EnqueueJobResult;
import com.asyncpipeline.job.application.dto.HandleCallbackCommand;
import com.asyncpipeline.job.application.dto.HandleCallbackResult;
import com.asyncpipeline.job.application.dto.HandleCallbackResult.CallbackOutcome;
import com.asyncpipeline.job.application.dto.JobDispatchRequest;
import com.asyncpipeline.job.application.port.in.ClaimJobUseCase;
import com.asyncpipeline.job.application.port.in.EnqueueJobUseCase;
import com.asyncpipeline.job.application.port.in.JobCallbackUseCase;
import com.asyncpipeline.job.application.port.out.JobPersistencePort;
import com.asyncpipeline.job.domain.Document;
import com.asyncpipeline.job.domain.Job;
import com.asyncpipeline.job.domain.JobArtifact;
import com.asyncpipeline.job.domain.JobCallbackInbox;
import com.asyncpipeline.job.domain.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class JobService implements EnqueueJobUseCase, ClaimJobUseCase, JobCallbackUseCase {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobPersistencePort persistencePort;
    private final ApplicationEventPublisher eventPublisher;

    public JobService(JobPersistencePort persistencePort, ApplicationEventPublisher eventPublisher) {
        this.persistencePort = persistencePort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public EnqueueJobResult enqueue(EnqueueJobCommand command) {
        Document document = new Document(
                command.fileName(),
                command.contentType(),
                command.storagePath(),
                "PENDING"
        );
        document = persistencePort.saveDocument(document);

        String pipelineVersion = "v1";
        String idempotencyKey = buildIdempotencyKey(document.getId(), pipelineVersion);
        UUID dispatchToken = UUID.randomUUID();

        Job job = new Job(
                document.getId(),
                JobStatus.ENQUEUING,
                idempotencyKey,
                dispatchToken,
                pipelineVersion
        );
        job = persistencePort.saveJob(job);

        log.info("Created job {} for document {} with idempotencyKey={}",
                job.getId(), document.getId(), idempotencyKey);

        JobDispatchRequest dispatchRequest = new JobDispatchRequest(
                UUID.randomUUID().toString(),
                job.getId(),
                document.getId(),
                pipelineVersion,
                job.getAttemptNumber(),
                idempotencyKey,
                dispatchToken,
                buildCallbackUrl(job.getId()),
                command.storagePath()
        );

        eventPublisher.publishEvent(new JobDispatchEvent(job.getId(), dispatchRequest));

        return new EnqueueJobResult(document.getId(), job.getId(), job.getStatus());
    }

    @Override
    @Transactional
    public ClaimJobResult claim(ClaimJobCommand command) {
        var jobOpt = persistencePort.findJobByIdForUpdate(command.jobId());

        if (jobOpt.isEmpty()) {
            return ClaimJobResult.denied(null, "Job not found: " + command.jobId());
        }

        Job job = jobOpt.get();

        if (job.getStatus() != JobStatus.QUEUED) {
            return ClaimJobResult.denied(job.getStatus(),
                    "Job is not claimable; current status is " + job.getStatus());
        }

        if (!job.getIdempotencyKey().equals(command.idempotencyKey())) {
            return ClaimJobResult.denied(job.getStatus(),
                    "Idempotency key mismatch");
        }

        UUID workerRunId = generateDeterministicWorkerRunId(
                job.getId(), command.attemptNumber(), command.idempotencyKey());

        job.setStatus(JobStatus.RUNNING);
        job.setWorkerRunId(workerRunId);
        job.setAttemptNumber(command.attemptNumber());
        persistencePort.saveJob(job);

        log.info("Job {} claimed by worker with runId={}", job.getId(), workerRunId);

        return ClaimJobResult.granted(workerRunId, job.getStatus());
    }

    @Override
    @Transactional
    public HandleCallbackResult handleCallback(HandleCallbackCommand command) {
        // Step 1: Idempotent insert into callback inbox
        JobCallbackInbox inboxEntry = new JobCallbackInbox(
                command.jobId(),
                command.callbackId(),
                command.payloadHash(),
                "RECEIVED"
        );

        boolean inserted = persistencePort.insertCallbackInbox(inboxEntry);
        if (!inserted) {
            log.info("Duplicate callback {} for job {}", command.callbackId(), command.jobId());
            return new HandleCallbackResult(
                    CallbackOutcome.DUPLICATE_CALLBACK, command.jobId(), null);
        }

        // Step 2: Lock and load the job
        var jobOpt = persistencePort.findJobByIdForUpdate(command.jobId());
        if (jobOpt.isEmpty()) {
            log.warn("Callback received for unknown job {}", command.jobId());
            return new HandleCallbackResult(
                    CallbackOutcome.JOB_NOT_FOUND, command.jobId(), null);
        }

        Job job = jobOpt.get();

        if (job.getStatus() != JobStatus.RUNNING) {
            log.warn("Callback for job {} in unexpected state {}", command.jobId(), job.getStatus());
            return new HandleCallbackResult(
                    CallbackOutcome.INVALID_STATE, command.jobId(), job.getStatus());
        }

        // Step 3: Apply the callback outcome
        JobStatus finalStatus;

        if ("SUCCEEDED".equals(command.callbackStatus())) {
            finalStatus = JobStatus.SUCCEEDED;
            job.setStatus(finalStatus);
            job.setCompletedAt(command.completedAt() != null ? command.completedAt() : Instant.now());
            persistencePort.saveJob(job);

            // Persist artifacts
            if (command.artifacts() != null) {
                for (HandleCallbackCommand.ArtifactMetadata artifact : command.artifacts()) {
                    JobArtifact jobArtifact = new JobArtifact(
                            job.getId(),
                            artifact.artifactType(),
                            artifact.objectKey(),
                            artifact.contentType(),
                            artifact.fileName(),
                            artifact.sizeBytes(),
                            artifact.checksumSha256()
                    );
                    persistencePort.saveArtifact(jobArtifact);
                }
            }

            // Update document status
            persistencePort.findDocumentById(job.getDocumentId()).ifPresent(doc -> {
                doc.setStatus("COMPLETED");
                persistencePort.updateDocument(doc);
            });

            log.info("Job {} succeeded with {} artifact(s)", job.getId(),
                    command.artifacts() != null ? command.artifacts().size() : 0);

        } else {
            finalStatus = JobStatus.FAILED;
            job.setStatus(finalStatus);
            job.setCompletedAt(command.completedAt() != null ? command.completedAt() : Instant.now());
            persistencePort.saveJob(job);

            persistencePort.findDocumentById(job.getDocumentId()).ifPresent(doc -> {
                doc.setStatus("FAILED");
                persistencePort.updateDocument(doc);
            });

            log.info("Job {} failed: {}", job.getId(), command.errorMessage());
        }

        // Step 4: Update callback inbox entry
        inboxEntry.setStatus("PROCESSED");
        inboxEntry.setProcessedAt(Instant.now());
        inboxEntry.setResult(finalStatus.name());
        persistencePort.insertCallbackInbox(inboxEntry);

        return new HandleCallbackResult(CallbackOutcome.APPLIED, job.getId(), finalStatus);
    }

    private String buildIdempotencyKey(UUID documentId, String pipelineVersion) {
        return documentId + ":" + pipelineVersion + ":1";
    }

    private String buildCallbackUrl(UUID jobId) {
        return "/api/v1/jobs/" + jobId + "/callback";
    }

    private UUID generateDeterministicWorkerRunId(UUID jobId, int attemptNumber, String idempotencyKey) {
        String seed = jobId + ":" + attemptNumber + ":" + idempotencyKey;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
