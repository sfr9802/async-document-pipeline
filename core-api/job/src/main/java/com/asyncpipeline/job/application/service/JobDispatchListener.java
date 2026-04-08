package com.asyncpipeline.job.application.service;

import com.asyncpipeline.job.application.port.out.JobDispatchPort;
import com.asyncpipeline.job.application.port.out.JobPersistencePort;
import com.asyncpipeline.job.domain.Job;
import com.asyncpipeline.job.domain.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class JobDispatchListener {

    private static final Logger log = LoggerFactory.getLogger(JobDispatchListener.class);

    private final JobDispatchPort jobDispatchPort;
    private final JobPersistencePort jobPersistencePort;

    public JobDispatchListener(JobDispatchPort jobDispatchPort, JobPersistencePort jobPersistencePort) {
        this.jobDispatchPort = jobDispatchPort;
        this.jobPersistencePort = jobPersistencePort;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobDispatch(JobDispatchEvent event) {
        try {
            jobDispatchPort.dispatch(event.request());

            jobPersistencePort.findJobById(event.jobId()).ifPresent(job -> {
                job.setStatus(JobStatus.QUEUED);
                jobPersistencePort.saveJob(job);
            });

            log.info("Job {} dispatched successfully and transitioned to QUEUED", event.jobId());
        } catch (Exception ex) {
            log.error("Failed to dispatch job {}: {}", event.jobId(), ex.getMessage(), ex);
        }
    }
}
