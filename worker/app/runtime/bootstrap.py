from dataclasses import dataclass
from pathlib import Path

from app.application.ports.analysis_provider import AnalysisProvider
from app.application.services.analyze_service import AnalyzeService
from app.application.services.result_publisher import ResultPublisher
from app.infrastructure.clients.callback_client import CallbackClient
from app.infrastructure.clients.core_job_client import CoreJobClient
from app.infrastructure.providers.local_provider import LocalAnalysisProvider
from app.infrastructure.providers.mock_provider import MockAnalysisProvider
from app.infrastructure.storage.object_storage import ObjectStorageClient
from app.runtime.config import Settings


@dataclass(slots=True)
class ServiceContainer:
    analyze_service: AnalyzeService
    result_publisher: ResultPublisher


def build_service_container(settings: Settings) -> ServiceContainer:
    # Build infrastructure clients
    storage_client = ObjectStorageClient(
        base_dir=Path(settings.storage_base_dir),
        backend=settings.storage_backend,
    )
    core_job_client = CoreJobClient(
        claim_url=settings.core_claim_url,
        timeout_seconds=settings.core_claim_timeout_seconds,
        stub_mode=settings.core_claim_stub_mode,
    )
    callback_client = CallbackClient(
        timeout_seconds=settings.callback_timeout_seconds,
        stub_mode=settings.callback_stub_mode,
        max_attempts=settings.callback_max_attempts,
        retry_backoff_seconds=settings.callback_retry_backoff_seconds,
    )

    # Build analysis provider
    provider = _build_analysis_provider(settings)

    # Build services
    result_publisher = ResultPublisher(
        storage_client=storage_client,
        callback_client=callback_client,
    )
    analyze_service = AnalyzeService(
        core_job_client=core_job_client,
        analysis_provider=provider,
        result_publisher=result_publisher,
        storage_client=storage_client,
    )

    return ServiceContainer(
        analyze_service=analyze_service,
        result_publisher=result_publisher,
    )


def _build_analysis_provider(settings: Settings) -> AnalysisProvider:
    match settings.analysis_provider:
        case "mock":
            return MockAnalysisProvider()
        case "local":
            return LocalAnalysisProvider()
        case _:
            raise ValueError(f"Unknown analysis provider: {settings.analysis_provider}")
