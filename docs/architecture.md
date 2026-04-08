# Architecture Overview

## System Context

This system processes documents asynchronously through a two-service architecture:

- **Core API** (Spring Boot) — Owns the database, manages job lifecycle, exposes the public REST API, and coordinates dispatch/callback flows.
- **Worker** (FastAPI) — Stateless execution engine that claims jobs, runs analysis, uploads artifacts, and reports results via callbacks.

Both services share a filesystem volume for artifact storage and communicate over HTTP for job dispatch, claims, and callbacks.

## Hexagonal Architecture (Ports & Adapters)

The Core API follows hexagonal architecture to separate domain logic from infrastructure:

```
                    ┌─────────────────────────────────┐
                    │        Application Layer         │
                    │                                  │
  Inbound Ports     │   EnqueueJobUseCase              │     Outbound Ports
  ──────────────►   │   ClaimJobUseCase                │   ──────────────►
  (Controllers)     │   JobCallbackUseCase             │   (Adapters)
                    │           │                      │
                    │     ┌─────┴──────┐               │
                    │     │ JobService │               │
                    │     └────────────┘               │
                    │                                  │
                    │   Ports:                         │
                    │   - JobPersistencePort            │──► JpaJobPersistenceAdapter
                    │   - JobDispatchPort               │──► HttpJobDispatchAdapter / NoopAdapter
                    │   - ArtifactStoragePort           │──► LocalFileStorageAdapter
                    └─────────────────────────────────┘
```

**Why hexagonal?** The job orchestration logic (state transitions, idempotency, claim validation) is the valuable, stable core. Infrastructure details (which database, which queue, which storage) change between environments. Ports make these concerns independently testable and swappable.

## Data Model

```
┌─────────────┐     1:N      ┌──────────┐     1:N      ┌───────────────┐
│  documents  │──────────────│   jobs    │──────────────│ job_artifacts │
│             │              │           │              │               │
│ id          │              │ id        │              │ id            │
│ file_name   │              │ doc_id    │              │ job_id        │
│ status      │              │ status    │              │ artifact_type │
│ storage_path│              │ attempt_no│              │ object_key    │
└─────────────┘              │ idemp_key │              │ size_bytes    │
                             │ worker_id │              │ checksum      │
                             └─────┬─────┘              └───────────────┘
                                   │ 1:N
                             ┌─────┴──────────────┐
                             │ job_callback_inbox  │
                             │                     │
                             │ callback_id (unique)│
                             │ payload_hash        │
                             │ status              │
                             └─────────────────────┘
```

## Dispatch Mechanism

Instead of a message queue (Kafka, RabbitMQ, Cloud Tasks), this system uses **direct HTTP dispatch**:

1. `JobService.enqueue()` saves the job in a `@Transactional` block
2. A `@TransactionalEventListener(phase = AFTER_COMMIT)` fires after the transaction commits
3. The listener calls `JobDispatchPort.dispatch()`, which POSTs to the worker

**Why after-commit dispatch?** If the dispatch happens inside the transaction and the transaction rolls back, the worker would receive a job that doesn't exist. Dispatching after commit guarantees the job is persisted before the worker sees it.

**Trade-off**: If the dispatch HTTP call fails after the commit, the job stays in `ENQUEUING` state. A production system would add a reconciliation scheduler to re-dispatch stale jobs. This is noted as a future enhancement.

## Claim Protocol

The claim mechanism prevents duplicate execution:

```
Worker                          Core API
  │                                │
  │  POST /internal/jobs/claim     │
  │  {jobId, attemptNo, idempKey}  │
  │ ─────────────────────────────► │
  │                                │ ── SELECT ... FOR UPDATE (pessimistic lock)
  │                                │ ── validate: status=QUEUED, key matches
  │                                │ ── UPDATE status=RUNNING, workerRunId=...
  │  {claimGranted: true,          │
  │   workerRunId: "abc-123"}      │
  │ ◄───────────────────────────── │
```

The `workerRunId` is deterministic: `UUID.nameUUIDFromBytes(jobId + attemptNo + idempotencyKey)`. This means retried dispatches that claim with the same parameters get the same `workerRunId`, enabling correlation.

## Callback Idempotency

Network retries may deliver the same callback multiple times. The `job_callback_inbox` table handles this:

| Scenario | HTTP Response | Action |
|----------|--------------|--------|
| New callback, job is RUNNING | 200 | Apply: transition job, save artifacts |
| Duplicate callback (same callbackId, same payload hash) | 200 | No-op: already processed |
| Conflicting callback (same callbackId, different payload) | 409 | Reject: data integrity violation |
| Job not found | 404 | No-op |
| Job not in RUNNING state | 202 | Accepted but not applied |

## Storage Layout

Artifacts are stored in a deterministic path structure:

```
{storage-root}/
├── documents/
│   └── {documentId}/
│       └── {fileName}              ← uploaded document
└── jobs/
    └── {jobId}/
        └── attempts/
            └── {attemptNo}/
                └── artifacts/
                    └── {artifactType}/
                        ├── analysis.json       ← artifact content
                        └── analysis.json.meta  ← sidecar metadata
```

Each artifact has a `.meta` sidecar file containing `contentType`, `sizeBytes`, and SHA-256 `checksum` for integrity verification.

## Worker Provider Abstraction

```
AnalysisProvider (Protocol)
├── MockAnalysisProvider      ← deterministic fixtures
└── LocalAnalysisProvider     ← heuristic text analysis
```

The `AnalysisProvider` protocol defines a single method: `analyze(content, file_name) → AnalysisResult`. Implementations are wired at startup based on the `ANALYSIS_PROVIDER` environment variable, following the strategy pattern.

This abstraction exists because in a real system, the analysis backend might be:
- A local LLM (LLaMA, Gemma)
- An external API (OpenAI, Claude)
- A custom ML model
- A rule-based engine

The orchestration logic (claim, store, callback) stays identical regardless of which provider runs the analysis.
