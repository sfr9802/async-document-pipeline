package com.asyncpipeline.job.infrastructure.persistence;

import com.asyncpipeline.job.domain.JobArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaJobArtifactRepository extends JpaRepository<JobArtifact, UUID> {

    List<JobArtifact> findByJobId(UUID jobId);
}
