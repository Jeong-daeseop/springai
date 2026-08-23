# 5축 SpringAI 파이프라인 Release Gate 운영 Runbook

## 목적

MCP Tool 계약, API 인증, DB·Redis 연결, Evidence·Handoff 결과를 운영 배포 전에 같은 기준으로 검증한다.

## 1. 기본 검증

```bash
./gradlew test bootJar
./gradlew prodBootJarSmoke
git diff --check
```

세 명령이 모두 성공해야 애플리케이션 산출물을 배포 후보로 취급한다.

## 2. MCP Tool baseline

현재 승인된 Tool 계약 hash를 GitHub Actions Repository Variable `MCP_TOOL_SNAPSHOT_HASH`에 저장한다.
로컬 운영 설정은 `.env.example`의 동일한 변수명을 사용한다.
CI의 `McpToolDefinitionSnapshotTest`는 Tool 이름·설명·입력 Schema 전체 hash를 계산해 baseline과 비교한다. 입력 Schema 객체 키 순서는 canonicalize되므로 등록 구현의 JSON 출력 순서만 바뀐 경우 drift로 판정하지 않는다.

Tool 계약을 의도적으로 변경할 때는 다음 순서로 처리한다.

1. 변경된 Tool 정의와 입력 Schema를 리뷰한다.
2. baseline JSON과 `MCP_TOOL_SNAPSHOT_HASH`를 함께 갱신한다.
3. CI에서 snapshot 및 전체 회귀 테스트를 다시 실행한다.

## 3. 운영 인프라 Smoke

MySQL과 Redis가 실행된 환경에서 다음을 실행한다.

```bash
./scripts/pipeline-live-smoke.sh
```

스크립트는 `.env`를 자동 로드하고 `PIPELINE_INFRA_SMOKE_LIVE=true`로 Gradle live smoke를 실행한다.

또는 실행 중인 애플리케이션에 인증된 요청을 보낸다.

```bash
curl -H "X-API-Key: $APP_API_KEY" \
  http://localhost:8080/api/operations/infrastructure-smoke
```

응답의 `databaseReady`와 `redisReady`가 모두 `true`여야 한다.

이 검증이 외부 인프라 미구성으로 실행되지 않은 경우에는 완료 상태로 표시하지 않고 구현목록의 `[~]` 상태를 유지한다.

## 4. Release Gate·Handoff 확인

Release Gate API는 모든 검증 결과를 명시적으로 전달한다.

```bash
curl -X POST -H "X-API-Key: $APP_API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"binding":true,"build":true,"render":true,"accessibility":true}' \
  http://localhost:8080/api/pipeline/release-readiness
```

응답의 `ready=true`와 빈 `failedGateNames`를 확인한다.

승인된 Handoff를 Agent 관점으로 투영할 때는 동일한 Gate 결과를 전달한다.

```bash
curl -X POST -H "X-API-Key: $APP_API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"binding":true,"build":true,"render":true}' \
  "http://localhost:8080/api/pipeline/handoff/$BUNDLE_ID/projection?audience=AGENT"
```

응답의 `releaseReady`, `failedGateNames`, `auditSnapshotHash`가 Release Gate 및 Evidence와 일치해야 한다.

MCP baseline을 API에서 직접 확인하려면 승인된 hash를 함께 전달한다.

```bash
curl -G -H "X-API-Key: $APP_API_KEY" \
  --data-urlencode "expectedHash=$MCP_TOOL_SNAPSHOT_HASH" \
  http://localhost:8080/api/pipeline/mcp-tools
```

응답의 `baselineMatched`가 `true`여야 한다.

운영 환경변수 `MCP_TOOL_SNAPSHOT_HASH`가 설정되어 있으면 `expectedHash` query parameter를 생략해도 서버가 환경변수 baseline을 자동으로 사용한다.

`POST /api/pipeline/release-readiness`의 `ready=true`를 확인한 뒤 Handoff Projection 응답에서 다음을 확인한다.

- `releaseReady=true`
- `failedGateNames=[]`
- Evidence와 동일한 `auditSnapshotHash`

하나라도 불일치하면 배포를 중지하고 해당 Evidence·승인 감사 이력을 재검토한다.

## 5. 실패 처리

- MCP hash 불일치: Tool 계약 변경 승인 및 baseline 재생성
- DB 실패: JDBC URL·계정·MySQL 상태 확인
- Redis 실패: `REDIS_URI`·Redis Stack 상태 확인
- Release Gate 실패: `failedGateNames`에 해당하는 Binding·Build·Render Evidence 재생성
- audit hash 불일치: 승인 이후 변경 여부와 Handoff 재생성 여부 확인

## 완료 기준

다음 조건을 모두 만족하면 현재 Release Gate 운영 작업을 완료로 기록한다.

- `./scripts/pipeline-live-smoke.sh` 성공
- `./gradlew check` 성공
- `./gradlew bootJar` 성공
- 구현목록에 `[~]` 또는 `[ ]` 항목이 없음
