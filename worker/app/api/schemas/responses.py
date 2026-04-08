from __future__ import annotations

from typing import TYPE_CHECKING

from pydantic import BaseModel, ConfigDict, Field

if TYPE_CHECKING:
    from app.application.services.analyze_service import AnalyzeWorkflowResult


class CallbackStatusResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    published: bool
    status_code: int = Field(alias="statusCode")
    target_url: str = Field(alias="targetUrl")


class AnalyzeTaskResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    request_id: str = Field(alias="requestId")
    job_id: str = Field(alias="jobId")
    status: str  # SUCCEEDED, FAILED, NO_OP
    callback: CallbackStatusResponse | None = None

    @classmethod
    def from_result(cls, result: AnalyzeWorkflowResult) -> AnalyzeTaskResponse:
        callback = None
        if result.callback_published or result.callback_url:
            callback = CallbackStatusResponse(
                published=result.callback_published,
                status_code=result.callback_status_code,
                target_url=result.callback_url,
            )
        return cls(
            request_id=result.request_id,
            job_id=result.job_id,
            status=result.status,
            callback=callback,
        )
