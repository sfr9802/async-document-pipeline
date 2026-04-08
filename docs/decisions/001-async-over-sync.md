# ADR-001: Asynchronous Pipeline Over Synchronous Processing

## Status
Accepted

## Context
Documents submitted for analysis may take variable time to process (milliseconds for mock, seconds for heuristic, minutes for ML models). The system needs to handle concurrent submissions without blocking API resources.

## Decision
Use an asynchronous job pipeline: the API accepts the document immediately (202 Accepted), creates a job, and dispatches it to a worker. The client polls for results.

## Consequences

**Positive:**
- API response time is constant regardless of analysis duration
- Workers scale independently of the API
- Failed analyses don't affect API availability
- Natural retry mechanism via job re-dispatch

**Negative:**
- Clients must poll or use webhooks for completion notification
- More complex than a synchronous endpoint
- Requires job state management and lifecycle tracking

**Trade-offs accepted:**
- Polling latency (client sees results after next poll, not immediately)
- Operational complexity of managing two services
