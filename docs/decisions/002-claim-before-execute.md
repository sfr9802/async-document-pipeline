# ADR-002: Claim-Before-Execute Pattern

## Status
Accepted

## Context
In at-least-once delivery systems (message queues, HTTP retries, task schedulers), the same job may be dispatched to multiple worker instances simultaneously. Without coordination, two workers could process the same document, wasting resources and potentially producing conflicting results.

## Decision
Workers must claim a job from the Core API before processing it. The claim:
1. Acquires a pessimistic database lock on the job row
2. Validates the job is in `QUEUED` state
3. Transitions the job to `RUNNING`
4. Returns a deterministic `workerRunId`

Only the worker that receives `claimGranted: true` proceeds with execution.

## Consequences

**Positive:**
- Exactly-once execution semantics despite at-least-once dispatch
- Deterministic `workerRunId` enables correlation across retries
- Race conditions are resolved at the database level (pessimistic lock)
- Clear ownership: only one worker "owns" a job at any time

**Negative:**
- Extra HTTP round-trip before processing begins
- Database lock contention under very high concurrency
- Claim endpoint is a single point of coordination

**Why not distributed locks (Redis, ZooKeeper)?**
The database already stores the job state. Using it for locking avoids introducing another infrastructure dependency. The pessimistic lock is held only for the duration of the claim transaction (milliseconds), not the entire processing duration.
