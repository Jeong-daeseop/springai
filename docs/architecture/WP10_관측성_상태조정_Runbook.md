# WP10 관측성과 상태 조정 Runbook

작성일: 2026-08-06  
대상: `ARCH-WP10` (`ARCH-1001~1013`)

## 1. 요청 추적 규격

모든 Servlet 요청은 `ObservabilityFilter`에서 `X-Correlation-ID`를 검증한다. 허용 형식은
영문자·숫자와 `._:-`, 최대 128자다. 값이 없거나 잘못되면 UUID를 생성하며 같은 값을 응답 헤더에
돌려준다. MCP 인증 필터는 이 값을 새로 만들지 않고 request attribute로 전달받는다.

| 필드 | 의미 | 생성·갱신 지점 | metric label 사용 |
|---|---|---|---|
| `correlationId` | 한 요청과 비동기 후속 작업의 추적 ID | HTTP 진입점 | 금지 |
| `operationId` | 상태 전이 대상 Operation | workflow/repository | 금지 |
| `artifactId` | 저장·연결·조회 대상 Artifact | Artifact service | 금지 |
| `actorId` | `anonymous`, MCP principal, REST principal | 인증 필터 | 금지 |
| `channel` | `MCP`, `SSE`, `REST`, `WEB`, `INTERNAL` | HTTP 진입점/내부 실행 | 허용하지 않음 |

`ThreadPoolTaskExecutor`와 Spring MVC async executor는 제출 시점 context를 캡처해 작업 스레드에
복원한다. 작업 종료 시 이전 MDC와 ThreadLocal을 복원하므로 스레드 재사용에 의한 누수를 막는다.

## 2. 구조화 로그

`/tmp/springai-mcp.log`는 한 줄당 JSON 객체(JSON Lines)로 기록한다. 공통 필드는
`timestamp`, `level`, `event`, `logger`, `thread`, `message`이며 context가 존재하면 위 5개 추적 필드를
추가한다. 예외는 stack/file content 대신 `exceptionType`만 기록한다.

주요 `event` 값은 `mcp_authentication`, `mcp_authorization`, `tool_call`,
`operation_transition`, `artifact_action`, `artifact_reconciliation`, `external_call`,
`validation_gate`다. password, secret, token, API key, Authorization/Bearer 값은 layout에서
`[REDACTED]` 처리하고 message는 4 KiB에서 자른다. 요청·응답 body와 생성 파일 내용은 로그 필드로
기록하지 않는다.

조회 예시:

```bash
jq 'select(.correlationId == "<correlation-id>")' /tmp/springai-mcp.log
jq 'select(.operationId == "<operation-id>")' /tmp/springai-mcp.log
```

## 3. Metric catalog

Actuator 조회 경로는 `/actuator/metrics`와 `/actuator/metrics/{name}`이다. 식별자·경로·Tool 원문 이름은
label로 쓰지 않고 유한 집합으로 정규화한다.

| Metric | 핵심 label | 용도 |
|---|---|---|
| `springai.tool.calls.total`, `.duration` | `tool_family`, `risk`, `outcome` | Tool 호출·거부·실패율과 지연 |
| `springai.operation.transitions.total` | `operation_type`, `from`, `to`, `outcome` | 상태 전이 흐름 |
| `springai.operation.outcomes.total` | `operation_type`, `outcome` | conflict·rejected·rollback 집계 |
| `springai.artifact.actions.total`, `.duration` | `artifact_type`, `action`, `outcome` | ingest·link·read 결과 |
| `springai.artifact.reconciliation.runs.total`, `.duration` | `mode`, `outcome` | dry-run/execute 성공·실패 |
| `springai.artifact.reconciliation.items.total` | `mode`, `kind` | orphan·missing·quarantined 수 |
| `springai.external.calls.total`, `.duration` | `dependency`, `outcome` | 외부 의존성 지연·실패·timeout |
| `springai.external.timeouts.total` | `dependency` | timeout 횟수 |
| `springai.external.circuit.open.total` | `dependency` | OPEN 진입·차단 횟수 |
| `springai.external.circuit.state` | `dependency` | `0=CLOSED`, `1=HALF_OPEN`, `2=OPEN` |
| `springai.gate.executions.total`, `.duration` | `gate`, `severity`, `outcome` | parse/build/render/a11y/visual Gate |

Timer의 `count`, `sum`, `max`와 histogram을 이용해 호출량 및 p95를 계산한다. Prometheus registry를
추가하는 운영 환경에서는 동일 이름이 snake case로 노출될 수 있다.

## 4. 상태 조회와 Artifact 조정

아래 경로는 기존 `/api/**` 규칙대로 `X-API-Key` 인증이 필요하다.

| Method / path | 동작 |
|---|---|
| `GET /api/operations/status` | outbox, 마지막 reconciler 결과, 외부 circuit 상태 조회 |
| `POST /api/operations/artifacts/reconcile/dry-run` | 변경 없이 orphan/missing 조사 |
| `POST /api/operations/artifacts/reconcile?confirm=true` | orphan을 quarantine으로 이동하고 missing catalog row를 `QUARANTINED` 처리 |

`confirm=true`가 없는 실제 실행 요청은 400으로 거부한다. 즉시 삭제는 하지 않는다. 현재 outbox
(`ARCH-0508`)는 구현되지 않았으므로 상태 응답은 이를 `NOT_CONFIGURED`로 명시한다. 이 endpoint는
없는 outbox consumer를 정상처럼 가장하지 않으며, 현행 poll 기반 `ArtifactReconciler`의 마지막 결과를
별도로 보여준다.

## 5. Dashboard와 alert 기준

권장 dashboard는 다음 5개 행으로 구성한다.

1. 요청/Tool: Tool family별 호출량, 실패율, 거부율, p95
2. Operation: 상태 전이량, conflict·rejected·rollback 추이
3. Artifact: ingest 실패율, orphan/missing/quarantined 수, 마지막 reconciliation 시각
4. 외부 의존성: dependency별 p95, timeout율, circuit state/OPEN 횟수
5. 품질 Gate: build·render·accessibility·visual parity의 BLOCK/WARN 비율

초기 alert 기준은 5분 window를 기본으로 하며 트래픽이 20건 미만이면 비율 경보를 보류한다.

| 등급 | 조건 | 조치 |
|---|---|---|
| Critical | circuit state가 2로 2분 지속, Tool 실패율 20% 이상, reconciliation execute 실패 | 장애 격리 상태 확인 후 호출 중단·rollback 판단 |
| Warning | 외부 timeout율 5% 이상, Tool 실패율 5% 이상, Operation conflict 5건 이상 | correlation log로 공통 원인 조사 |
| Warning | orphan 또는 missing 1건 이상 | dry-run 결과 검토 후 승인된 execute 수행 |
| Warning | BLOCK Gate 1건 이상 또는 WARN 비율 10% 이상 | 해당 Artifact/Operation의 Gate 결과 확인 |
| Stale | reconciler가 운영 주기의 2배 동안 실행되지 않음 | 스케줄러/수동 dry-run 상태 확인 |

실제 임계값은 WP9 Runbook의 bulkhead·외부 의존성 임계값과 함께 2주간 baseline을 수집한 뒤 조정한다.

## 6. 검증

자동 테스트는 다음을 고정한다.

- HTTP→MCP 인증의 correlation ID 동일성 및 요청 종료 후 MDC 정리
- Operation→Artifact 이벤트의 correlation/actor/operation 연결
- 동적 사용자 ID·경로가 metric label이 되지 않음
- JSON 로그의 secret/token 비노출과 message 상한
- Tool, Operation, Artifact, 외부 호출, Gate metric 생성
- outbox `NOT_CONFIGURED`, reconciler 마지막 상태, circuit 상태 조회

```bash
./gradlew test
./gradlew bootJar
```
