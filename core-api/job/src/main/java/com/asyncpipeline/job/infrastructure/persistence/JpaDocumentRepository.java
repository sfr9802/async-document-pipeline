package com.asyncpipeline.job.infrastructure.persistence;

import com.asyncpipeline.job.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaDocumentRepository extends JpaRepository<Document, UUID> {
}
