# ARCH-WP1 MCP 보안 운영 Runbook

작성일: 2026-08-03  
대상: Streamable HTTP `/mcp/**`, 레거시 SSE `/sse/**`

## 1. 기본 정책

- 기본 모드는 `REQUIRED`이며 등록된 92개 MCP Tool 모두 인증 전에는 실행되지 않는다.
- 요청은 `X-MCP-Token` 헤더로 인증한다. credential 원문은 `SecurityContext`, `ActorContext`, 로그에 저장하지 않는다.
- 모든 `@Tool` 메서드는 `@McpToolRisk(READ|EXTERNAL|DB_WRITE|FILE_WRITE|APPLY)`를 선언해야 한다. 누락 시 기동이 실패한다.
- `server.address`가 loopback이 아니면 `MCP_SHARED_TOKEN`과 `MCP_AUTH_MODE=REQUIRED`가 모두 설정되어야 한다.

## 2. 클라이언트 전환

1. 32바이트 이상의 무작위 token을 비밀 저장소에 생성한다.
2. 서버에 `MCP_SHARED_TOKEN`을 주입하고 `MCP_AUTH_MODE=COMPATIBILITY`,
   `MCP_LEGACY_CREDENTIAL_VALID_UNTIL=<짧은 UTC 만료 시각>`으로 한 번 기동한다.
3. MCP 클라이언트가 모든 `/mcp/**` 요청에 `X-MCP-Token`을 보내도록 설정한다.
4. 감사 로그에서 `decision=ALLOW`만 발생하고 `ALLOW_LEGACY`가 더 이상 발생하지 않는지 확인한다.
5. `MCP_AUTH_MODE=REQUIRED`로 전환하고 기존 `FIGMA_MCP_SHARED_SECRET`/Thymeleaf Tool 인자 사용을 중단한다.

`AUDIT_ONLY`는 loopback 개발 환경의 짧은 진단에만 사용한다. non-loopback에서는 기동 guard가 차단한다.

## 3. 무중단 token 회전

1. 현재 값을 `MCP_PREVIOUS_SHARED_TOKEN`으로 옮긴다.
2. 새 값을 `MCP_SHARED_TOKEN`에 설정한다.
3. `MCP_PREVIOUS_TOKEN_VALID_UNTIL`을 짧은 UTC ISO-8601 만료 시각으로 설정한다.
4. 서버 재기동 후 클라이언트를 새 token으로 전환한다.
5. 감사 로그의 `credentialVersion=previous`가 0건인지 확인한다.
6. 만료 후 이전 token 환경변수 두 개를 제거하고 다시 기동한다.

이전 token은 만료 시각이 없거나 이미 지났으면 거부된다. 회전 중 문제가 생기면 새 token을 폐기하고 이전 token을 다시 현재 token으로 복구한다.

## 4. 실패 코드와 감사

| 코드 | 의미 | 조치 |
|---|---|---|
| `MCP_TOKEN_MISSING` | 헤더 누락 | 클라이언트 헤더 설정 확인 |
| `MCP_TOKEN_INVALID` | 불일치 token | 비밀 저장소와 client 설정 비교 |
| `MCP_TOKEN_EXPIRED` | 이전 token 유예 만료 | 새 token으로 교체 |
| `MCP_CREDENTIAL_NOT_CONFIGURED` | 서버 공통 token 미설정 | `MCP_SHARED_TOKEN` 주입 |
| `MCP_AUTH_REQUIRED` | 인증 또는 위험 등급 권한 부족 | 인증 주체·정책 확인 |

감사 로그에는 `correlationId`, Tool 이름, 위험 등급, 결정, credential 버전만 남긴다. token, secret, API key, fileKey 원문은 기록하지 않는다.

## 5. 검증 명령

```bash
./gradlew test --tests 'com.krdevops.springai.config.mcp.*' \
  --tests 'com.krdevops.springai.config.McpToolDefinitionSnapshotTest'
./gradlew bootJar
```

MCP schema 변경 여부는 `src/test/resources/mcp/tool-definitions-baseline.json`과 snapshot 테스트로 확인한다.
