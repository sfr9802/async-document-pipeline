package com.asyncpipeline.api.internal;

import com.asyncpipeline.job.application.dto.ClaimJobCommand;
import com.asyncpipeline.job.application.dto.ClaimJobResult;
import com.asyncpipeline.job.application.port.in.ClaimJobUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/jobs")
public class JobClaimController {

    private final ClaimJobUseCase claimJobUseCase;

    public JobClaimController(ClaimJobUseCase claimJobUseCase) {
        this.claimJobUseCase = claimJobUseCase;
    }

    @PostMapping("/claim")
    public ResponseEntity<ClaimJobResponse> claimJob(@RequestBody ClaimJobRequest request) {
        ClaimJobResult result = claimJobUseCase.claim(new ClaimJobCommand(
                request.requestId(),
                request.jobId(),
                request.attemptNumber(),
                request.pipelineVersion(),
                request.idempotencyKey()));

        return ResponseEntity.ok(new ClaimJobResponse(
                result.claimGranted(),
                result.workerRunId(),
                result.currentStatus() != null ? result.currentStatus().name() : null,
                result.noopReason()));
    }
}
