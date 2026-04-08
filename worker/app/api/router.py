from fastapi import FastAPI

from app.api.routes import health, tasks


def register_routers(app: FastAPI) -> None:
    app.include_router(health.router)
    app.include_router(tasks.router)
