# Generation 패키지 이동 체크리스트

Board·Master/Detail 생성은 `service/generation/*` Pipeline을 운영 경로로 사용한다.
구형 Orchestrator와 Compatibility Facade는 제거 완료했다.

## 이동 순서

1. MCP Adapter가 Feature Use Case만 참조하는지 Snapshot·구조 테스트 확인
2. 외부 테스트·문서·직접 호출부를 Pipeline Use Case로 전환
3. 구형 Orchestrator의 공개 생성자와 직접 참조 제거
5. 기능별 Infrastructure 의존성을 `service/generation/{feature}` 하위로 이동
6. 전체 테스트와 `bootJar` 실행 후 Deprecated 표식 제거 여부 결정

## 제거 결과

- 구형 Orchestrator 직접 호출 테스트와 Compatibility Facade를 제거했다.
- 외부 결과 VO는 Pipeline Result Assembler의 반환 계약으로 유지한다.

## 테스트 마이그레이션 현황

- 완료: Board·Master/Detail Pipeline 실패 격리 테스트
- 완료: Use Case 운영 주입 경계 테스트
- Compatibility 경계 테스트는 운영 Pipeline 생성자 주입만 검증하도록 정리
- 완료: FK Resolver 단위 테스트
- 완료: MCP Facade → Feature Use Case 위임 테스트
- 완료: Master/Detail MCP Facade → Dispatch Use Case 위임 테스트
- 완료: CRUD MCP Facade → Dispatch Use Case 위임 테스트
- 완료: Board Planner 실패 → 기존 결과 VO 변환 테스트
- 완료: Master/Detail Planner 실패 → 기존 결과 VO 변환 테스트
- 완료: Master/Detail Planner 패키지·테이블 사전검증 테스트
- 완료: Master/Detail Renderer Layout 재사용·레이어 수 보존 테스트
- 완료: Master/Detail Contract Verifier 누락 레이어 테스트
- 완료: Board Contract Verifier 누락 레이어 테스트
- 완료: Board CSS Processor 실패 정책 테스트
- 완료: Master/Detail MainController 목록 미저장 시 보정 생략 테스트
- 완료: Master/Detail Servlet Scan 설정 파일 누락 시 생략 테스트
- 완료: Board Entry Point 목록 미저장 시 갱신 생략 테스트
- 완료: Master/Detail 성공 Pipeline의 WRITE→POST_WRITE 실행 순서 테스트
- 완료: Board 성공 Pipeline의 WRITE→POST_WRITE→Verifier→History 실행 순서 테스트
- 완료: Master/Detail 성공 Result Assembler 파일·검증·이력 요약 보존 테스트
- 완료: Board 성공 Result Assembler 파일·검증·이력 요약 보존 테스트
- 완료: Master/Detail POST_WRITE Processor 실패 누적 테스트
- 완료: Board POST_WRITE Processor 실패 누적 테스트
- 완료: `CrudPromptBuilderToolTest`의 Board·Master/Detail 생성 시나리오를 Pipeline 실행·Result Assembler 경계로 전환
- 완료: 생성 Tool 테스트에서 구형 Orchestrator 직접 Mock/호출 제거
- 완료: 구형 Orchestrator 직접 호출 테스트 2개 클래스 제거

실행 예:

```bash
./gradlew test --tests '*GenerationMcpFacadeTest'
./gradlew test --tests 'com.krdevops.springai.tools.CrudPromptBuilderToolTest'
```

## 완료 기준

- Use Case 운영 생성자에 구형 Orchestrator 타입이 없음
- 구형 Orchestrator 및 Compatibility Facade 소스가 없음
- Feature Pipeline 실패 격리 테스트 통과
- MCP Tool Snapshot 변경 없음
- `./gradlew test bootJar` 성공
