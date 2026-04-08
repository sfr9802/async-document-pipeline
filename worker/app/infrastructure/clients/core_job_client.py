import hashlib
import logging
from dataclasses import dataclass
from enum import Enum

import httpx

logger = logging.getLogger(__name__)


class ClaimDecision(str, Enum):
    GRANTED = "GRANTED"
    NOT_GRANTED = "NOT_GRANTED"
    REJECTED = "REJECTED"


@dataclass(frozen=True)
class ClaimResult:
    claim_granted: bool
    worker_run_id: str | None
    current_status: str
    noop_reason: str | None
    decision: ClaimDecision


class CoreJobClient:
    def __init__(
        self,
        *,
        claim_url: str,
        timeout_seconds: float,
        stub_mode: bool = True,
    ) -> None:
        self._claim_url = claim_url
        self._timeout = timeout_seconds
        self._stub_mode = stub_mode

    async def claim_job(
        self,
        *,
        job_id: str,
        attempt_number: int,
        pipeline_version: str,
        idempotency_key: str,
        request_id: str,
    ) -> ClaimResult:
        if self._stub_mode:
            return self._stub_claim(
                job_id=job_id,
                attempt_number=attempt_number,
                idempotency_key=idempotency_key,
            )

        payload = {
            "requestId": request_id,
            "jobId": job_id,
            "attemptNumber": attempt_number,
            "pipelineVersion": pipeline_version,
            "idempotencyKey": idempotency_key,
        }

        async with httpx.AsyncClient(timeout=self._timeout) as client:
            resp = await client.post(self._claim_url, json=payload)
            resp.raise_for_status()
            data = resp.json()

        granted = data.get("claimGranted", False)
        return ClaimResult(
            claim_granted=granted,
            worker_run_id=data.get("workerRunId"),
            current_status=data.get("currentStatus", "UNKNOWN"),
            noop_reason=data.get("noopReason"),
            decision=ClaimDecision.GRANTED if granted else ClaimDecision.NOT_GRANTED,
        )

    def _stub_claim(
        self,
        *,
        job_id: str,
        attempt_number: int,
        idempotency_key: str,
    ) -> ClaimResult:
        # Deterministic worker_run_id from job context
        run_seed = f"{job_id}:{attempt_number}:{idempotency_key}"
        worker_run_id = hashlib.md5(run_seed.encode()).hexdigest()[:32]  # noqa: S324
        worker_run_id = (
            f"{worker_run_id[:8]}-{worker_run_id[8:12]}-"
            f"{worker_run_id[12:16]}-{worker_run_id[16:20]}-"
            f"{worker_run_id[20:]}"
        )

        logger.info(
            "Stub claim granted: job_id=%s worker_run_id=%s",
            job_id,
            worker_run_id,
        )
        return ClaimResult(
            claim_granted=True,
            worker_run_id=worker_run_id,
            current_status="RUNNING",
            noop_reason=None,
            decision=ClaimDecision.GRANTED,
        )
