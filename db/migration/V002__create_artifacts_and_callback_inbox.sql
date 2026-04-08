-- ============================================================
-- V002: Artifact storage + idempotent callback inbox
-- ============================================================

CREATE TABLE job_artifacts (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id         UUID          NOT NULL REFERENCES jobs(id),
    artifact_type  VARCHAR(50)   NOT NULL,
    object_key     VARCHAR(500)  NOT NULL,
    content_type   VARCHAR(100)  NOT NULL,
    file_name      VARCHAR(255)  NOT NULL,
    size_bytes     BIGINT        NOT NULL,
    checksum       VARCHAR(128),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_job_artifacts_job_id ON job_artifacts(job_id);

CREATE TABLE job_callback_inbox (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id         UUID          NOT NULL REFERENCES jobs(id),
    callback_id    UUID          NOT NULL,
    payload_hash   VARCHAR(128)  NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'RECEIVED',
    processed_at   TIMESTAMPTZ,
    result         VARCHAR(50),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_callback_per_job UNIQUE (job_id, callback_id)
);
