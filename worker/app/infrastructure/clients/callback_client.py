import asyncio
import logging
from dataclasses import dataclass

import httpx

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class CallbackResult:
    published: bool
    status_code: int
    target_url: str
    attempt_count: int


class CallbackClient:
    def __init__(
        self,
        *,
        timeout_seconds: float,
        stub_mode: bool = True,
        max_attempts: int = 3,
        retry_backoff_seconds: float = 1.0,
    ) -> None:
        self._timeout = timeout_seconds
        self._stub_mode = stub_mode
        self._max_attempts = max_attempts
        self._backoff = retry_backoff_seconds

    async def publish(
        self,
        *,
        callback_url: str,
        payload: dict,
        event_type: str = "job.completed",
    ) -> CallbackResult:
        if self._stub_mode:
            logger.info(
                "Stub callback publish: url=%s event=%s",
                callback_url,
                event_type,
            )
            return CallbackResult(
                published=True,
                status_code=200,
                target_url=callback_url,
                attempt_count=1,
            )

        headers = {
            "Content-Type": "application/json",
            "X-Event-Type": event_type,
        }

        last_status = 0
        for attempt in range(1, self._max_attempts + 1):
            try:
                async with httpx.AsyncClient(timeout=self._timeout) as client:
                    resp = await client.post(
                        callback_url,
                        json=payload,
                        headers=headers,
                    )
                    last_status = resp.status_code

                if resp.status_code < 400:
                    logger.info(
                        "Callback published: url=%s status=%d attempt=%d",
                        callback_url,
                        resp.status_code,
                        attempt,
                    )
                    return CallbackResult(
                        published=True,
                        status_code=resp.status_code,
                        target_url=callback_url,
                        attempt_count=attempt,
                    )

                if resp.status_code < 500 and resp.status_code != 429:
                    # Client error (not retryable)
                    logger.warning(
                        "Callback rejected: url=%s status=%d",
                        callback_url,
                        resp.status_code,
                    )
                    return CallbackResult(
                        published=False,
                        status_code=resp.status_code,
                        target_url=callback_url,
                        attempt_count=attempt,
                    )

                # Server error or 429 -- retry
                logger.warning(
                    "Callback attempt %d failed: url=%s status=%d",
                    attempt,
                    callback_url,
                    resp.status_code,
                )

            except httpx.TimeoutException:
                logger.warning(
                    "Callback attempt %d timed out: url=%s",
                    attempt,
                    callback_url,
                )
                last_status = 0
            except httpx.HTTPError as e:
                logger.warning(
                    "Callback attempt %d error: url=%s error=%s",
                    attempt,
                    callback_url,
                    str(e),
                )
                last_status = 0

            if attempt < self._max_attempts:
                await asyncio.sleep(self._backoff * attempt)

        logger.error(
            "Callback publish failed after %d attempts: url=%s",
            self._max_attempts,
            callback_url,
        )
        return CallbackResult(
            published=False,
            status_code=last_status,
            target_url=callback_url,
            attempt_count=self._max_attempts,
        )
