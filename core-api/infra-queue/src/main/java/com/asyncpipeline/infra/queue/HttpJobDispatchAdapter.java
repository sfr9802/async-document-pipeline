package com.asyncpipeline.infra.queue;

import com.asyncpipeline.job.application.dto.JobDispatchRequest;
import com.asyncpipeline.job.application.port.out.JobDispatchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.queue.dispatch-mode", havingValue = "http")
public class HttpJobDispatchAdapter implements JobDispatchPort {

    private static final Logger log = LoggerFactory.getLogger(HttpJobDispatchAdapter.class);

    private final RestClient restClient;
    private final String workerUrl;
    private final String sharedSecret;

    public HttpJobDispatchAdapter(
            @Value("${app.worker.url}") String workerUrl,
            @Value("${app.internal.shared-secret}") String sharedSecret) {
        this.workerUrl = workerUrl;
        this.sharedSecret = sharedSecret;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public void dispatch(JobDispatchRequest request) {
        log.info("Dispatching job {} for document {} to worker at {}",
                request.jobId(), request.documentId(), workerUrl);

        Map<String, Object> payload = Map.of(
                "requestId", request.requestId(),
                "jobId", request.jobId().toString(),
                "documentId", request.documentId().toString(),
                "pipelineVersion", request.pipelineVersion(),
                "attemptNumber", request.attemptNumber(),
                "idempotencyKey", request.idempotencyKey(),
                "callbackUrl", request.callbackUrl(),
                "inputStoragePath", request.inputStoragePath()
        );

        try {
            restClient.post()
                    .uri(workerUrl)
                    .header("X-Internal-Secret", sharedSecret)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Successfully dispatched job {} to worker", request.jobId());
        } catch (Exception ex) {
            log.error("Failed to dispatch job {} to worker: {}", request.jobId(), ex.getMessage(), ex);
            throw new RuntimeException("Failed to dispatch job " + request.jobId(), ex);
        }
    }
}
