package com.asyncpipeline.infra.queue;

import com.asyncpipeline.job.application.dto.JobDispatchRequest;
import com.asyncpipeline.job.application.port.out.JobDispatchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.queue.dispatch-mode", havingValue = "noop", matchIfMissing = true)
public class NoopJobDispatchAdapter implements JobDispatchPort {

    private static final Logger log = LoggerFactory.getLogger(NoopJobDispatchAdapter.class);

    @Override
    public void dispatch(JobDispatchRequest request) {
        log.info("Noop dispatch: jobId={} documentId={} attempt={}",
                request.jobId(), request.documentId(), request.attemptNumber());
    }
}
