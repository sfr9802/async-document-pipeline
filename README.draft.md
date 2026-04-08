# Async Document Pipeline

Spring Boot + FastAPI 기반 비동기 문서 처리 아키텍처.
Job 수명주기 관리, 서비스 분리, Provider 추상화를 실제 운영 수준의 패턴으로 구현한 프로젝트입니다.

두 개의 독립 서비스가 공유 DB와 HTTP 콜백으로 협업하여 문서를 비동기 처리합니다.
실제 시스템에서 수천 건의 작업을 처리하는 것과 동일한 아키텍처입니다.

---

## 아키텍처

```
                                ┌──────────────────────────────────────┐
                                │           Shared Storage              │
                                │      (filesystem / object store)      │
                                └──────────┬──────────────┬────────────┘
                                           │              │
                                      write input    read input / write artifacts
                                           │              │
┌──────────┐   POST /documents  ┌──────────┴──┐      ┌────┴─────────────┐
│          │ ─────────────────► │             │      │                  │
│  Client  │                   │  Core API   │      │     Worker       │
│          │ ◄──────────────── │ (Spring Boot)│      │    (FastAPI)     │
│          │   202 + jobId     │             │      │                  │
└──────────┘                   │ ┌─────────┐ │      │ ┌──────────────┐ │
     │                         │ │   Job   │ │ claim│ │  Processing  │ │
     │  GET /documents/{id}    │ │ Service │ │◄─────│ │  Provider    │ │
     │ ───────────────────────►│ └────┬────┘ │      │ └──────────────┘ │
     │                         │      │      │      │                  │
     │ ◄───────────────────────│ ┌────┴────┐ │ HTTP │                  │
     │   {status, artifacts}   │ │Dispatch │─┼─────►│   execute job    │
     │                         │ │  Port   │ │      │                  │
     │                         │ └─────────┘ │      │ ┌──────────────┐ │
     │                         │             │◄─────│ │  Callback    │ │
     │                         │ ┌─────────┐ │ POST │ │  Client      │ │
     │                         │ │Callback │ │      │ └──────────────┘ │
     │                         │ │ Inbox   │ │      │                  │
     │                         │ └─────────┘ │      └──────────────────┘
     │                         └──────┬──────┘
     │                         ┌──────┴──────┐
     │                         │ PostgreSQL  │
     │                         └─────────────┘
```

**Core API** (Java 21 / Spring Boot 4.0)는 데이터베이스를 소유하고 Job 상태를 관리합니다.
**Worker** (Python 3.12 / FastAPI)는 상태를 갖지 않는 실행 엔진으로, Job을 claim하고 처리한 뒤 결과를 콜백으로 보고합니다.

---

## 처리 흐름

```
Client                    Core API                         Worker
  │                          │                               │
  │  POST /api/v1/documents  │                               │
  │ ────────────────────────►│                               │
  │                          │── Document 저장                │
  │                          │── Job 생성 (ENQUEUING)         │
  │  202 {documentId, jobId} │                               │
  │ ◄────────────────────────│                               │
  │                          │                               │
  │                          │── TX commit 후 dispatch ──────►│
  │                          │                               │
  │                          │  POST /internal/jobs/claim     │
  │                          │◄──────────────────────────────│
  │                          │── 비관적 잠금, QUEUED→RUNNING   │
  │                          │── workerRunId 반환             │
  │                          │──────────────────────────────►│
  │                          │                               │── 문서 읽기
  │                          │                               │── Provider 실행
  │                          │                               │── 결과 artifact 저장
  │                          │                               │
  │                          │  POST /internal/jobs/callbacks │
  │                          │◄──────────────────────────────│
  │                          │── 콜백 멱등성 검증              │
  │                          │── artifact 메타데이터 저장      │
  │                          │── RUNNING → SUCCEEDED          │
  │                          │                               │
  │  GET /api/v1/documents/{id}                              │
  │ ────────────────────────►│                               │
  │ ◄────────────────────────│                               │
  │  {status, job, artifacts}│                               │
```

### Job 상태 머신

```
ENQUEUING ──► QUEUED ──► RUNNING ──► SUCCEEDED
                │               ├──► FAILED
                ▼               └──► DEAD (복구 불가)
             CANCELLED
```

| 전이 | 트리거 | 보호 조건 |
|------|--------|-----------|
| ENQUEUING → QUEUED | Dispatch 성공 | TX commit 이후 이벤트 리스너 |
| QUEUED → RUNNING | Worker claim 성공 | 비관적 잠금 + idempotencyKey 일치 |
| RUNNING → SUCCEEDED | 콜백 수신 (status=SUCCEEDED) | callbackId 유니크 제약 |
| RUNNING → FAILED | 콜백 수신 (status=FAILED) | 동일 |

---

## 프로젝트 구조

```
async-document-pipeline/
├── core-api/                     # Spring Boot — 오케스트레이션 & Public API
│   ├── app/                      #   REST 컨트롤러, 설정, 진입점
│   ├── job/                      #   Job 도메인, 포트, 서비스 (헥사고날 코어)
│   ├── infra-queue/              #   Dispatch 어댑터 (HTTP / Noop)
│   └── infra-storage/            #   로컬 파일시스템 스토리지 어댑터
├── worker/                       # FastAPI — 상태 비보존 Job 실행기
│   └── app/
│       ├── api/                  #   Job 실행 엔드포인트, 스키마
│       ├── application/          #   ProcessingProvider 포트, JobExecutor 서비스
│       ├── infrastructure/       #   HTTP 클라이언트, 스토리지, Provider 구현체
│       └── runtime/              #   설정, 부트스트랩, 라이프사이클
├── contracts/                    # 서비스 간 JSON Schema 계약
├── docs/                         # 아키텍처 문서 & ADR
├── docker-compose.yml
└── .env.example
```

### 모듈 역할

| 모듈 | 역할 |
|------|------|
| `core-api/job` | Job 상태 머신, 영속성 포트, claim/callback 로직. 헥사고날 아키텍처의 코어 |
| `core-api/infra-queue` | Dispatch 어댑터 — `HttpJobDispatchAdapter` (Worker에 HTTP POST) 또는 `NoopJobDispatchAdapter` (개발용 stub) |
| `core-api/infra-storage` | 로컬 파일시스템 스토리지. SHA-256 체크섬 포함 `.meta` 사이드카 파일 |
| `worker/application` | `ProcessingProvider` 프로토콜 정의 + `JobExecutor` 오케스트레이터 |
| `worker/infrastructure` | HTTP 클라이언트 (claim, callback), 스토리지, Provider 구현체 (`Local`, `Mock`) |

---

## 설계 결정

### 왜 비동기 파이프라인인가?

문서 처리는 밀리초에서 수 분까지 소요될 수 있습니다.
동기 방식(`POST → 대기 → 응답`)은 다음 문제를 야기합니다:

- HTTP 연결 점유로 인한 리소스 고갈
- 클라이언트 타임아웃 관리의 복잡성
- API 가용성이 Worker 가용성에 종속

비동기 패턴은 제출과 실행을 분리하여, 독립적인 스케일링과 장애 격리를 가능하게 합니다.
([ADR-001](docs/decisions/001-async-over-sync.md))

### 왜 Claim-Before-Execute인가?

At-least-once 전달 시스템에서는 같은 Job이 여러 Worker에 동시 전달될 수 있습니다.
Claim 메커니즘이 이를 해결합니다:

1. Job 행에 비관적 잠금(`SELECT ... FOR UPDATE`) 획득
2. 상태가 `QUEUED`인지, idempotencyKey가 일치하는지 검증
3. `RUNNING`으로 전이, 결정적 `workerRunId` 반환

동일 파라미터로 재시도된 dispatch는 항상 같은 `workerRunId`를 받아, 추적이 가능합니다.
([ADR-002](docs/decisions/002-claim-before-execute.md))

### 왜 Provider 추상화인가?

`ProcessingProvider` 프로토콜은 실제 처리 백엔드를 오케스트레이션 로직과 분리합니다:

```python
class ProcessingProvider(Protocol):
    async def process(self, *, content: str, file_name: str) -> ProcessingResult: ...
```

| Provider | 용도 |
|----------|------|
| `MockProcessingProvider` | 결정적 fixture 반환. 테스트, CI용 |
| `LocalProcessingProvider` | 휴리스틱 텍스트 분석. 데모, 개발용 |
| *(확장)* `ExternalProvider` | 외부 ML/LLM API 호출 |

환경변수 하나로 전환합니다: `PROCESSING_PROVIDER=mock|local`

오케스트레이션(claim → process → publish)은 어떤 Provider를 사용하든 동일하게 유지됩니다.
([ADR-003](docs/decisions/003-provider-abstraction.md))

### 왜 Callback Inbox인가?

네트워크 재시도로 인해 같은 콜백이 여러 번 도착할 수 있습니다.
`job_callback_inbox` 테이블이 멱등성을 보장합니다:

| 시나리오 | 응답 | 동작 |
|----------|------|------|
| 새 콜백, Job이 RUNNING | 200 | 적용: Job 전이, artifact 저장 |
| 중복 콜백 (같은 callbackId + 같은 payload hash) | 200 | 무시: 이미 처리됨 |
| 충돌 콜백 (같은 callbackId + 다른 payload hash) | 409 | 거부: 데이터 무결성 위반 |
| Job 없음 | 404 | 무시 |
| Job이 RUNNING 상태가 아님 | 202 | 수신했으나 미적용 |

---

## 빠른 시작

### 사전 요구사항

- Docker, Docker Compose
- (선택) Java 21, Python 3.12 — Docker 없이 로컬 개발 시

### Docker Compose로 실행

```bash
git clone <repo-url> && cd async-document-pipeline
docker compose up --build
```

서비스 3개가 시작됩니다:
- **PostgreSQL** — `localhost:5432` (Flyway가 시작 시 마이그레이션 실행)
- **Core API** — `localhost:8082`
- **Worker** — `localhost:8000`

### 파이프라인 테스트

```bash
# 1. 테스트 문서 생성
echo "The quick brown fox jumps over the lazy dog. This is a sample document." > sample.txt

# 2. 문서 제출
curl -s -X POST http://localhost:8082/api/v1/documents \
  -F "file=@sample.txt" | jq .
# → { "documentId": "...", "jobId": "...", "status": "ENQUEUING" }

# 3. 상태 확인 (documentId를 위 응답값으로 교체)
curl -s http://localhost:8082/api/v1/documents/{documentId} | jq .
# → status: "COMPLETED", artifacts 포함
```

### 서비스 개별 실행

```bash
# Core API (PostgreSQL이 localhost:5432에 실행 중이어야 함)
cd core-api && ./gradlew :app:bootRun

# Worker
cd worker && pip install -e . && uvicorn main:app --reload --port 8000
```

---

## API

### External API (클라이언트용)

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/api/v1/documents` | 문서 업로드 + 비동기 처리 시작. `multipart/form-data`. 202 반환 |
| `GET` | `/api/v1/documents/{id}` | 문서 상태, Job 진행률, artifact 목록 조회 |

### Internal API (서비스 간 통신)

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/internal/jobs/claim` | Worker가 Job 실행 권한 획득. 비관적 잠금 |
| `POST` | `/internal/jobs/callbacks` | Worker가 처리 결과 + artifact 메타데이터 보고 |
| `POST` | `/internal/jobs/execute` | Core API가 Worker에 Job dispatch (Worker 엔드포인트) |

Internal 엔드포인트는 `X-Internal-Secret` 헤더로 인증합니다.

---

## 헥사고날 아키텍처

Core API는 포트 & 어댑터 패턴으로 도메인 로직을 인프라에서 분리합니다.

```
                ┌─────────────────────────────────┐
  Inbound       │       Application Layer          │     Outbound
  ────────►     │                                  │     ────────►
  Controllers   │   EnqueueJobUseCase              │     Adapters
                │   ClaimJobUseCase                │
                │   JobCallbackUseCase             │
                │           │                      │
                │     ┌─────┴──────┐               │
                │     │ JobService │               │
                │     └────────────┘               │
                │                                  │
                │   JobPersistencePort ────────────►│ JpaJobPersistenceAdapter
                │   JobDispatchPort   ────────────►│ HttpDispatch / NoopDispatch
                │   ArtifactStoragePort ──────────►│ LocalFileStorageAdapter
                └─────────────────────────────────┘
```

Job 오케스트레이션(상태 전이, 멱등성, claim 검증)이 안정적인 코어이고,
인프라 세부사항(어떤 DB, 어떤 큐, 어떤 스토리지)은 어댑터로 교체 가능합니다.

---

## 데이터 모델

```
┌─────────────┐     1:N      ┌──────────────┐     1:N      ┌───────────────┐
│  documents  │──────────────│    jobs       │──────────────│ job_artifacts │
│             │              │              │              │               │
│ id (PK)     │              │ id (PK)      │              │ id (PK)       │
│ file_name   │              │ document_id  │              │ job_id        │
│ status      │              │ status       │              │ artifact_type │
│ storage_path│              │ attempt_no   │              │ object_key    │
│ created_at  │              │ idempotency_ │              │ size_bytes    │
│ updated_at  │              │   key (UQ)   │              │ checksum      │
└─────────────┘              │ worker_run_id│              └───────────────┘
                             │ pipeline_ver │
                             │ completed_at │
                             └──────┬───────┘
                                    │ 1:N
                             ┌──────┴──────────────┐
                             │ job_callback_inbox   │
                             │                      │
                             │ callback_id          │
                             │ payload_hash         │
                             │ status               │
                             │ UQ(job_id,callback_id)│
                             └─────────────────────┘
```

---

## 설정

모든 설정은 환경변수로 관리합니다. [`.env.example`](.env.example)에서 전체 목록을 확인하세요.

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `APP_QUEUE_DISPATCH_MODE` | `noop` | `http`: Worker에 dispatch, `noop`: 개발용 stub |
| `PROCESSING_PROVIDER` | `local` | `mock`: fixture, `local`: 휴리스틱 분석 |
| `CORE_CLAIM_STUB_MODE` | `true` | `false`: 실제 Core API claim 호출 |
| `CALLBACK_STUB_MODE` | `true` | `false`: 실제 콜백 발행 |
| `APP_INTERNAL_SHARED_SECRET` | *(empty)* | 서비스 간 인증 토큰. 비어 있으면 dev 모드 (인증 생략) |

---

## 기술 스택

| 컴포넌트 | 기술 | 버전 |
|----------|------|------|
| Core API | Spring Boot | 4.0.3 |
| Worker | FastAPI | 0.115+ |
| Database | PostgreSQL | 16 |
| Build | Gradle (multi-module) | 8.12 |
| Migrations | Flyway | via Spring Boot |
| Containers | Docker Compose | v2 |
| Java | Eclipse Temurin | 21 |
| Python | CPython | 3.12 |

---

## 한계

의도적으로 단순화한 부분을 명시합니다.
포트폴리오 프로젝트로서 아키텍처 패턴에 집중하기 위해, 운영 수준의 일부 기능은 포함하지 않았습니다.

| 한계 | 운영 환경이라면 |
|------|-----------------|
| 단일 Worker, 분산 큐 없음 | Kafka / SQS / Cloud Tasks + 수평 스케일링 |
| FAILED Job 재시도 스케줄러 없음 | 스케줄러가 `ENQUEUING`/`FAILED` 상태의 stale Job을 재dispatch |
| Artifact 다운로드 엔드포인트 없음 | Signed URL 또는 스트리밍 다운로드 API |
| External API 인증 없음 | JWT / OAuth2 미들웨어 |
| 실시간 상태 알림 없음 | WebSocket / SSE로 폴링 대체 |
| 클라우드 스토리지 미지원 | `ArtifactStoragePort` 구현체로 S3/GCS 어댑터 추가 |

이러한 확장은 아키텍처 변경 없이 포트/어댑터 교체만으로 가능하도록 설계되어 있습니다.

---

## 확장 가능 지점

기존 코드 변경 없이 구현체를 추가하는 것만으로 확장됩니다:

| 확장 | 방법 |
|------|------|
| 클라우드 큐 | `JobDispatchPort` 구현체 추가 (Cloud Tasks, SQS 등) |
| 클라우드 스토리지 | `ArtifactStoragePort` 구현체 추가 (S3, GCS 등) |
| AI/ML 처리 | `ProcessingProvider` 구현체 추가 (OpenAI, Claude, 로컬 LLM 등) |
| 실시간 알림 | `JobService` 상태 전이 후 이벤트 발행 → WebSocket 핸들러 |
| 실패 Job 재처리 | 스케줄러 추가, `ENQUEUING`/`FAILED` 상태 Job 재dispatch |

---

## License

MIT
