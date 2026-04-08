from fastapi import FastAPI

from app.api.router import register_routers
from app.runtime.bootstrap import build_service_container
from app.runtime.config import get_settings
from app.runtime.lifespan import build_lifespan


def create_app(
    settings=None,
    services=None,
) -> FastAPI:
    app_settings = settings or get_settings()

    app = FastAPI(
        title="Document Analysis Worker",
        version="0.1.0",
        lifespan=build_lifespan(),
    )
    app.state.settings = app_settings
    app.state.services = services or build_service_container(app_settings)

    register_routers(app)
    return app


app = create_app()
