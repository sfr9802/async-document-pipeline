import logging
import time
from dataclasses import dataclass

from app.api.schemas.requests import AnalyzeTaskRequest
from app.application.ports.analysis_provider import AnalysisProvider
from app.application.services.result_publisher import ResultPublisher
from app.infrastructure.clients.core_job_client import ClaimDecision, CoreJobClient
from app.infrastructure.storage.object_storage import ObjectStorageClient

logger = logging.getLogger(__name__)


@dataclass
class AnalyzeWorkflowResult:
    status: str  # SUCCEEDED, FAILED, NO_OP
    job_id: str
    request_id: str = ""
    summary: str | None = None
    callback_published: bool = False
    callback_status_code: int = 0
    callback_url: str = ""


class AnalyzeService:
    def __init__(
        self,
        *,
        core_job_client: CoreJobClient,
        analysis_provider: AnalysisProvider,
        result_publisher: ResultPublisher,
        storage_client: ObjectStorageClient,
    ) -> None:
        self._core_job_client = core_job_client
        self._provider = analysis_provider
        self._publisher = result_publisher
        self._storage = storage_client

    async def analyze(self, request: AnalyzeTaskRequest) -> AnalyzeWorkflowResult:
        start = time.monotonic()
        logger.info(
            "Analyze started: job_id=%s attempt=%d provider=%s",
            request.job_id,
            request.attempt_number,
            self._provider.provider_name,
        )

        # 1. Claim job
        claim = await self._core_job_client.claim_job(
            job_id=request.job_id,
            attempt_number=request.attempt_number,
            pipeline_version=request.pipeline_version,
            idempotency_key=request.idempotency_key,
            request_id=request.request_id,
        )

        if claim.decision != ClaimDecision.GRANTED:
            logger.info(
                "Claim not granted: job_id=%s reason=%s",
                request.job_id,
                claim.noop_reason,
            )
            return AnalyzeWorkflowResult(
                status="NO_OP",
                job_id=request.job_id,
                request_id=request.request_id,
                summary=f"Claim not granted: {claim.noop_reason}",
            )

        worker_run_id = claim.worker_run_id

        try:
            # 2. Download input document
            content = await self._storage.download(
                object_key=request.input_storage_path,
            )
            logger.info(
                "Input downloaded: path=%s size=%d",
                request.input_storage_path,
                len(content),
            )

            # 3. Run analysis
            analysis_result = await self._provider.analyze(
                content=content,
                file_name=request.input_storage_path.rsplit("/", 1)[-1],
            )
            logger.info(
                "Analysis complete: findings=%d words=%d",
                len(analysis_result.findings),
                analysis_result.word_count,
            )

            # 4. Publish success (upload artifacts + callback)
            duration_ms = int((time.monotonic() - start) * 1000)
            callback_result = await self._publisher.publish_success(
                request=request,
                worker_run_id=worker_run_id,
                analysis_result=analysis_result,
                duration_ms=duration_ms,
            )

            return AnalyzeWorkflowResult(
                status="SUCCEEDED",
                job_id=request.job_id,
                request_id=request.request_id,
                summary=analysis_result.summary,
                callback_published=callback_result.published,
                callback_status_code=callback_result.status_code,
                callback_url=callback_result.target_url,
            )

        except Exception as exc:
            logger.error(
                "Analysis failed: job_id=%s error=%s",
                request.job_id,
                str(exc),
                exc_info=True,
            )

            # Publish failure callback
            duration_ms = int((time.monotonic() - start) * 1000)
            try:
                callback_result = await self._publisher.publish_failure(
                    request=request,
                    worker_run_id=worker_run_id,
                    error_message=str(exc),
                    duration_ms=duration_ms,
                )
                return AnalyzeWorkflowResult(
                    status="FAILED",
                    job_id=request.job_id,
                    request_id=request.request_id,
                    summary=f"Error: {str(exc)[:200]}",
                    callback_published=callback_result.published,
                    callback_status_code=callback_result.status_code,
                    callback_url=callback_result.target_url,
                )
            except Exception as cb_exc:
                logger.error(
                    "Failure callback also failed: job_id=%s error=%s",
                    request.job_id,
                    str(cb_exc),
                )
                raise exc from cb_exc
