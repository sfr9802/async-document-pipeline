import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

logger = logging.getLogger(__name__)


def build_lifespan():
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        logger.info(
            "Worker starting: provider=%s, storage=%s",
            app.state.settings.analysis_provider,
            app.state.settings.storage_backend,
        )
        yield
        logger.info("Worker shutting down")

    return lifespan
