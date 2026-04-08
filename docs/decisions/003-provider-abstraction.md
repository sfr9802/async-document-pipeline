# ADR-003: Provider Abstraction for Analysis Backend

## Status
Accepted

## Context
The analysis logic (what happens to the document content) is the most volatile part of the system. It may be:
- A mock returning fixture data (testing, CI)
- A heuristic engine (demo, development)
- A local LLM (on-premise deployment)
- An external ML API (cloud deployment)

The orchestration logic (claim, store artifacts, publish callback) is stable and should not change when the analysis backend changes.

## Decision
Define an `AnalysisProvider` protocol (interface) with a single method:
```python
async def analyze(self, *, content: str, file_name: str) -> AnalysisResult
```

Concrete implementations are selected at startup via the `ANALYSIS_PROVIDER` environment variable. The `ServiceContainer` wires the chosen provider into the `AnalyzeService`.

## Consequences

**Positive:**
- New providers are added without modifying orchestration code
- Testing uses `MockProvider` with deterministic output
- Provider selection is a deploy-time decision, not a code change
- Each provider is independently testable

**Negative:**
- The `AnalysisResult` schema must accommodate all providers (lowest common denominator)
- Provider-specific configuration requires additional env vars

**Why Protocol (not ABC)?**
Python's `Protocol` enables structural subtyping — any class with the right method signature satisfies the protocol without explicit inheritance. This is lighter and more Pythonic than abstract base classes.
