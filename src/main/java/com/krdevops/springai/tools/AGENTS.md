<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# tools

## Purpose
Claude Desktop에 노출되는 MCP Tool 구현체 19종. `@Tool` 어노테이션으로 선언된 메서드가
MCP 프로토콜을 통해 Claude가 호출할 수 있는 도구로 등록됩니다.
각 Tool은 대응하는 `service/` 클래스에 비즈니스 로직을 위임합니다.

## Key Files

| File | Description |
|------|-------------|
| `SecurityTemplateTool.java` | eGovFrame 보안 설정 XML 자동 생성 도구 |
| `ProjectInitializrTool.java` | eGovFrame 프로젝트 초기화 구조 생성 도구 |
| `CrudPromptBuilderTool.java` | 테이블 기반 eGovFrame CRUD 소스 생성 프롬프트 빌더 |
| `SchemaReaderTool.java` | DB 테이블 스키마/컬럼 정보 조회 (`getTableSchema`, `getTableList`) |
| `CodeSaverTool.java` | Claude 생성 소스를 파일로 저장 (`saveGeneratedCode`) |
| `CodeTemplateTool.java` | eGovFrame 코드 템플릿 제공 |
| `CodeValidatorTool.java` | 생성 코드 유효성 검사 |
| `SqlTool.java` | SQL 생성 및 실행 지원 |
| `RagTool.java` | VectorStore 기반 문서 검색 |
| `ProjectScannerTool.java` | 기존 프로젝트 구조 스캔 |
| `ProjectHealthTool.java` | 프로젝트 건강도 검사 |
| `OutputPathResolverTool.java` | 생성 파일 출력 경로 결정 |
| `WorkflowGuideTool.java` | eGovFrame 개발 워크플로 가이드 |
| `AuthTool.java` | 인증/인가 도구 |
| `CommonCodeTool.java` | 공통 코드 조회 도구 |
| `EmployeeTool.java` | 직원 CRUD 도구 (`getEmployeeList`, `getEmployee`, `createEmployee`, ...) |
| `GenerationHistoryTool.java` | 코드 생성 이력 조회 도구 |
| `MenuTool.java` | 메뉴 구조 조회 도구 |
| `DateTimeTool.java` | 현재 시각 조회 (`getCurrentDateTime`) |

## For AI Agents

### Working In This Directory
- **신규 Tool 추가 절차:**
  1. `@Component` + `@RequiredArgsConstructor` 클래스 작성
  2. `@Tool(description = "한국어로 상세 설명")` 메서드 선언 — Claude가 설명으로 Tool 선택
  3. `config/McpConfig.java`에 `MethodToolCallbackProvider` 빈 등록 **필수**
- Tool 클래스는 얇게 유지 — 로직은 `service/`에 위임
- `description`은 Claude가 언제 이 Tool을 사용할지 이해할 수 있도록 구체적으로 작성

### Common Patterns
```java
@Component
@RequiredArgsConstructor
public class MyTool {
    private final MyService myService;

    @Tool(description = "기능 설명을 한국어로 상세히 작성")
    public String myMethod(String param) {
        return myService.doSomething(param);
    }
}
```

### Testing Requirements
- Tool 메서드는 서비스 목(Mock) 기반 단위 테스트
- Claude Desktop에서 실제 호출 테스트: `tail -f /tmp/springai-mcp.log`

## Dependencies

### Internal
- `service/` — 모든 비즈니스 로직
- `config/McpConfig.java` — Tool 등록 허브

### External
- Spring AI `@Tool` 어노테이션
- Spring AI `MethodToolCallbackProvider`

<!-- MANUAL: -->
