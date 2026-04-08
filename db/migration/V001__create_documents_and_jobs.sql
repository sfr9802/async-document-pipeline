-- ============================================================
-- V001: Core tables for the async document analysis pipeline
-- ============================================================

CREATE TABLE documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name       VARCHAR(255)  NOT NULL,
    content_type    VARCHAR(100)  NOT NULL,
    storage_path    VARCHAR(500)  NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_document_status CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED'))
);

CREATE TABLE jobs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id      UUID          NOT NULL REFERENCES documents(id),
    status           VARCHAR(20)   NOT NULL DEFAULT 'ENQUEUING',
    attempt_number   INT           NOT NULL DEFAULT 1,
    idempotency_key  VARCHAR(255)  NOT NULL UNIQUE,
    dispatch_token   UUID,
    worker_run_id    UUID,
    pipeline_version VARCHAR(50)   NOT NULL DEFAULT 'v1',
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at     TIMESTAMPTZ,

    CONSTRAINT chk_job_status CHECK (
        status IN ('ENQUEUING','QUEUED','RUNNING','SUCCEEDED','FAILED','DEAD','CANCELLED')
    )
);

CREATE INDEX idx_jobs_document_id ON jobs(document_id);
CREATE INDEX idx_jobs_status      ON jobs(status);
