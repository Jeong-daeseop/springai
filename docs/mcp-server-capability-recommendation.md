# MCP 서버 Capability 제공 권장안

이 문서는 `springai` MCP 서버가 `Tools`, `Resources`, `Prompts`를 어떤 기준으로 제공하는 것이 좋은지 검토한 결과다.

## 결론

이 프로젝트에서는 다음 기준으로 나누는 것이 가장 적절하다.

| 폴더/구현 | 현재 의미 | MCP 제공 방식 |
| --- | --- | --- |
| `src/main/java/com/krdevops/springai/tools/*.java` | 실행 기능 | Tool |
| `docs/*` | 설명서, 설계 문서, 가이드 | Resource |
| `prompts/*` | AI 판단 규칙 | Resource |
| `templates/*` | 생성 요청 양식 | Prompt |

핵심 기준은 다음과 같다.

- **Tools**: 실행한다.
- **Resources**: 읽어서 참고한다.
- **Prompts**: 작업 요청을 구성한다.

## Tools 제공 기준

Tool은 서버가 실제 동작을 수행해야 할 때 제공한다.

### 제공하면 좋은 경우

- DB를 조회해야 한다.
- 파일을 저장해야 한다.
- 코드를 생성해야 한다.
- SQL을 생성해야 한다.
- Vector Store를 검색해야 한다.
- 프로젝트 구조를 분석해야 한다.
- 코드 표준 준수 여부를 검증해야 한다.
- 외부 시스템 또는 내부 서비스 로직을 실행해야 한다.

### 제공하지 않는 것이 좋은 경우

- 단순 설명 문서다.
- 읽기 전용 가이드다.
- 프롬프트 양식이다.
- 사람이 검토해야 하는 정책 문서다.
- 실행 없이 LLM이 참고만 하면 되는 자료다.

### 이 프로젝트 Tool 권장 목록

현재 `@Tool` 기반 클래스들은 Tool로 제공하는 것이 적절하다.

| Tool | 제공 이유 |
| --- | --- |
| `SchemaReaderTool` | DB 테이블/컬럼/관계 조회 실행 |
| `CrudPromptBuilderTool` | CRUD 생성 프롬프트 또는 자동 생성 수행 |
| `CodeSaverTool` | 파일 저장 또는 서버 템플릿 기반 소스 생성 |
| `CodeValidatorTool` | 생성 코드 검증 실행 |
| `ProjectScannerTool` | 기존 프로젝트 구조 분석 |
| `ProjectHealthTool` | 프로젝트 완성도 점검 |
| `ProjectInitializrTool` | 신규 프로젝트 파일 생성 |
| `SecurityTemplateTool` | Security 템플릿 생성 또는 파일 저장 |
| `MenuTool` | 메뉴 구조 조회 및 메뉴 SQL 생성 |
| `AuthTool` | 프로그램 목록 조회 및 권한 SQL 생성 |
| `SqlTool` | 읽기 전용 SQL 실행 및 실행 계획 분석 |
| `RagTool` | RAG 문서 등록 및 Vector Store 검색 |
| `CommonCodeTool` | 공통코드 조회 |
| `GenerationHistoryTool` | 생성 이력 저장/조회 |
| `OutputPathResolverTool` | 출력 경로 계산 |
| `WorkflowGuideTool` | 작업 단계 안내 |
| `EmployeeTool` | 직원 데이터 CRUD |
| `DateTimeTool` | 시간 조회, 온도 변환 |
| `CodeTemplateTool` | 서버 내부 템플릿 반환 |

### Tool 설계 원칙

- Tool 이름은 동사형으로 명확하게 작성한다.
- description에는 언제 사용해야 하는지와 사용 금지 조건을 포함한다.
- input schema는 가능한 한 좁게 정의한다.
- DB 변경은 직접 실행하지 않고 SQL 생성까지만 수행한다.
- 파일 저장은 허용된 경로 안에서만 수행한다.
- Tool 결과는 사람이 읽을 수 있는 text와 구조화 가능한 결과를 함께 고려한다.
- 실패는 숨기지 말고 오류 메시지로 반환한다.

## Resources 제공 기준

Resource는 LLM이 읽고 참고해야 하는 컨텍스트다.

실행 기능이 아니라 지식, 문서, 규칙, 상태 snapshot을 제공한다.

### 제공하면 좋은 경우

- Tool 사용 설명서다.
- 시스템 운영 규칙이다.
- 코드 생성 규칙이다.
- 보안/CRUD/RAG 설계 문서다.
- DB 스키마 snapshot이다.
- 프로젝트 구조 분석 결과다.
- LLM이 답변 또는 코드 생성 전에 참고해야 하는 자료다.

### 제공하지 않는 것이 좋은 경우

- 실행이 필요한 기능이다.
- 사용자 입력값을 받아 최종 프롬프트로 조립해야 한다.
- DB나 파일 시스템 변경을 수행한다.
- 매번 계산해야 하는 결과다.

### 이 프로젝트 Resource 권장 목록

| Resource | 권장 URI | 이유 |
| --- | --- | --- |
| `docs/tool-catalog.md` | `resource://docs/tool-catalog` | Tool 전체 설명서 |
| `docs/mcp-tools-resources-prompts-guide.md` | `resource://docs/mcp-tools-resources-prompts-guide` | MCP capability 설명 |
| `docs/security/*` | `resource://docs/security/{name}` | Security 설계/구현 참고 |
| `docs/crud/*` | `resource://docs/crud/{name}` | CRUD 생성 참고 |
| `docs/rag/*` | `resource://docs/rag/{name}` | RAG 구조 참고 |
| `docs/architecture/*` | `resource://docs/architecture/{name}` | 아키텍처 참고 |
| `prompts/system-prompt.md` | `resource://prompts/system-prompt` | AI 역할/금지사항 |
| `prompts/tool-selection.md` | `resource://prompts/tool-selection` | Tool 선택 기준 |
| `prompts/code-generation-rule.md` | `resource://prompts/code-generation-rule` | 코드 생성 표준 |

### prompts 폴더를 Resource로 보는 이유

`prompts/system-prompt.md`, `prompts/tool-selection.md`, `prompts/code-generation-rule.md`는 이름은 prompt지만 MCP Prompt로 제공하기보다 Resource로 제공하는 편이 낫다.

이유:

- 사용자가 매번 선택하는 작업 양식이 아니다.
- AI가 항상 참고해야 하는 운영 규칙이다.
- 치환 파라미터로 최종 사용자 요청을 만드는 템플릿이 아니다.
- Tool 선택과 코드 생성 판단을 보조하는 컨텍스트다.

따라서 `prompts` 폴더는 MCP 관점에서 **AI 판단 규칙 Resource**로 제공하는 것이 적절하다.

## Prompts 제공 기준

Prompt는 사용자가 특정 작업을 시작할 때 선택하는 재사용 가능한 작업 지시 템플릿이다.

### 제공하면 좋은 경우

- 반복적으로 사용하는 작업 요청 양식이다.
- 사용자 입력값을 받아 최종 프롬프트를 만든다.
- `{{tableName}}`, `{{domain}}`, `{{tableSchema}}` 같은 치환값이 있다.
- CRUD 생성, Security 생성, 메뉴 SQL 생성처럼 작업 유형이 명확하다.
- 클라이언트에서 prompt picker 또는 slash command로 선택할 수 있다.

### 제공하지 않는 것이 좋은 경우

- 단순 참고 문서다.
- 운영 규칙 문서다.
- 코드 생성 표준 문서다.
- Tool 자체 설명서다.
- 실행 결과를 반환해야 하는 기능이다.

### 이 프로젝트 Prompt 권장 목록

| Prompt | 파일 | 용도 |
| --- | --- | --- |
| `code-generation` | `templates/prompt-template.md` | 공통 코드 생성 요청 |
| `crud-generation` | `templates/crud-prompt-template.md` | CRUD 생성 요청 |
| `security-generation` | `templates/security-prompt-template.md` | Security 생성 요청 |
| `menu-generation` | `templates/menu-prompt-template.md` | 메뉴/프로그램/권한 SQL 생성 요청 |

### Prompt 입력값 예시

`crud-generation` Prompt는 다음 입력값을 받을 수 있다.

```json
{
  "userRequest": "COMTNEMPLYRINFO CRUD 만들어줘",
  "database": "ebt",
  "tableName": "COMTNEMPLYRINFO",
  "domain": "Employer",
  "domainLc": "employer",
  "packageName": "egovframework.let.emp",
  "urlPrefix": "/emp/employer",
  "egovVersion": "5.0",
  "outputPath": "/Users/user/Desktop/egov-generated/employer",
  "tableSchema": "...",
  "tableRelations": "..."
}
```

## 요청 유형별 권장 흐름

### CRUD 생성 요청

```text
Resource 참고:
  resource://prompts/system-prompt
  resource://prompts/tool-selection
  resource://prompts/code-generation-rule
  resource://docs/tool-catalog

Tool 실행:
  SchemaReaderTool.getTableSchema
  SchemaReaderTool.getTableRelations
  OutputPathResolverTool.*
  CrudPromptBuilderTool.buildFullCrudPrompt
  CodeValidatorTool.validateGeneratedCodeDirectory
  GenerationHistoryTool.saveGenerationHistory

Prompt 사용:
  crud-generation
```

### Security 생성 요청

```text
Resource 참고:
  resource://prompts/system-prompt
  resource://prompts/tool-selection
  resource://docs/security/*

Tool 실행:
  WorkflowGuideTool.suggestSecurityMenuAuthWorkflow
  SecurityTemplateTool.getSecurityTemplate
  AuthTool.generateAuthInsertSql

Prompt 사용:
  security-generation
```

### 메뉴 생성 요청

```text
Resource 참고:
  resource://prompts/tool-selection
  resource://docs/tool-catalog

Tool 실행:
  MenuTool.getMenuStructure
  AuthTool.getProgramList
  MenuTool.generateMenuInsertSql
  AuthTool.generateAuthInsertSql

Prompt 사용:
  menu-generation
```

### RAG 검색 요청

```text
Resource 참고:
  resource://docs/rag/*

Tool 실행:
  RagTool.ragSearch

Prompt 사용:
  필요 시 code-generation 또는 별도 rag-answer Prompt
```

## Capability별 구현 우선순위

### 1단계: Tools 안정화

현재 구현된 `@Tool` 기반 기능을 유지한다.

우선순위:

1. Tool description 보강
2. 입력 검증 강화
3. 위험 작업 확인 절차 명확화
4. 오류 메시지 표준화
5. 구조화 출력 도입 검토

### 2단계: Resources 제공

`docs`, `prompts` 파일을 읽기 전용 Resource로 제공한다.

우선순위:

1. `docs/tool-catalog.md`
2. `prompts/system-prompt.md`
3. `prompts/tool-selection.md`
4. `prompts/code-generation-rule.md`
5. `docs/security/*`
6. `docs/crud/*`
7. `docs/rag/*`

권장 URI 체계:

```text
resource://docs/{category}/{name}
resource://prompts/{name}
resource://schema/{database}/{tableName}
resource://project/{projectId}/structure
```

### 3단계: Prompts 제공

`templates` 파일을 MCP Prompt로 제공한다.

우선순위:

1. `crud-generation`
2. `security-generation`
3. `menu-generation`
4. `code-generation`

Prompt는 사용자 입력값을 받아 최종 메시지를 구성해야 한다.

### 4단계: 동적 Resource/Prompt 확장

정적 Markdown 파일 외에 동적 컨텍스트도 Resource로 제공할 수 있다.

예:

- 현재 DB 스키마
- 최근 생성 이력
- 프로젝트 스캔 결과
- RAG 검색 결과 링크

## 현재 프로젝트 상태 평가

| Capability | 현재 상태 | 평가 |
| --- | --- | --- |
| Tools | `McpConfig`에서 `MethodToolCallbackProvider`로 등록 | 적절함 |
| Resources | 문서 파일은 있으나 MCP Resource 등록 없음 | 구조는 준비됨 |
| Prompts | 템플릿 파일은 있으나 MCP Prompt 등록 없음 | 구조는 준비됨 |

현재 상태는 다음과 같이 볼 수 있다.

```text
현재:
  MCP Tool Server
  + 내부 운영 문서 구조

권장 목표:
  MCP Tool Server
  + Resource Provider
  + Prompt Provider
```

## 최종 권장안

이 프로젝트는 다음 기준으로 MCP capability를 제공하는 것이 좋다.

1. `tools` Java 클래스는 계속 Tool로 제공한다.
2. `docs`는 설명서 Resource로 제공한다.
3. `prompts`는 AI 판단 규칙 Resource로 제공한다.
4. `templates`는 사용자 선택형 Prompt로 제공한다.
5. 동적 DB 스키마와 프로젝트 구조는 Resource Template로 제공한다.
6. 파일 저장, SQL 생성, 코드 검증처럼 실행이 필요한 것은 Prompt나 Resource가 아니라 Tool로 유지한다.

이 구조가 유지보수성과 MCP 의미론을 가장 잘 맞춘다.
