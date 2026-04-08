package com.asyncpipeline.api.internal;

import com.asyncpipeline.job.application.dto.HandleCallbackCommand;
import com.asyncpipeline.job.application.dto.HandleCallbackResult;
import com.asyncpipeline.job.application.dto.HandleCallbackResult.CallbackOutcome;
import com.asyncpipeline.job.application.port.in.JobCallbackUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@RestController
@RequestMapping("/internal/jobs")
public class JobCallbackController {

    private static final Logger log = LoggerFactory.getLogger(JobCallbackController.class);

    private final JobCallbackUseCase callbackUseCase;

    public JobCallbackController(JobCallbackUseCase callbackUseCase) {
        this.callbackUseCase = callbackUseCase;
    }

    @PostMapping("/callbacks")
    public ResponseEntity<CallbackResponse> handleCallback(
            @RequestBody WorkerCallbackRequest request) {

        log.info("Received callback for job {} with status {}",
                request.jobId(), request.status());

        String payloadHash = computePayloadHash(request);

        List<HandleCallbackCommand.ArtifactMetadata> artifacts = List.of();
        if (request.artifacts() != null) {
            artifacts = request.artifacts().stream()
                    .map(a -> new HandleCallbackCommand.ArtifactMetadata(
                            a.artifactType(),
                            a.objectKey(),
                            a.contentType(),
                            a.fileName(),
                            a.sizeBytes(),
                            a.checksumSha256()))
                    .toList();
        }

        String errorMessage = null;
        if (request.error() != null) {
            errorMessage = request.error().message();
        }

        HandleCallbackCommand command = new HandleCallbackCommand(
                request.requestId(),
                request.callbackId(),
                request.jobId(),
                request.workerRunId(),
                request.status(),
                request.attemptNumber(),
                request.pipelineVersion(),
                artifacts,
                request.summary(),
                request.completedAt(),
                errorMessage,
                payloadHash);

        HandleCallbackResult result = callbackUseCase.handleCallback(command);

        HttpStatus httpStatus = mapOutcomeToHttpStatus(result.outcome());

        String message = buildMessage(result);

        return ResponseEntity
                .status(httpStatus)
                .body(new CallbackResponse(
                        result.outcome().name(),
                        result.jobId(),
                        message));
    }

    private HttpStatus mapOutcomeToHttpStatus(CallbackOutcome outcome) {
        return switch (outcome) {
            case APPLIED -> HttpStatus.OK;
            case DUPLICATE_CALLBACK -> HttpStatus.OK;
            case CONFLICT -> HttpStatus.CONFLICT;
            case JOB_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_STATE -> HttpStatus.ACCEPTED;
        };
    }

    private String buildMessage(HandleCallbackResult result) {
        return switch (result.outcome()) {
            case APPLIED -> "Callback applied successfully";
            case DUPLICATE_CALLBACK -> "Duplicate callback ignored";
            case CONFLICT -> "Callback conflicts with current job state";
            case JOB_NOT_FOUND -> "Job not found";
            case INVALID_STATE -> "Job is in an invalid state for this callback";
        };
    }

    private String computePayloadHash(WorkerCallbackRequest request) {
        String raw = String.valueOf(request.jobId())
                + String.valueOf(request.workerRunId())
                + String.valueOf(request.status())
                + request.attemptNumber()
                + String.valueOf(request.pipelineVersion())
                + (request.completedAt() != null ? request.completedAt().toString() : "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("SHA-256 algorithm not available", ex);
        }
    }
}
