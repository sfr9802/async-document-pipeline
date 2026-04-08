from pydantic import BaseModel, ConfigDict, Field


class AnalyzeTaskRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    request_id: str = Field(alias="requestId")
    job_id: str = Field(alias="jobId")
    document_id: str = Field(alias="documentId")
    pipeline_version: str = Field(alias="pipelineVersion")
    attempt_number: int = Field(alias="attemptNumber")
    idempotency_key: str = Field(alias="idempotencyKey")
    callback_url: str = Field(alias="callbackUrl")
    input_storage_path: str = Field(alias="inputStoragePath")
