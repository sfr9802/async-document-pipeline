import json
import logging
import uuid
from dataclasses import asdict
from datetime import datetime, timezone

from app.api.schemas.requests import AnalyzeTaskRequest
from app.application.ports.analysis_provider import AnalysisResult
from app.infrastructure.clients.callback_client import CallbackClient, CallbackResult
from app.infrastructure.storage.object_storage import ObjectStorageClient

logger = logging.getLogger(__name__)


class ResultPublisher:
    def __init__(
        self,
        *,
        storage_client: ObjectStorageClient,
        callback_client: CallbackClient,
    ) -> None:
        self._storage = storage_client
        self._callback = callback_client

    async def publish_success(
        self,
        *,
        request: AnalyzeTaskRequest,
        worker_run_id: str | None,
        analysis_result: AnalysisResult,
        duration_ms: int,
    ) -> CallbackResult:
        # Upload JSON report artifact
        report_content = json.dumps(asdict(analysis_result), indent=2, default=str)
        artifact = await self._storage.upload_artifact(
            job_id=request.job_id,
            attempt_number=request.attempt_number,
            artifact_type="REPORT_JSON",
            content=report_content,
            content_type="application/json",
        )

        # Build callback payload
        payload = {
            "requestId": request.request_id,
            "callbackId": str(uuid.uuid4()),
            "jobId": request.job_id,
            "documentId": request.document_id,
            "workerRunId": worker_run_id,
            "status": "SUCCEEDED",
            "attemptNumber": request.attempt_number,
            "pipelineVersion": request.pipeline_version,
            "artifacts": [
                {
                    "artifactType": artifact.artifact_type,
                    "objectKey": artifact.object_key,
                    "contentType": artifact.content_type,
                    "fileName": artifact.file_name,
                    "sizeBytes": artifact.size_bytes,
                    "checksumSha256": artifact.checksum_sha256,
                }
            ],
            "summary": analysis_result.summary,
            "completedAt": datetime.now(timezone.utc).isoformat(),
            "error": None,
            "executionMetadata": {
                "workerRunId": worker_run_id,
                "providerName": "document-analysis",
                "durationMs": duration_ms,
            },
        }

        return await self._callback.publish(
            callback_url=request.callback_url,
            payload=payload,
            event_type="job.succeeded",
        )

    async def publish_failure(
        self,
        *,
        request: AnalyzeTaskRequest,
        worker_run_id: str | None,
        error_message: str,
        duration_ms: int,
    ) -> CallbackResult:
        payload = {
            "requestId": request.request_id,
            "callbackId": str(uuid.uuid4()),
            "jobId": request.job_id,
            "documentId": request.document_id,
            "workerRunId": worker_run_id,
            "status": "FAILED",
            "attemptNumber": request.attempt_number,
            "pipelineVersion": request.pipeline_version,
            "artifacts": [],
            "summary": None,
            "completedAt": datetime.now(timezone.utc).isoformat(),
            "error": {"code": "ANALYSIS_FAILED", "message": error_message},
            "executionMetadata": {
                "workerRunId": worker_run_id,
                "providerName": "document-analysis",
                "durationMs": duration_ms,
            },
        }

        return await self._callback.publish(
            callback_url=request.callback_url,
            payload=payload,
            event_type="job.failed",
        )
