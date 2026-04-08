from dataclasses import dataclass, field
from typing import Protocol


@dataclass(frozen=True)
class AnalysisFinding:
    category: str
    description: str
    severity: str = "info"  # info, warning, important


@dataclass(frozen=True)
class AnalysisResult:
    summary: str
    findings: list[AnalysisFinding] = field(default_factory=list)
    word_count: int = 0
    sentence_count: int = 0
    key_phrases: list[str] = field(default_factory=list)
    metadata: dict[str, object] = field(default_factory=dict)


class AnalysisProvider(Protocol):
    @property
    def provider_name(self) -> str: ...

    async def analyze(self, *, content: str, file_name: str) -> AnalysisResult: ...
