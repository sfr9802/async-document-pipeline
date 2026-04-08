# ADR-003: 분석 백엔드를 위한 Provider 추상화

## 상태
승인됨

## 배경
분석 로직(문서 내용에 대해 수행되는 처리)은 시스템에서 가장 변동성이 높은 부분입니다. 다음과 같을 수 있습니다:
- fixture 데이터를 반환하는 mock (테스트, CI)
- 휴리스틱 엔진 (데모, 개발)
- 로컬 LLM (온프레미스 배포)
- 외부 ML API (클라우드 배포)

오케스트레이션 로직(claim, artifact 저장, 콜백 발행)은 안정적이며, 분석 백엔드가 변경되어도 바뀌지 않아야 합니다.

## 결정
단일 메서드를 가진 `AnalysisProvider` 프로토콜(인터페이스)을 정의합니다:
```python
async def analyze(self, *, content: str, file_name: str) -> AnalysisResult
```

구체적인 구현체는 `ANALYSIS_PROVIDER` 환경변수를 통해 시작 시점에 선택됩니다. `ServiceContainer`가 선택된 Provider를 `AnalyzeService`에 주입합니다.

## 결과

**긍정적:**
- 오케스트레이션 코드를 수정하지 않고 새 Provider 추가 가능
- 테스트에서 결정적 출력을 가진 `MockProvider` 사용
- Provider 선택이 코드 변경이 아닌 배포 시점 결정
- 각 Provider를 독립적으로 테스트 가능

**부정적:**
- `AnalysisResult` 스키마가 모든 Provider를 수용해야 함 (최소 공통 분모)
- Provider별 설정에 추가 환경변수 필요

**왜 Protocol인가 (ABC가 아닌)?**
Python의 `Protocol`은 구조적 서브타이핑을 지원합니다 — 올바른 메서드 시그니처를 가진 클래스라면 명시적 상속 없이도 프로토콜을 만족합니다. 이는 추상 기반 클래스보다 가볍고 Pythonic합니다.
