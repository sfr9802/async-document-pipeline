from fastapi import APIRouter, HTTPException, Request

from app.api.schemas.requests import AnalyzeTaskRequest
from app.api.schemas.responses import AnalyzeTaskResponse
from app.runtime.bootstrap import ServiceContainer

router = APIRouter(prefix="/internal/tasks", tags=["internal"])


@router.post("/analyze", response_model=AnalyzeTaskResponse)
async def analyze_task(body: AnalyzeTaskRequest, request: Request) -> AnalyzeTaskResponse:
    secret = request.headers.get("X-Internal-Secret", "")
    if secret != request.app.state.settings.internal_shared_secret:
        raise HTTPException(status_code=403, detail="Invalid internal secret")

    services: ServiceContainer = request.app.state.services
    result = await services.analyze_service.analyze(body)
    return AnalyzeTaskResponse.from_result(result)
