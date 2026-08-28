<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-08-28 -->

# tools

## Purpose
Claude Desktop/Web에 MCP로 노출되는 37개 Tool 클래스(총 102개 `@Tool` 메서드) 패키지.
각 Tool은 `@Tool` 어노테이션 메서드로 구성되며, 실제 로직은 `service/` 패키지에 위임합니다.
모든 Tool은 `config/McpConfig.java`의 `allToolCallbacks` 빈에 등록되어 있습니다.
전체 메서드 단위 목록은 `docs/tool-reference/MCP_Tool_전체목록.md` 참고(37개 파일/102개 메서드,
`McpToolDefinitionSnapshotTest`의 `EXPECTED_TOOL_OBJECT_COUNT`/`EXPECTED_TOOL_METHOD_COUNT`와 일치).

## Key Files

### DB / SQL
| File | Description |
|------|-------------|
| `SqlTool.java` | 임의 SQL 실행(SELECT 전용) 및 AI 기반 쿼리 설명 |
| `SchemaReaderTool.java` | DB 테이블 목록·스키마·관계 조회 |

### CRUD / 게시판 / 마스터-디테일 소스 생성
| File | Description |
|------|-------------|
| `generation/CrudGenerationTool.java` | CRUD 전체 생성/프롬프트 빌드(`llmProvider=auto/claude`) |
| `generation/MasterDetailGenerationTool.java` | 1:N 마스터-디테일 생성/프롬프트 빌드 |
| `generation/BoardGenerationTool.java` | 게시판(BBS) 기능 세트 생성(`llmProvider` 없음, 항상 결정론적) |
| `generation/JoinQueryTool.java` | 조인 SELECT 프롬프트 빌드 |
| `generation/CrudScreenSourceTool.java` | 단일 CRUD 화면(목록/상세/등록/수정) 1개만 미리보기 렌더링 |
| `generation/BoardScreenSourceTool.java` | 단일 게시판 화면 1개만 미리보기 렌더링 |
| `generation/MasterDetailScreenSourceTool.java` | 단일 마스터-디테일 화면 1개만 미리보기 렌더링 |
| `generation/CrudGenerationSnapshotTool.java` | 5축 파이프라인 Ownership 보호용 baseline 스냅샷 등록(파일 미변경) |

### Thymeleaf Layout · 코드 저장 · 검증
| File | Description |
|------|-------------|
| `ThymeleafLayoutTool.java` | Thymeleaf 공통 layout 5종 + GNB 메뉴 컴포넌트 4종 생성 |
| `CodeTemplateTool.java` | eGovFrame 레이어별 코드 템플릿 단독 반환 |
| `CodeSaverTool.java` | 생성된 소스 코드를 파일로 저장, 출력 디렉터리 점검 |
| `CodeValidatorTool.java` | 생성된 Java/XML 코드 유효성·렌더링·빌드 검증 |

### 디자인 참조 분석 (로컬 캡처 / Vision)
| File | Description |
|------|-------------|
| `DesignReferenceTool.java` | 이미지/PDF/Figma 참조 → `UiDesignSpec`/`ScreenSpecification` 초안·승인 |
| `CaptureWebPageTool.java` | 로컬 화면을 Chromium으로 캡처해 Design Artifact 생성(단일/멀티 viewport) |
| `DesignArtifactTool.java` | Design Artifact 조회, Figma Import용 `.figpack` 준비 |

### Figma 연동 (Export / Design System / Orchestration)
| File | Description |
|------|-------------|
| `FigmaExportTool.java` | 승인된 ScreenSpecification → FigmaScreenSpec 생성·검증 |
| `DesignSystemTool.java` | DesignSystemSpec/ComponentRegistry 검증·드리프트 점검 |
| `FigmaDesignOrchestrationTool.java` | 텍스트/참조/이미지 기반 Figma 화면 생성·수정·변환(10개 메서드) |
| `FigmaApprovedSpecificationTool.java` | APPROVED ScreenSpecification → Figma Bundle Artifact 생성 |

### Thymeleaf 마이그레이션 승인 워크플로우
| File | Description |
|------|-------------|
| `ThymeleafBindingGenerationTool.java` | 레거시 JSP → Thymeleaf 생성 미리보기(대상 프로젝트 미변경) |
| `ThymeleafProjectWorkflowTool.java` | Preview → 승인 → 원자 적용 → 재검증 상태기계 |
| `ThymeleafBaselineApprovalTool.java` | 적용된 Thymeleaf 화면을 시각 비교 baseline으로 승인 |

### 프로젝트 초기화
| File | Description |
|------|-------------|
| `ProjectInitializrTool.java` | 신규 eGovFrame 4.3/5.0 WAR/Boot 프로젝트 골격 생성 |
| `ProjectScannerTool.java` | 기존 프로젝트 구조 스캔 |
| `ProjectHealthTool.java` | 프로젝트 건강 상태 점검 |
| `OutputPathResolverTool.java` | 생성 코드 출력 경로 결정 |

### 보안 / 메뉴 / 권한
| File | Description |
|------|-------------|
| `SecurityTemplateTool.java` | Spring Security 설정 템플릿 생성 |
| `MenuTool.java` | eGovFrame 메뉴 구조 조회 및 INSERT SQL 생성 |
| `AuthTool.java` | 인증 권한 INSERT SQL 생성 |

### 공통코드 / 직원
| File | Description |
|------|-------------|
| `CommonCodeTool.java` | eGovFrame 공통코드 조회·검색 |
| `EmployeeTool.java` | 직원 CRUD — 목록 조회, 단건 조회, 등록, 수정, 삭제 |

### RAG / 문서
| File | Description |
|------|-------------|
| `RagTool.java` | RAG 문서 등록(텍스트/URL/디렉터리) 및 유사도 검색 |

### 유틸리티 / 워크플로우
| File | Description |
|------|-------------|
| `DateTimeTool.java` | 현재 일시 반환(IANA 시간대), 섭씨→화씨 변환 |
| `GenerationHistoryTool.java` | 코드 생성 이력 저장·조회 |
| `WorkflowGuideTool.java` | eGovFrame CRUD 생성 워크플로우 단계별 안내 |

## For AI Agents

### Working In This Directory
- Tool 클래스는 **얇은 래퍼**: 비즈니스 로직 금지 → `service/` 패키지에 위임
- 새 Tool 추가 시 `config/McpConfig.java`의 `allToolCallbacks()` 파라미터 + `toolObjects(...)` 목록 양쪽 추가 필수
- `@Tool(description = "...")` 설명은 Claude가 tool 선택 기준으로 사용 — 한국어로 상세하게 작성
- `CrudGenerationTool`/`MasterDetailGenerationTool`/`BoardGenerationTool`/`JoinQueryTool`/
  `~ScreenSourceTool` 3종/`CrudGenerationSnapshotTool`은 `generation/` 하위 패키지에 있다 —
  이 디렉터리 바로 아래(flat)에 있는 나머지 Tool과 위치가 다르다

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
