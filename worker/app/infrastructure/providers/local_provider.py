import re
from collections import Counter

from app.application.ports.analysis_provider import AnalysisFinding, AnalysisResult


class LocalAnalysisProvider:
    @property
    def provider_name(self) -> str:
        return "local"

    async def analyze(self, *, content: str, file_name: str) -> AnalysisResult:
        words = content.split()
        word_count = len(words)

        sentences = re.split(r"[.!?]+", content)
        sentences = [s.strip() for s in sentences if s.strip()]
        sentence_count = len(sentences)

        # Vocabulary richness
        unique_words = set(w.lower() for w in words)
        vocab_ratio = len(unique_words) / max(word_count, 1)

        # Key phrase extraction (simple: most common 2-grams)
        bigrams = [
            f"{words[i].lower()} {words[i + 1].lower()}" for i in range(len(words) - 1)
        ]
        key_phrases = [phrase for phrase, _ in Counter(bigrams).most_common(5)]

        # Build findings
        findings: list[AnalysisFinding] = []

        if word_count < 50:
            findings.append(
                AnalysisFinding(
                    category="length",
                    description="Document is very short (< 50 words)",
                    severity="warning",
                )
            )
        elif word_count > 5000:
            findings.append(
                AnalysisFinding(
                    category="length",
                    description="Document is lengthy (> 5000 words)",
                    severity="info",
                )
            )

        avg_sentence_length = word_count / max(sentence_count, 1)

        if avg_sentence_length > 25:
            findings.append(
                AnalysisFinding(
                    category="readability",
                    description=f"Average sentence length is high ({avg_sentence_length:.1f} words)",
                    severity="warning",
                )
            )

        if vocab_ratio > 0.7:
            findings.append(
                AnalysisFinding(
                    category="vocabulary",
                    description="High vocabulary diversity",
                    severity="info",
                )
            )
        elif vocab_ratio < 0.3:
            findings.append(
                AnalysisFinding(
                    category="vocabulary",
                    description="Low vocabulary diversity — repetitive content",
                    severity="warning",
                )
            )

        findings.append(
            AnalysisFinding(
                category="structure",
                description=f"Contains {sentence_count} sentences across {word_count} words",
                severity="info",
            )
        )

        summary = (
            f"Analysis of '{file_name}': {word_count} words, {sentence_count} sentences, "
            f"vocabulary ratio {vocab_ratio:.2f}. {len(findings)} findings."
        )

        return AnalysisResult(
            summary=summary,
            findings=findings,
            word_count=word_count,
            sentence_count=sentence_count,
            key_phrases=key_phrases,
            metadata={
                "provider": "local",
                "vocabRatio": round(vocab_ratio, 3),
                "avgSentenceLength": round(avg_sentence_length, 1),
            },
        )
