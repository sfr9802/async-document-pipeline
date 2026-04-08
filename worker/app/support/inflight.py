import asyncio
import logging
from dataclasses import dataclass
from typing import Awaitable, Callable, TypeVar

logger = logging.getLogger(__name__)

T = TypeVar("T")


@dataclass
class CoalescedResult:
    """Result wrapper indicating whether the caller joined an existing request."""

    value: object
    joined_existing: bool


class InFlightCoalescer:
    """Deduplicates concurrent requests with the same key.

    If a second request arrives for a key that's already in-flight,
    it awaits the first request's result instead of running again.
    """

    def __init__(self) -> None:
        self._tasks: dict[str, asyncio.Task] = {}

    async def run(
        self,
        key: str,
        factory: Callable[[], Awaitable[T]],
    ) -> CoalescedResult:
        if key in self._tasks:
            task = self._tasks[key]
            logger.info("Request coalesced: key=%s", key)
            result = await task
            return CoalescedResult(value=result, joined_existing=True)

        task = asyncio.create_task(factory())
        self._tasks[key] = task
        try:
            result = await task
            return CoalescedResult(value=result, joined_existing=False)
        finally:
            self._tasks.pop(key, None)
