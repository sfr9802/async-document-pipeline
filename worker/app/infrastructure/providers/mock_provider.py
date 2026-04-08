from app.application.ports.analysis_provider import AnalysisFinding, AnalysisResult


class MockAnalysisProvider:
    @property
    def provider_name(self) -> str:
        return "mock"

    async def analyze(self, *, content: str, file_name: str) -> AnalysisResult:
        return AnalysisResult(
            summary=f"Mock analysis of '{file_name}': {len(content)} characters processed.",
            findings=[
                AnalysisFinding(
                    category="structure",
                    description="Document has standard structure",
                    severity="info",
                ),
                AnalysisFinding(
                    category="content",
                    description="Content appears well-formed",
                    severity="info",
                ),
            ],
            word_count=len(content.split()),
            sentence_count=content.count(".")
            + content.count("!")
            + content.count("?"),
            key_phrases=["mock-phrase-1", "mock-phrase-2"],
            metadata={"provider": "mock", "deterministic": True},
        )
