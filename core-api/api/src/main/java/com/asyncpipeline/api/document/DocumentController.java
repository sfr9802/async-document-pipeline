package com.asyncpipeline.api.document;

import com.asyncpipeline.infra.storage.DocumentStorageService;
import com.asyncpipeline.job.application.dto.EnqueueJobCommand;
import com.asyncpipeline.job.application.dto.EnqueueJobResult;
import com.asyncpipeline.job.application.port.in.EnqueueJobUseCase;
import com.asyncpipeline.job.application.port.out.JobPersistencePort;
import com.asyncpipeline.job.domain.Document;
import com.asyncpipeline.job.domain.Job;
import com.asyncpipeline.job.domain.JobArtifact;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final EnqueueJobUseCase enqueueJobUseCase;
    private final JobPersistencePort persistencePort;
    private final DocumentStorageService documentStorageService;

    public DocumentController(EnqueueJobUseCase enqueueJobUseCase,
                              JobPersistencePort persistencePort,
                              DocumentStorageService documentStorageService) {
        this.enqueueJobUseCase = enqueueJobUseCase;
        this.persistencePort = persistencePort;
        this.documentStorageService = documentStorageService;
    }

    /**
     * Submit a document for analysis.
     * Accepts a multipart file upload, stores it, and enqueues a processing job.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentSubmitResponse> submitDocument(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            fileName = "unnamed";
        }

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read uploaded file", ex);
        }

        UUID storageId = UUID.randomUUID();
        String storagePath = documentStorageService.storeDocument(
                storageId, fileName, content, contentType);

        log.info("Stored upload at {} (storageId={})", storagePath, storageId);

        EnqueueJobResult result = enqueueJobUseCase.enqueue(
                new EnqueueJobCommand(fileName, contentType, storagePath));

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(new DocumentSubmitResponse(
                        result.documentId(),
                        result.jobId(),
                        result.status().name()));
    }

    /**
     * Get document status, its latest job, and any artifacts produced.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentDetailResponse> getDocument(@PathVariable UUID id) {
        Document document = persistencePort.findDocumentById(id)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + id));

        JobSummary jobSummary = null;
        List<ArtifactSummary> artifactSummaries = List.of();

        var jobOpt = persistencePort.findLatestJobByDocumentId(id);
        if (jobOpt.isPresent()) {
            Job job = jobOpt.get();
            jobSummary = new JobSummary(
                    job.getId(),
                    job.getStatus().name(),
                    job.getAttemptNumber(),
                    job.getCreatedAt(),
                    job.getCompletedAt());

            List<JobArtifact> artifacts = persistencePort.findArtifactsByJobId(job.getId());
            artifactSummaries = artifacts.stream()
                    .map(a -> new ArtifactSummary(
                            a.getArtifactType(),
                            a.getFileName(),
                            a.getSizeBytes(),
                            a.getCreatedAt()))
                    .toList();
        }

        return ResponseEntity.ok(new DocumentDetailResponse(
                document.getId(),
                document.getFileName(),
                document.getStatus(),
                jobSummary,
                artifactSummaries));
    }
}
