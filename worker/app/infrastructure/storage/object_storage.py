import hashlib
import json
import logging
from dataclasses import dataclass
from pathlib import Path

from app.infrastructure.storage.object_key_policy import build_artifact_object_key

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class StoredArtifact:
    artifact_type: str
    object_key: str
    content_type: str
    file_name: str
    size_bytes: int
    checksum_sha256: str


class ObjectStorageClient:
    def __init__(self, *, base_dir: Path, backend: str = "filesystem") -> None:
        self._base_dir = base_dir
        self._backend = backend
        self._base_dir.mkdir(parents=True, exist_ok=True)

    async def upload_artifact(
        self,
        *,
        job_id: str,
        attempt_number: int,
        artifact_type: str,
        content: str,
        content_type: str,
        file_name: str | None = None,
    ) -> StoredArtifact:
        object_key = build_artifact_object_key(
            job_id=job_id,
            attempt_number=attempt_number,
            artifact_type=artifact_type,
            file_name=file_name,
        )

        content_bytes = content.encode("utf-8")
        checksum = hashlib.sha256(content_bytes).hexdigest()

        file_path = self._base_dir / object_key
        file_path.parent.mkdir(parents=True, exist_ok=True)
        file_path.write_bytes(content_bytes)

        # Write metadata sidecar
        meta_path = file_path.with_suffix(file_path.suffix + ".meta")
        meta_path.write_text(
            json.dumps(
                {
                    "contentType": content_type,
                    "sizeBytes": len(content_bytes),
                    "checksum": checksum,
                }
            )
        )

        resolved_name = file_name or file_path.name
        logger.info(
            "Artifact uploaded: key=%s size=%d checksum=%s",
            object_key,
            len(content_bytes),
            checksum[:16],
        )

        return StoredArtifact(
            artifact_type=artifact_type,
            object_key=object_key,
            content_type=content_type,
            file_name=resolved_name,
            size_bytes=len(content_bytes),
            checksum_sha256=checksum,
        )

    async def download(self, *, object_key: str) -> str:
        file_path = self._base_dir / object_key
        if not file_path.exists():
            raise FileNotFoundError(f"Object not found: {object_key}")
        return file_path.read_text("utf-8")
