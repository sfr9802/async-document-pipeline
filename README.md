# Async Document Analysis Pipeline

A production-style asynchronous document processing system demonstrating
service separation, job lifecycle management, and provider abstraction.

Two independent services coordinate through a shared database and HTTP callbacks
to process documents asynchronously — the same architecture used in real-world
systems handling thousands of jobs per day.

## Architecture

```
                                    ┌──────────────────────────────────────────────┐
                                    │              Shared Storage                   │
                                    │         (filesystem / object store)           │
                                    └──────────┬───────────────┬───────────────────┘
                                               │               │
                                           write input     read input / write artifacts
                                               │               │
┌──────────┐    POST /documents    ┌───────────┴───┐       ┌───┴──────────────┐
│          │ ───────────────────── │               │       │                  │
│  Client  │                      │   Core API    │       │     Worker       │
│          │ ◄─────────────────── │  (Spring Boot)│       │    (FastAPI)     │
│          │    202 + jobId       │               │       │                  │
└──────────┘                      │  ┌─────────┐  │       │  ┌────────────┐  │
     │                            │  │   Job   │  │  HTTP │  │  Analysis  │  │
     │ GET /documents/{id}        │  │ Service │  │◄──────│  │  Provider  │  │
     │ ───────────────────────── │  └────┬────┘  │ claim  │  └────────────┘  │
     │                            │       │       │       │                  │
     │ ◄─────────────────────── │  ┌────┴────┐  │ HTTP  │                  │
     │    {status, artifacts}     │  │Dispatch │──┼──────►│  dispatch task   │
     │                            │  │  Port   │  │       │                  │
     │                            │  └─────────┘  │       │  ┌────────────┐  │
     │                            │               │◄──────│  │  Callback  │  │
     │                            │  ┌─────────┐  │ POST  │  │  Client    │  │
     │                            │  │Callback │  │callback│  └────────────┘  │
     │                            │  │ Inbox   │  │       │                  │
     │                            │  └─────────┘  │       │                  │
     │                            └───────┬───────┘       └──────────────────┘
     │                                    │
     │                            ┌───────┴───────┐
     │                            │  PostgreSQL   │
     │                            │  (jobs, docs, │
     │                            │   artifacts)  │
     │                            └───────────────┘
```

## End-to-End Flow

1. **Submit** — Client uploads a document via `POST /api/v1/documents`
2. **Enqueue** — Core API saves the document, creates a job (`ENQUEUING`), and schedules dispatch
3. **Dispatch** — After the DB transaction commits, the dispatch adapter POSTs the task to the worker
4. **Claim** — Worker calls `POST /internal/jobs/claim` to acquire exclusive ownership. Core transitions the job to `RUNNING` and returns a `workerRunId`
5. **Analyze** — Worker reads the document from shared storage, runs the analysis provider, and generates report artifacts
6. **Callback** — Worker uploads artifacts to shared storage, then POSTs a callback to `POST /internal/jobs/callbacks` with artifact metadata
7. **Complete** — Core API validates the callback (idempotent via `callbackId`), saves artifacts, and transitions the job to `SUCCEEDED`
8. **Query** — Client polls `GET /api/v1/documents/{id}` to see status and artifacts

### Job Lifecycle

```
ENQUEUING ──► QUEUED ──► RUNNING ──► SUCCEEDED
                │                ├──► FAILED
                ▼                └──► DEAD (unrecoverable)
             CANCELLED
```

## Project Structure

```
async-document-pipeline/
├── core-api/                  # Spring Boot — orchestration & public API
│   ├── api/                   #   REST controllers, security config
│   ├── job/                   #   Job domain, ports, service (hexagonal core)
│   ├── infra-queue/           #   Dispatch adapters (HTTP / noop)
│   ├── infra-storage/         #   Local filesystem storage adapter
│   └── support/               #   Shared utilities
├── worker/                    # FastAPI — stateless document analysis
│   └── app/
│       ├── api/               #   Task endpoints and schemas
│       ├── application/       #   Ports (AnalysisProvider) and services
│       ├── infrastructure/    #   HTTP clients, storage, provider implementations
│       └── runtime/           #   Config, bootstrap, lifecycle
├── contracts/                 # Shared JSON schemas for service contracts
├── db/                        # Flyway SQL migrations (canonical source)
└── docs/                      # Architecture docs and decision records
```

### Module Responsibility

| Module | Responsibility |
|--------|---------------|
| `core-api/job` | Job state machine, persistence ports, claim/callback logic |
| `core-api/infra-queue` | Dispatch adapters: `HttpJobDispatchAdapter` (POST to worker) or `NoopJobDispatchAdapter` (dev) |
| `core-api/infra-storage` | Local filesystem storage with SHA-256 checksums |
| `worker/application` | `AnalysisProvider` protocol + `AnalyzeService` orchestrator |
| `worker/infrastructure` | HTTP clients (claim, callback), storage, provider implementations |

## Quick Start

### Prerequisites

- Docker and Docker Compose
- (Optional) Java 21, Python 3.12 for local development without Docker

### Run with Docker Compose

```bash
# Clone and start all services
git clone <repo-url> && cd async-document-pipeline
docker compose up --build
```

This starts:
- **PostgreSQL** on port 5432
- **Core API** on port 8082 (runs Flyway migrations on startup)
- **Worker** on port 8000

### Test the Pipeline

```bash
# 1. Create a test document
echo "The quick brown fox jumps over the lazy dog. This is a sample document for analysis." > sample.txt

# 2. Submit for analysis
curl -s -X POST http://localhost:8082/api/v1/documents \
  -F "file=@sample.txt" | jq .

# Response: { "documentId": "...", "jobId": "...", "status": "ENQUEUING" }

# 3. Check status (replace {id} with documentId from step 2)
curl -s http://localhost:8082/api/v1/documents/{id} | jq .

# Response includes: status, job details, and artifacts once complete
```

### Run Services Individually

**Core API:**
```bash
cd core-api
./gradlew :api:bootRun
# Requires PostgreSQL on localhost:5432 (or set SPRING_DATASOURCE_URL)
```

**Worker:**
```bash
cd worker
pip install -e .
uvicorn main:app --reload --port 8000
```

## Key Design Decisions

### Why Async Pipeline (not synchronous)?

Documents may take seconds to minutes to analyze. A synchronous `POST → wait → response` would:
- Hold HTTP connections open (resource exhaustion under load)
- Require client-side timeout management
- Couple API availability to worker availability

The async pattern decouples submission from execution, enabling independent scaling and failure isolation.

### Why Claim-Before-Execute?

In at-least-once delivery systems (message queues, task schedulers), the same job may be dispatched multiple times. The claim mechanism provides:
- **Exactly-once execution**: Only one worker instance processes a given job
- **Idempotent claims**: Same `idempotencyKey` always produces the same `workerRunId`
- **State safety**: Job transitions are guarded by pessimistic locks

### Why Service Separation?

The Core API (Java/Spring) and Worker (Python/FastAPI) are separate services because:
- **Language fit**: Orchestration and data integrity → JVM strengths. Stateless computation → Python strengths.
- **Independent scaling**: Workers scale horizontally based on queue depth; the API scales based on request volume.
- **Failure isolation**: A worker crash doesn't affect the API. Jobs are retryable.

### Why Provider Abstraction?

The `AnalysisProvider` protocol allows swapping the analysis backend without changing orchestration logic:
- `MockProvider` — Deterministic fixtures for tests and CI
- `LocalProvider` — Heuristic text analysis for demos
- Future: `ExternalProvider` — HTTP call to an ML service

Switching is a single environment variable: `ANALYSIS_PROVIDER=mock|local`.

### Why Callback Inbox?

The `job_callback_inbox` table ensures idempotent callback processing:
- Same `callbackId` + same payload → accepted (duplicate), no state change
- Same `callbackId` + different payload → rejected (409 Conflict)
- This handles network retries gracefully without corrupting job state

## Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Core API | Spring Boot | 4.0.3 |
| Worker | FastAPI | 0.115+ |
| Database | PostgreSQL | 16 |
| Build | Gradle (multi-module) | 8.12 |
| Migrations | Flyway | via Spring Boot |
| Containers | Docker Compose | v2 |
| Java | Eclipse Temurin | 21 |
| Python | CPython | 3.12 |

## Configuration

All configuration is via environment variables. See [`.env.example`](.env.example) for the full list.

Key variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `APP_QUEUE_DISPATCH_MODE` | `noop` | `http` to dispatch to worker, `noop` for dev |
| `ANALYSIS_PROVIDER` | `local` | `mock` for fixtures, `local` for heuristic analysis |
| `CORE_CLAIM_STUB_MODE` | `true` | `false` to call real core-api claim endpoint |
| `CALLBACK_STUB_MODE` | `true` | `false` to publish real callbacks |

## License

MIT
