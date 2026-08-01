# 코드 리뷰: c9ce284

## 대상

- 커밋: `c9ce284 refactor: replace legacy orchestrators with generation pipelines`
- 범위: Board·Master/Detail 생성 Pipeline 전환, MCP Adapter 분리, 구형 Orchestrator 및 직접 호출 테스트 제거

## 리뷰 결과

**승인 권고: APPROVE**

### 확인한 항목

- Board와 Master/Detail Use Case가 Pipeline과 Result Assembler만 주입받는다.
- 구형 `BoardOrchestrationService`, `MasterDetailOrchestrationService` 및 Compatibility Facade가 제거되었다.
- 직접 호출 테스트가 Planner·Renderer·Processor·Verifier·Pipeline 테스트로 분리되었다.
- MCP Tool 이름·입력 Schema Snapshot이 통과한다.
- 구형 클래스가 재도입되지 않도록 구조 테스트가 추가되었다.
- Pipeline 실패·검증·이력·결과 조립 경로가 테스트된다.

## 검증

```text
./gradlew test bootJar  ✅
git diff --check       ✅
```

## 잔여 확인사항

- 운영 반영 전 CI에서 `clean test bootJar`를 한 번 더 실행한다.
- Board·Master/Detail 자동 생성 Smoke Test를 운영 대상 DB/출력 경로에서 수행한다.
- 다음 변경부터는 `feature/*` 브랜치에서 작업 후 `main`으로 PR을 생성한다.

