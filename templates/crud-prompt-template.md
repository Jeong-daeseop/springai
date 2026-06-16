# CRUD Generation Request

## User Request

{{userRequest}}

## Selected Tool Flow

1. `WorkflowGuideTool.suggestNextStep("")`
2. `SchemaReaderTool.getTableSchema("{{database}}", "{{tableName}}")`
3. `SchemaReaderTool.getTableRelations("{{database}}", "{{tableName}}")`
4. `OutputPathResolverTool.{{outputPathResolverMethod}}`
5. `CrudPromptBuilderTool.{{crudPromptMethod}}`
6. `CodeValidatorTool.validateGeneratedCodeDirectory("{{outputPath}}")`
7. `GenerationHistoryTool.saveGenerationHistory(...)`
8. `ProjectHealthTool.checkProjectHealth("{{projectRootPath}}", "{{domainLc}}")`

## Tool Inputs

| Key | Value |
| --- | --- |
| Database | `{{database}}` |
| Table Name | `{{tableName}}` |
| Domain | `{{domain}}` |
| Domain Lowercase | `{{domainLc}}` |
| Domain Korean Name | `{{domainKr}}` |
| Package Name | `{{packageName}}` |
| URL Prefix | `{{urlPrefix}}` |
| Output Path | `{{outputPath}}` |
| eGovFrame Version | `{{egovVersion}}` |
| LLM Provider | `{{llmProvider}}` |

## Table Schema

{{tableSchema}}

## Table Relations

{{tableRelations}}

## Common Code Context

{{commonCodeContext}}

## Existing Project Context

{{projectContext}}

## Generation Mode

{{generationMode}}

- `auto`: Tool 내부에서 템플릿 기반 11개 파일을 생성/저장한다.
- `claude`: 프롬프트와 템플릿을 기반으로 LLM이 파일별 코드를 생성하고 `saveGeneratedCode()`로 저장한다.

## CRUD Generation Rules

- VO는 테이블 컬럼을 Java 필드로 변환한다.
- PK 필드는 상세/수정/삭제 기준 필드로 사용한다.
- Controller는 목록, 상세, 등록 화면, 등록 처리, 수정 화면, 수정 처리, 삭제 처리를 포함한다.
- Service는 interface로 선언하고, ServiceImpl은 구현체로 작성한다.
- Mapper interface와 Mapper XML id는 동일하게 유지한다.
- Mapper XML은 목록, 건수, 단건, 등록, 수정, 삭제 SQL을 포함한다.
- 목록 조회에는 pagination 파라미터를 반영한다.
- 검색 조건은 `searchCondition`, `searchKeyword`, `pageIndex` 기준으로 처리한다.
- JSP를 생성하는 경우 목록/상세/등록/수정 4개 화면을 생성한다.
- Thymeleaf를 생성하는 경우 list/detail/form/update 템플릿을 분리한다.
- eGovFrame 5.0은 Jakarta validation import를 사용한다.
- eGovFrame 4.3은 Javax validation import를 사용한다.

## Required Output Files

| Layer | File |
| --- | --- |
| VO | `{{domain}}VO.java` |
| Mapper Interface | `{{domain}}Mapper.java` |
| Mapper XML | `{{domain}}Mapper.xml` |
| Service | `{{domain}}Service.java` |
| ServiceImpl | `{{domain}}ServiceImpl.java` |
| Controller | `Egov{{domain}}Controller.java` |
| Validation Handler | `Egov{{domain}}ValidationHandler.java` |
| List View | `Egov{{domain}}List.jsp` |
| Detail View | `Egov{{domain}}Detail.jsp` |
| Register View | `Egov{{domain}}Regist.jsp` |
| Update View | `Egov{{domain}}Updt.jsp` |

## Output Format

각 파일은 다음 형식으로 출력한다.

```text
// FILE: {{relativePath}}
{{fileContent}}
```

## Post Generation

생성 후 반드시 다음 작업을 수행한다.

1. `CodeValidatorTool.validateGeneratedCodeDirectory("{{outputPath}}")`
2. 오류가 있으면 파일별로 수정한다.
3. `GenerationHistoryTool.saveGenerationHistory(...)`
4. 필요 시 `MenuTool.generateMenuInsertSql(...)` 및 `AuthTool.generateAuthInsertSql(...)`로 등록 SQL을 생성한다.

## Stop Conditions

- `tableSchema`가 비어 있으면 생성하지 않는다.
- `outputPath`가 확정되지 않았으면 저장하지 않는다.
- `packageName`이 불명확하면 생성하지 않는다.
- eGovFrame 버전이 불명확하면 사용자에게 확인한다.
