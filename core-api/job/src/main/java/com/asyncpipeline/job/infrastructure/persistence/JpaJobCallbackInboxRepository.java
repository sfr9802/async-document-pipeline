package com.asyncpipeline.job.infrastructure.persistence;

import com.asyncpipeline.job.domain.JobCallbackInbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaJobCallbackInboxRepository extends JpaRepository<JobCallbackInbox, UUID> {

    Optional<JobCallbackInbox> findByJobIdAndCallbackId(UUID jobId, UUID callbackId);
}
