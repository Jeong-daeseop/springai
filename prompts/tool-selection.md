# Tool Selection Rule

사용자 요청을 보고 어떤 MCP Tool을 먼저 호출할지 결정하는 규칙이다.

## 공통 우선순위

1. 사용자가 Tool 이름을 명시하면 해당 Tool을 우선 사용한다.
2. 여러 단계 작업이면 먼저 `WorkflowGuideTool`을 사용한다.
3. 생성 전에는 조회/분석 Tool을 먼저 사용한다.
4. 생성 후에는 검증/이력 Tool을 사용한다.
5. DB 변경 SQL은 생성까지만 하고 실행하지 않는다.

## 프로젝트 생성

다음 요청이면 `ProjectInitializrTool.initializeProject`를 사용한다.

- 프로젝트 생성
- 초기 구조 생성
- eGovFrame 프로젝트 만들어줘
- Spring Boot 기반 eGovFrame 프로젝트 생성
- WAR 방식 프로젝트 생성
- boot 프로젝트 생성

필수 확인값:

- `projectName`
- `groupId`
- `artifactId`
- `packageName`
- `buildTool`
- `projectType`
- `egovVersion`
- `outputPath`

보조 Tool:

- 설정 파일만 필요하면 `ProjectInitializrTool.getConfigTemplate`
- 생성 후 구조 확인은 `ProjectScannerTool.scanProjectStructure`

## 기존 프로젝트 분석

다음 요청이면 `ProjectScannerTool.scanProjectStructure`를 먼저 사용한다.

- 기존 프로젝트 구조 확인
- 이 프로젝트에 맞춰 생성
- 기존 패키지/URL/JSP 경로 확인
- 프로젝트 스캔

출력 경로가 필요하면 다음 Tool을 사용한다.

- 기존 프로젝트에 직접 추가: `OutputPathResolverTool.resolveProjectOutputPath`
- 별도 생성 경로 필요: `OutputPathResolverTool.getDefaultOutputPath`

## CRUD 생성

다음 요청이면 CRUD 생성 흐름을 사용한다.

- CRUD 생성
- 테이블 기준 소스 생성
- 목록/상세/등록/수정/삭제 화면 생성
- VO, Mapper, Service, Controller 생성
- eGovFrame 소스 생성

Tool 순서:

1. `WorkflowGuideTool.suggestNextStep("")`
2. `SchemaReaderTool.getTableSchema`
3. `SchemaReaderTool.getTableRelations`
4. `OutputPathResolverTool.getDefaultOutputPath` 또는 `resolveProjectOutputPath`
5. 관계 결과에 따라 선택
   - 단일 테이블: `CrudPromptBuilderTool.buildFullCrudPrompt`
   - 마스터-디테일: `CrudPromptBuilderTool.buildMasterDetailPrompt`
   - JOIN 보강: `CrudPromptBuilderTool.buildJoinSelectPrompt`
6. 필요 시 `CodeSaverTool.generateSource`
7. 필요 시 `CodeSaverTool.saveGeneratedCode`
8. `CodeValidatorTool.validateGeneratedCodeDirectory`
9. `GenerationHistoryTool.saveGenerationHistory`
10. `ProjectHealthTool.checkProjectHealth`

## 코드 템플릿 조회

다음 요청이면 `CodeTemplateTool.getCodeTemplate`을 사용한다.

- VO 템플릿 보여줘
- Controller 템플릿
- Mapper XML 템플릿
- eGovFrame 표준 템플릿 확인

서버 템플릿으로 실제 소스를 만들 때는 `CodeSaverTool.generateSource`를 우선 사용한다.

## 코드 저장

다음 요청이면 `CodeSaverTool.saveGeneratedCode`를 사용한다.

- 생성된 코드 저장
- 파일로 저장
- 이 경로에 만들어줘

저장 전 경로 확인이 필요하면 `CodeSaverTool.checkOutputDirectory`를 사용한다.

## 코드 검증

다음 요청이면 검증 Tool을 사용한다.

- 코드 검증
- eGovFrame 표준 맞는지 확인
- 생성 결과 검사
- 디렉터리 전체 검증

Tool 선택:

- 파일 하나: `CodeValidatorTool.validateGeneratedCode`
- 디렉터리 전체: `CodeValidatorTool.validateGeneratedCodeDirectory`
- 프로젝트 완성도: `ProjectHealthTool.checkProjectHealth`

## Security 생성

다음 요청이면 `SecurityTemplateTool.getSecurityTemplate`을 사용한다.

- 로그인 구현
- Spring Security 설정
- context-security.xml 생성
- security config 생성
- web.xml security filter 생성
- 로그인 페이지 생성
- 권한 설정 템플릿 생성

Tool 순서:

1. `WorkflowGuideTool.suggestSecurityMenuAuthWorkflow("")`
2. `SecurityTemplateTool.getSecurityTemplate`
3. URL 권한이 필요하면 `AuthTool.generateAuthInsertSql`

선택 기준:

- eGovFrame 4.3 XML Security: `setup-war-43-xml`
- eGovFrame 4.3 Java Config: `setup-war-43-java`
- eGovFrame 5.0 WAR Security: `setup-war-50`
- 5.0 전체 보안 구성: `setup-all-war-50`
- 필터만 생성: `setup-filters`
- 4.3 핸들러만 생성: `setup-handlers-43`

## 메뉴 생성

다음 요청이면 `MenuTool`을 사용한다.

- 메뉴 추가
- 메뉴 SQL 생성
- 상위 메뉴 아래 메뉴 생성
- COMTNMENUINFO 등록
- 프로그램 등록 SQL 생성

Tool 순서:

1. `MenuTool.getMenuStructure`
2. `AuthTool.getProgramList`
3. `MenuTool.generateMenuInsertSql`

필수 입력:

- `upperMenuNo`
- `urlPrefix`
- `menuNm`
- `progrmFileNm`

## URL 권한 생성

다음 요청이면 `AuthTool.generateAuthInsertSql`을 사용한다.

- URL 권한 추가
- ROLE SQL 생성
- COMTNROLEINFO 등록
- COMTNAUTHORROLERELATE 등록

사전 확인:

- 프로그램 중복 검색: `AuthTool.getProgramList`
- Security DB 권한 조회 설정 여부 확인

## DB 조회

다음 요청이면 `SchemaReaderTool` 또는 `SqlTool`을 사용한다.

- 테이블 목록: `SchemaReaderTool.getTableList`
- 컬럼 상세: `SchemaReaderTool.getTableSchema`
- 테이블 관계: `SchemaReaderTool.getTableRelations`
- 샘플 데이터: `SqlTool.getSampleData`
- SELECT 실행: `SqlTool.executeQuery`
- 실행 계획: `SqlTool.explainQuery`

금지:

- `SqlTool.executeQuery`로 `INSERT`, `UPDATE`, `DELETE`, `DDL`을 실행하지 않는다.

## RAG 문서 등록/검색

다음 요청이면 `RagTool`을 사용한다.

- 텍스트 문서 등록: `RagTool.ragIngest`
- Java 소스 디렉터리 등록: `RagTool.ragIngestDirectory`
- 단일 URL 등록: `RagTool.ragIngestUrl`
- 여러 URL 등록: `RagTool.ragIngestUrls`
- RAG 검색: `RagTool.ragSearch`

금지:

- 내부망, localhost, 파일 URL, 민감정보 URL은 무검토 등록하지 않는다.

## 공통코드 조회

다음 요청이면 `CommonCodeTool`을 사용한다.

- 코드ID를 아는 경우: `CommonCodeTool.getCommonCode`
- 코드ID를 모르는 경우: `CommonCodeTool.searchCommonCode`

## 직원 CRUD

다음 요청이면 `EmployeeTool`을 사용한다.

- 직원 목록: `EmployeeTool.getEmployeeList`
- 직원 상세: `EmployeeTool.getEmployee`
- 직원 등록: `EmployeeTool.createEmployee`
- 직원 수정: `EmployeeTool.updateEmployee`
- 직원 삭제: `EmployeeTool.deleteEmployee`

삭제는 사용자의 명시적 삭제 요청이 있을 때만 수행한다.

## 생성 이력

다음 요청이면 `GenerationHistoryTool`을 사용한다.

- 생성 이력 조회: `GenerationHistoryTool.getGenerationHistory`
- 생성 이력 저장: `GenerationHistoryTool.saveGenerationHistory`

CRUD 생성 완료 후에는 이력 저장을 권장한다.

## 시간/온도

다음 요청이면 `DateTimeTool`을 사용한다.

- 현재 시간: `DateTimeTool.getCurrentDateTime`
- 섭씨/화씨 변환: `DateTimeTool.celsiusToFahrenheit`
