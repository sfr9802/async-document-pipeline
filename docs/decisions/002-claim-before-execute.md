# ADR-002: Claim-Before-Execute 패턴

## 상태
승인됨

## 배경
At-least-once 전달 시스템(메시지 큐, HTTP 재시도, 태스크 스케줄러)에서는 같은 Job이 여러 Worker 인스턴스에 동시에 dispatch될 수 있습니다. 조율 없이는 두 Worker가 같은 문서를 처리하여 리소스를 낭비하고 충돌하는 결과를 생성할 수 있습니다.

## 결정
Worker는 처리를 시작하기 전에 Core API에서 Job을 claim해야 합니다. Claim은:
1. Job 행에 비관적 데이터베이스 잠금을 획득
2. Job이 `QUEUED` 상태인지 검증
3. Job을 `RUNNING`으로 전이
4. 결정적 `workerRunId`를 반환

`claimGranted: true`를 받은 Worker만 실행을 진행합니다.

## 결과

**긍정적:**
- At-least-once dispatch에도 불구하고 exactly-once 실행 보장
- 결정적 `workerRunId`로 재시도 간 상관관계 추적 가능
- 데이터베이스 수준에서 race condition 해결 (비관적 잠금)
- 명확한 소유권: 한 시점에 하나의 Worker만 Job을 "소유"

**부정적:**
- 처리 시작 전 추가 HTTP 왕복
- 매우 높은 동시성에서 데이터베이스 잠금 경합
- Claim 엔드포인트가 단일 조율 지점

**왜 분산 잠금(Redis, ZooKeeper)이 아닌가?**
데이터베이스에 이미 Job 상태가 저장되어 있습니다. 잠금에도 데이터베이스를 사용하면 별도 인프라 의존성을 추가하지 않습니다. 비관적 잠금은 claim 트랜잭션 동안(밀리초)만 유지되며, 전체 처리 기간 동안 유지되지 않습니다.
