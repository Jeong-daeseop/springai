# MCP Tools, Resources, Prompts 제공 기준

MCP 서버가 `Tools`, `Resources`, `Prompts`를 제공한다는 것은 단순히 프로젝트에 `docs`, `prompts`, `templates` 폴더가 있다는 뜻이 아니다.

MCP 서버가 클라이언트에게 다음 capability를 제공한다는 뜻이다.

- `Tools`: 실행 가능한 기능
- `Resources`: 읽을 수 있는 컨텍스트
- `Prompts`: 재사용 가능한 작업 지시 템플릿

## 전체 기준

| 구분 | 기준 | 제어 주체 | 예시 |
| --- | --- | --- | --- |
| Tools | 외부 시스템에 행동을 수행해야 할 때 | 모델 중심 | DB 조회, 파일 저장, 코드 생성, SQL 생성 |
| Resources | LLM에게 참고시킬 컨텍스트 데이터를 제공할 때 | 애플리케이션/클라이언트 중심 | 문서, DB 스키마, 파일, 프로젝트 구조 |
| Prompts | 재사용 가능한 작업 지시 템플릿을 제공할 때 | 사용자 중심 | CRUD 생성 프롬프트, 보안 생성 프롬프트 |

## Tools 제공 기준

`Tools`는 실행 가능한 기능이다.

사용자의 요청을 처리하기 위해 서버 쪽 로직을 실행해야 하는 경우 Tool로 제공한다.

### 판단 기준

- DB를 조회해야 한다.
- 파일을 저장해야 한다.
- 코드를 생성해야 한다.
- SQL을 만들어야 한다.
- Vector Store를 검색해야 한다.
- 프로젝트 상태를 분석해야 한다.

### 이 프로젝트 예시

| 사용자 요청 | 제공 기준 | Tool |
| --- | --- | --- |
| `COMTNEMPLYRINFO 스키마 보여줘` | DB 조회 실행 필요 | `SchemaReaderTool.getTableSchema` |
| `CRUD 생성해줘` | 스키마 기반 생성 로직 필요 | `CrudPromptBuilderTool.buildFullCrudPrompt` |
| `파일 저장해줘` | 파일 시스템 쓰기 필요 | `CodeSaverTool.saveGeneratedCode` |
| `메뉴 SQL 만들어줘` | DB 상태 확인 + SQL 생성 필요 | `MenuTool.generateMenuInsertSql` |
| `RAG 검색해줘` | Vector Store 검색 필요 | `RagTool.ragSearch` |

### 현재 프로젝트 상태

현재 `src/main/java/com/krdevops/springai/config/McpConfig.java`는 `MethodToolCallbackProvider`로 Tool 객체들을 MCP에 등록한다.

```java
@Bean
public ToolCallbackProvider allToolCallbacks(...) {
    return MethodToolCallbackProvider.builder()
        .toolObjects(...)
        .build();
}
```

따라서 현재 구현 기준으로는 **Tools 제공은 구현되어 있다.**

현재 Tool 규모는 `src/main/java/com/krdevops/springai/tools` 기준 **19개 클래스 / 44개 `@Tool` 메서드**다.

## Resources 제공 기준

`Resources`는 읽을 수 있는 컨텍스트다.

LLM이 참고해야 하지만 직접 실행 기능은 아닌 데이터는 Resource로 제공한다.

### 판단 기준

- 문서처럼 읽기 전용이다.
- DB 스키마 snapshot처럼 참고용 데이터다.
- 프로젝트 구조 분석 결과처럼 LLM의 판단에 필요한 컨텍스트다.
- URI로 식별해 읽을 수 있다.
- 실행이 아니라 조회/참조 목적이다.

### 이 프로젝트 Resource 후보

| 파일/데이터 | Resource로 제공하는 이유 |
| --- | --- |
| `docs/tool-catalog.md` | Tool 전체 설명서 |
| `prompts/system-prompt.md` | AI 기본 운영 규칙 |
| `prompts/tool-selection.md` | Tool 선택 기준 |
| `prompts/code-generation-rule.md` | 코드 생성 표준 |
| `docs/security/*` | Security 설계/구현 가이드 |
| `docs/crud/*` | CRUD 생성 가이드 |
| `docs/rag/*` | RAG 구조와 구현 설명 |
| DB 스키마 snapshot | 코드 생성 참고 컨텍스트 |
| 프로젝트 구조 분석 결과 | 기존 프로젝트 맞춤 생성 컨텍스트 |

### Resource URI 예시

```text
resource://docs/tool-catalog
resource://prompts/system-prompt
resource://prompts/tool-selection
resource://schema/com/COMTNEMPLYRINFO
resource://project/structure
```

### 현재 프로젝트 상태

현재 폴더에는 `docs`, `prompts`, `templates`가 존재한다.

`src/main/java/com/krdevops/springai/config/McpKnowledgeConfig.java`의 `mcpDocumentationResources()`가 `docs/**/*.md`, `prompts/**/*.md`를 MCP Resource로 등록한다.

따라서 현재 상태는 다음과 같다.

| 항목 | 상태 |
| --- | --- |
| Resource로 쓸 파일 구조 | 있음 |
| MCP Resource 등록 구현 | 있음 (`McpKnowledgeConfig.mcpDocumentationResources`) |

## Prompts 제공 기준

`Prompts`는 재사용 가능한 작업 지시 템플릿이다.

사용자가 특정 작업을 시작할 때 선택할 수 있는 프롬프트 양식은 Prompt로 제공한다.

### 판단 기준

- 반복적으로 사용하는 작업 지시문이다.
- 사용자 입력값을 받아 최종 프롬프트로 완성된다.
- Tool 선택, 컨텍스트, 출력 형식을 포함한다.
- slash command나 prompt picker처럼 사용자가 선택할 수 있다.

### 이 프로젝트 Prompt 후보

| Prompt | 용도 |
| --- | --- |
| `crud-generation` | 테이블 스키마 기반 CRUD 생성 요청 |
| `security-generation` | eGovFrame Security 설정 생성 |
| `menu-generation` | 메뉴/프로그램/권한 SQL 생성 |
| `code-review` | 생성 코드 검토 |
| `rag-answer` | RAG 컨텍스트 기반 답변 |

### templates 폴더 매핑

| 파일 | Prompt로 제공하는 이유 |
| --- | --- |
| `templates/prompt-template.md` | 공통 코드 생성 요청 양식 |
| `templates/crud-prompt-template.md` | CRUD 생성 요청 양식 |
| `templates/security-prompt-template.md` | Security 생성 요청 양식 |
| `templates/menu-prompt-template.md` | 메뉴 SQL 생성 요청 양식 |

### Prompt 입력값 예시

```json
{
  "userRequest": "COMTNEMPLYRINFO CRUD 만들어줘",
  "database": "com",
  "tableName": "COMTNEMPLYRINFO",
  "domain": "Employer",
  "packageName": "egovframework.let.emp",
  "egovVersion": "5.0",
  "outputPath": "/Users/user/Desktop/egov-generated/employer"
}
```

### 현재 프로젝트 상태

현재 `templates` 폴더에는 재사용 가능한 프롬프트 템플릿 파일이 있다.

`src/main/java/com/krdevops/springai/config/McpKnowledgeConfig.java`의 `mcpPromptTemplates()`가 아래 템플릿을 MCP Prompt로 등록한다.

| Prompt | Template |
| --- | --- |
| `code-generation` | `templates/prompt-template.md` |
| `crud-generation` | `templates/crud-prompt-template.md` |
| `security-generation` | `templates/security-prompt-template.md` |
| `menu-generation` | `templates/menu-prompt-template.md` |

따라서 현재 상태는 다음과 같다.

| 항목 | 상태 |
| --- | --- |
| Prompt로 쓸 템플릿 파일 구조 | 있음 |
| MCP Prompt 등록 구현 | 있음 (`McpKnowledgeConfig.mcpPromptTemplates`) |

## Tools, Resources, Prompts 차이

| 질문 | 예 | MCP 분류 |
| --- | --- | --- |
| 이것을 실행해야 하는가? | DB 조회, 파일 저장, SQL 생성 | Tool |
| 이것을 읽어서 참고해야 하는가? | 문서, 스키마, 가이드 | Resource |
| 이것을 작업 시작 템플릿으로 써야 하는가? | CRUD 생성 요청 양식 | Prompt |
| 인자가 들어가 최종 프롬프트가 만들어지는가? | `tableName`, `domain`, `schema` 치환 | Prompt |
| URI로 읽는 자료인가? | `resource://docs/tool-catalog` | Resource |

## 이 프로젝트 권장 매핑

### Tools

현재 `@Tool` 클래스로 제공되는 실행 기능이다.

```text
src/main/java/com/krdevops/springai/tools/
```

예:

- `SchemaReaderTool`
- `CrudPromptBuilderTool`
- `CodeSaverTool`
- `CodeValidatorTool`
- `ProjectInitializrTool`
- `SecurityTemplateTool`
- `MenuTool`
- `AuthTool`
- `RagTool`

### Resources

AI가 참고할 설명서와 운영 규칙이다.

```text
docs/
prompts/system-prompt.md
prompts/tool-selection.md
prompts/code-generation-rule.md
```

권장 Resource:

- `docs/tool-catalog.md`
- `docs/security/*`
- `docs/crud/*`
- `docs/rag/*`
- `prompts/system-prompt.md`
- `prompts/tool-selection.md`
- `prompts/code-generation-rule.md`

### Prompts

사용자가 특정 작업을 시작할 때 선택하는 재사용 프롬프트 양식이다.

```text
templates/
```

권장 Prompt:

- `templates/prompt-template.md`
- `templates/crud-prompt-template.md`
- `templates/security-prompt-template.md`
- `templates/menu-prompt-template.md`

## 최종 정리

현재 프로젝트는 운영 문서 구조 관점에서는 다음 구성이 갖춰져 있다.

```text
docs      = 설명서
prompts   = AI 판단 규칙
templates = 생성 양식
```

하지만 MCP 서버 capability 기준으로는 다음 상태다.

| Capability | 현재 상태 |
| --- | --- |
| Tools | 구현됨 |
| Resources | 구현됨 (`docs/**/*.md`, `prompts/**/*.md`) |
| Prompts | 구현됨 (`templates/*.md` 중 4개 템플릿) |

즉, 현재는 **MCP Tools + Resources + Prompts 제공 단계**다.

다음 단계로는 DB 스키마 snapshot, 프로젝트 구조 분석 결과, 추가 템플릿을 Resource/Prompt로 확장할 수 있다.
