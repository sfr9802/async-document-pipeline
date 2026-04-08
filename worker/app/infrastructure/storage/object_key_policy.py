ARTIFACT_TYPE_FILE_MAP: dict[str, str] = {
    "REPORT_JSON": "analysis.json",
    "REPORT_HTML": "report.html",
}


def build_artifact_object_key(
    *,
    job_id: str,
    attempt_number: int,
    artifact_type: str,
    file_name: str | None = None,
) -> str:
    resolved_name = file_name or ARTIFACT_TYPE_FILE_MAP.get(
        artifact_type,
        f"{artifact_type.lower()}.dat",
    )
    return f"jobs/{job_id}/attempts/{attempt_number}/artifacts/{artifact_type}/{resolved_name}"
