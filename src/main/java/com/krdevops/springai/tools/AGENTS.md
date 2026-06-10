<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# tools

## Purpose
Claude Desktop/Web에 MCP로 노출되는 19개 Tool 클래스 패키지.
각 Tool은 `@Tool` 어노테이션 메서드로 구성되며, 실제 로직은 `service/` 패키지에 위임합니다.
모든 Tool은 `config/McpConfig.java`의 `allToolCallbacks` 빈에 등록되어 있습니다.

## Key Files

| File | Description |
|------|-------------|
| `DateTimeTool.java` | 현재 일시 반환 (IANA 시간대 지원) |
| `EmployeeTool.java` | 직원 CRUD — 목록 조회, 단건 조회, 등록, 수정, 삭제 |
| `SchemaReaderTool.java` | DB 테이블 목록·스키마·관계 조회 |
| `CodeSaverTool.java` | 생성된 소스 코드를 파일로 저장 |
| `CodeTemplateTool.java` | eGovFrame 레이어별 코드 템플릿 제공 |
| `CodeValidatorTool.java` | 생성된 Java/XML 코드 유효성 검사 |
| `RagTool.java` | RAG 문서 등록(텍스트/URL/디렉터리) 및 검색 |
| `GenerationHistoryTool.java` | 코드 생성 이력 저장·조회 |
| `ProjectScannerTool.java` | 기존 프로젝트 구조 스캔 |
| `ProjectHealthTool.java` | 프로젝트 건강 상태 점검 |
| `ProjectInitializrTool.java` | 신규 eGovFrame 프로젝트 초기화 |
| `CommonCodeTool.java` | eGovFrame 공통코드 조회·검색 |
| `MenuTool.java` | eGovFrame 메뉴 구조 조회 및 INSERT SQL 생성 |
| `AuthTool.java` | 인증 권한 INSERT SQL 생성 |
| `SecurityTemplateTool.java` | Spring Security 설정 템플릿 생성 |
| `CrudPromptBuilderTool.java` | CRUD 전체/마스터-디테일/조인 SELECT 프롬프트 빌드 |
| `SqlTool.java` | 임의 SQL 실행 및 AI 기반 쿼리 설명 |
| `OutputPathResolverTool.java` | 생성 코드 출력 경로 결정 |
| `WorkflowGuideTool.java` | eGovFrame CRUD 생성 워크플로우 단계별 안내 |

## For AI Agents

### Working In This Directory
- Tool 클래스는 **얇은 래퍼**: 비즈니스 로직 금지 → `service/` 패키지에 위임
- 새 Tool 추가 시 `config/McpConfig.java`의 `allToolCallbacks()` 파라미터 + `toolObjects(...)` 목록 양쪽 추가 필수
- `@Tool(description = "...")` 설명은 Claude가 tool 선택 기준으로 사용 — 한국어로 상세하게 작성

### Common Patterns
```java
@Component
@RequiredArgsConstructor
public class MyTool {
    private final MyService myService;

    @Tool(description = "Claude가 이 도구를 선택하는 한국어 설명 — 언제, 무엇을 하는지 명시")
    public String myMethod(String param) {
        return myService.doSomething(param);
    }
}
```

## Dependencies

### Internal
- `service/` — 모든 비즈니스 로직 위임 대상
- `config/McpConfig.java` — Tool 빈 등록 중앙화

### External
- Spring AI `@Tool` 어노테이션

<!-- MANUAL: -->
