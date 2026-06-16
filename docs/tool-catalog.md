# MCP Tool Catalog

작성 기준: `../src/main/java/com/krdevops/springai/tools` 패키지의 `@Tool` 메서드

## 공통 원칙

- Tool 클래스는 얇은 래퍼이며 실제 로직은 `service/` 패키지에 위임한다.
- 신규 eGovFrame CRUD 생성은 `WorkflowGuideTool.suggestNextStep("")`로 순서를 확인한 뒤 진행한다.
- 파일 저장 경로는 사용자가 명시한 경로, `resolveProjectOutputPath()`, `getDefaultOutputPath()` 순서로 확정한다.
- DB 변경 SQL 생성 도구는 SQL 문자열만 반환한다. 직접 DB에 `INSERT`, `UPDATE`, `DELETE`를 실행하지 않는다.
- 임의 SQL 실행은 `SqlTool.executeQuery()` 기준 `SELECT`, `SHOW`, `EXPLAIN`, `DESC`만 허용된다.
- 프로젝트 생성은 `ProjectInitializrTool.initializeProject()`를 사용한다. Bash나 수동 파일 생성으로 대체하지 않는다.

## 에러 처리 기준

| 구분 | 기준 |
| --- | --- |
| 입력값 누락/형식 오류 | Tool 또는 Service가 `"오류: ..."`, `"파싱 실패: ..."`, `"지원하지 않는 ..."` 형식 문자열을 반환한다. |
| 조회 결과 없음 | `"조회된 ... 없습니다."`, `"찾을 수 없습니다."`, `"검색 결과가 없습니다."` 형식으로 반환한다. |
| 파일/디렉터리 오류 | `"파일 저장 실패"`, `"파일 읽기 실패"`, `"디렉터리 스캔 실패"`, `"프로젝트 디렉터리 읽기 실패"` 형식으로 반환한다. |
| SQL 오류 | `"SQL 실행 실패: ..."`, `"EXPLAIN 실패: ..."` 형식으로 반환한다. |
| RAG URL 오류 | 잘못된 URL, 허용되지 않는 URL 스킴, 내부 네트워크 접근, 알 수 없는 호스트, HTTP 오류를 실패 문자열로 반환한다. |
| JSON 오류 | `generateSource()`는 `valuesJson` 파싱 실패 시 예시 JSON과 함께 오류 문자열을 반환한다. |
| Java 예외 전파 가능 | `DateTimeTool.getCurrentDateTime()`의 잘못된 IANA timezone처럼 Tool 내부에서 catch하지 않는 경우 런타임 예외가 호출자에게 전파될 수 있다. |

## Tool 전체 목록

| Tool | Method | 목적 |
| --- | --- | --- |
| `AuthTool` | `getProgramList(keyword)` | COMTNPROGRMLIST 프로그램 중복/목록 검색 |
| `AuthTool` | `generateAuthInsertSql(urlPrefix, programNm, domain)` | URL 권한 등록 SQL 생성 |
| `CodeSaverTool` | `saveGeneratedCode(filePath, code)` | 생성 소스 파일 저장 |
| `CodeSaverTool` | `checkOutputDirectory(baseDir)` | 출력 디렉터리 상태 확인 |
| `CodeSaverTool` | `generateSource(layer, valuesJson)` | 서버 템플릿 기반 소스 생성 |
| `CodeTemplateTool` | `getCodeTemplate(layer)` | eGovFrame 레이어별 템플릿 반환 |
| `CodeValidatorTool` | `validateGeneratedCode(filePath)` | 생성 파일 단건 검증 |
| `CodeValidatorTool` | `validateGeneratedCodeDirectory(directoryPath)` | 생성 디렉터리 일괄 검증 |
| `CommonCodeTool` | `getCommonCode(codeId)` | 공통코드 상세 목록 조회 |
| `CommonCodeTool` | `searchCommonCode(keyword)` | 공통코드 그룹 검색 |
| `CrudPromptBuilderTool` | `buildFullCrudPrompt(...)` | 전체 CRUD 생성 프롬프트 또는 auto 생성 |
| `CrudPromptBuilderTool` | `buildMasterDetailPrompt(...)` | 1:N 마스터-디테일 CRUD 생성 지시 |
| `CrudPromptBuilderTool` | `buildJoinSelectPrompt(database, tableName)` | JOIN SELECT/resultMap/VO 필드 초안 생성 |
| `DateTimeTool` | `getCurrentDateTime(timezone)` | 지정 시간대 현재 시각 반환 |
| `DateTimeTool` | `celsiusToFahrenheit(celsius)` | 섭씨를 화씨로 변환 |
| `EmployeeTool` | `getEmployeeList(keyword)` | 직원 목록 조회 |
| `EmployeeTool` | `getEmployee(emplyrId)` | 직원 단건 조회 |
| `EmployeeTool` | `createEmployee(...)` | 직원 등록 |
| `EmployeeTool` | `updateEmployee(...)` | 직원 수정 |
| `EmployeeTool` | `deleteEmployee(emplyrId)` | 직원 삭제 |
| `GenerationHistoryTool` | `saveGenerationHistory(...)` | CRUD 생성 이력 저장 |
| `GenerationHistoryTool` | `getGenerationHistory(keyword)` | CRUD 생성 이력 조회 |
| `MenuTool` | `getMenuStructure(menuNo)` | COMTNMENUINFO 메뉴 트리 조회 |
| `MenuTool` | `generateMenuInsertSql(...)` | 메뉴/프로그램 등록 SQL 생성 |
| `OutputPathResolverTool` | `getDefaultOutputPath(domain)` | 기본 출력 경로 결정 |
| `OutputPathResolverTool` | `resolveProjectOutputPath(...)` | 기존 프로젝트 레이어별 출력 경로 분석 |
| `ProjectHealthTool` | `checkProjectHealth(projectRootPath, domain)` | 도메인 생성 완성도 점검 |
| `ProjectInitializrTool` | `initializeProject(...)` | eGovFrame 신규 프로젝트 생성 |
| `ProjectInitializrTool` | `getConfigTemplate(configType, packageName)` | 설정 파일 템플릿 반환 |
| `ProjectScannerTool` | `scanProjectStructure(projectRootPath)` | 기존 프로젝트 구조 스캔 |
| `RagTool` | `ragIngest(docId, content, type)` | 텍스트 문서 임베딩 등록 |
| `RagTool` | `ragIngestDirectory(directoryPath)` | Java 소스 디렉터리 임베딩 |
| `RagTool` | `ragIngestUrl(url, docId)` | 단일 URL 문서 임베딩 |
| `RagTool` | `ragIngestUrls(urls)` | 다중 URL 문서 임베딩 |
| `RagTool` | `ragSearch(query, topK)` | Vector Store 유사 문서 검색 |
| `SchemaReaderTool` | `getTableList(database)` | DB 테이블 목록 조회 |
| `SchemaReaderTool` | `getTableSchema(database, tableName)` | 테이블 컬럼 상세 조회 |
| `SchemaReaderTool` | `getTableRelations(database, tableName)` | FK/암묵 JOIN/공통코드 관계 분석 |
| `SecurityTemplateTool` | `getSecurityTemplate(...)` | Security 템플릿 또는 조합 파일 생성 |
| `SqlTool` | `executeQuery(sql)` | 읽기 전용 SQL 실행 |
| `SqlTool` | `getSampleData(database, tableName, limit)` | 샘플 데이터 조회 |
| `SqlTool` | `explainQuery(sql)` | 실행 계획 분석 |
| `WorkflowGuideTool` | `suggestNextStep(currentContext)` | CRUD 생성 워크플로우 안내 |
| `WorkflowGuideTool` | `suggestSecurityMenuAuthWorkflow(currentContext)` | Security/Menu/Auth 워크플로우 안내 |

## Tool 상세

### AuthTool

#### `getProgramList(keyword)`

- 사용 조건: 메뉴 등록 전 `COMTNPROGRMLIST`의 기존 프로그램명, URL, `PROGRM_FILE_NM` 중복을 확인할 때 사용한다.
- 사용 금지 조건: 실제 권한 SQL 생성이 목적이면 이 도구만으로 끝내지 말고 `generateAuthInsertSql()` 또는 `MenuTool.generateMenuInsertSql()`로 이어간다.
- 입력 예시:

```json
{"keyword":"EgovEmployer"}
```

- 출력 예시:

```text
프로그램 목록 (1건):
- EgovEmployerList | 직원관리 | /emp/employer/selectEmployerList.do
```

- 에러 처리 기준: 검색 결과가 없으면 `"검색 결과가 없습니다."`를 반환한다.

#### `generateAuthInsertSql(urlPrefix, programNm, domain)`

- 사용 조건: 신규 도메인 URL 접근 제어를 `COMTNROLEINFO`, `COMTNAUTHORROLERELATE`에 등록할 SQL이 필요할 때 사용한다.
- 사용 금지 조건: `securityMapper` 기반 DB URL 권한 설정이 준비되지 않았거나 SQL을 즉시 실행해야 하는 용도로 사용하지 않는다. 이 도구는 SQL만 반환한다.
- 입력 예시:

```json
{"urlPrefix":"/emp/employer","programNm":"직원관리","domain":"emp"}
```

- 출력 예시:

```sql
INSERT INTO COMTNROLEINFO (...);
INSERT INTO COMTNAUTHORROLERELATE (...);
```

- 에러 처리 기준: 입력 검증 실패는 `"오류: ..."` 형식으로 반환한다. ROLE_CODE 자동 계산은 동시 실행 시 race condition 가능성이 있다.

### CodeSaverTool

#### `saveGeneratedCode(filePath, code)`

- 사용 조건: Claude 또는 서버 템플릿이 생성한 VO, Mapper, Service, Controller, XML, JSP 파일을 저장할 때 사용한다.
- 사용 금지 조건: 허용 범위 밖 경로 저장, 검토되지 않은 SQL/바이너리/임의 실행 파일 저장에는 사용하지 않는다.
- 입력 예시:

```json
{"filePath":"/Users/user/Desktop/egov-gen/EmployerVO.java","code":"package egovframework.let.emp.service;\n..."}
```

- 출력 예시:

```text
파일 저장 완료: /Users/user/Desktop/egov-gen/EmployerVO.java (1234 chars)
```

- 에러 처리 기준: 허용 범위 밖 경로는 `"파일 저장 실패: 허용 범위 밖 경로입니다."`, IO 오류는 `"파일 저장 실패: ..."`를 반환한다.

#### `checkOutputDirectory(baseDir)`

- 사용 조건: 생성 파일을 저장하기 전에 디렉터리 존재 여부와 기존 파일 목록을 확인할 때 사용한다.
- 사용 금지 조건: 파일 생성 자체가 목적이면 `saveGeneratedCode()`를 사용한다.
- 입력 예시:

```json
{"baseDir":"/Users/user/Desktop/egov-gen/emp"}
```

- 출력 예시:

```text
디렉터리 존재: /Users/user/Desktop/egov-gen/emp
기존 파일: EmployerVO.java, EmployerMapper.java
```

- 에러 처리 기준: 디렉터리 접근 실패 시 `"디렉터리 확인 실패: ..."`를 반환한다.

#### `generateSource(layer, valuesJson)`

- 사용 조건: `buildFullCrudPrompt()`가 제공한 플레이스홀더 값을 JSON으로 넘겨 서버 템플릿 기반 소스를 결정적으로 생성할 때 사용한다.
- 사용 금지 조건: 플레이스홀더 값을 임의 추론하거나, `valuesJson`이 준비되지 않은 상태에서 호출하지 않는다.
- 입력 예시:

```json
{
  "layer":"vo",
  "valuesJson":"{\"PACKAGE\":\"egovframework.let.emp\",\"DOMAIN\":\"Employer\",\"DOMAIN_LC\":\"employer\",\"TABLE_NAME\":\"COMTNEMPLYRINFO\"}"
}
```

- 출력 예시:

```java
package egovframework.let.emp.service;

public class EmployerVO {
    ...
}
```

- 에러 처리 기준: JSON 파싱 실패 시 `"valuesJson 파싱 실패: ..."`와 올바른 JSON 예시를 반환한다. 지원하지 않는 layer는 서비스 또는 템플릿에서 오류 문자열을 반환한다.

### CodeTemplateTool

#### `getCodeTemplate(layer)`

- 사용 조건: eGovFrame 5.x 레이어별 표준 템플릿 원문이 필요할 때 사용한다.
- 사용 금지 조건: 템플릿에 없는 메서드, 주석, import를 임의 추가하는 용도로 사용하지 않는다. 일반적으로는 `generateSource()`를 우선 사용한다.
- 입력 예시:

```json
{"layer":"serviceImpl"}
```

- 출력 예시:

```java
package {{PACKAGE}}.service.impl;

public class {{DOMAIN}}ServiceImpl {
    ...
}
```

- 에러 처리 기준: 지원하지 않는 layer는 `"지원하지 않는 레이어입니다. 사용 가능: ..."`를 반환한다.

### CodeValidatorTool

#### `validateGeneratedCode(filePath)`

- 사용 조건: 생성된 단건 파일이 eGovFrame 5.x 표준을 만족하는지 확인할 때 사용한다.
- 사용 금지 조건: 아직 저장되지 않은 코드 문자열 검증에는 사용하지 않는다. 파일 경로가 필요하다.
- 입력 예시:

```json
{"filePath":"/Users/user/Desktop/egov-gen/EmployerController.java"}
```

- 출력 예시:

```text
✅ @Controller 선언
✅ @RequestMapping 선언
✅ PaginationInfo 페이징 처리
```

- 에러 처리 기준: 파일 접근 실패는 `"파일 읽기 실패: ..."`를 반환한다. 미준수 항목은 실패가 아니라 검증 결과로 `❌` 표시한다.

#### `validateGeneratedCodeDirectory(directoryPath)`

- 사용 조건: `.java`, `.xml`, `.jsp` 생성 결과를 하위 디렉터리까지 일괄 검증할 때 사용한다.
- 사용 금지 조건: 특정 파일 하나만 검증할 경우 `validateGeneratedCode()`를 사용한다.
- 입력 예시:

```json
{"directoryPath":"/Users/user/Desktop/egov-gen/emp"}
```

- 출력 예시:

```text
검증 대상: 11개 파일
전체 파일 eGovFrame 표준 검증 통과
```

- 에러 처리 기준: 디렉터리 접근 실패는 `"디렉터리 스캔 실패: ..."`를 반환한다.

### CommonCodeTool

#### `getCommonCode(codeId)`

- 사용 조건: JSP `<select>` 옵션, VO 필드 매핑, 검색 조건 코드값 확인이 필요할 때 사용한다.
- 사용 금지 조건: 코드ID를 모르는 상태에서는 먼저 `searchCommonCode()`를 사용한다.
- 입력 예시:

```json
{"codeId":"COM034"}
```

- 출력 예시:

```text
COM034 공통코드 상세:
- A: 사용 | USE_AT=Y
<option value="A">사용</option>
```

- 에러 처리 기준: 코드가 없으면 `"코드ID '...' 에 해당하는 공통 코드가 없습니다."`를 반환한다.

#### `searchCommonCode(keyword)`

- 사용 조건: 코드ID, 코드명, 설명 기준으로 공통코드 그룹을 찾을 때 사용한다.
- 사용 금지 조건: 특정 코드ID의 상세값 조회에는 `getCommonCode()`를 사용한다.
- 입력 예시:

```json
{"keyword":"직원"}
```

- 출력 예시:

```text
공통 코드 그룹:
- COM034 | 직원상태 | 직원 상태 코드
```

- 에러 처리 기준: 결과가 없으면 `"'keyword' 에 해당하는 공통 코드 그룹이 없습니다."`를 반환한다.

### CrudPromptBuilderTool

#### `buildFullCrudPrompt(database, tableName, domain, packageName, outputPath, llmProvider, egovVersion)`

- 사용 조건: 단일 테이블 CRUD 전체 소스 생성을 시작할 때 사용한다.
- 사용 금지 조건: `outputPath`를 추측해서 호출하지 않는다. 기존 프로젝트 경로가 있으면 `resolveProjectOutputPath()`를 먼저 호출한다.
- 입력 예시:

```json
{
  "database":"com",
  "tableName":"COMTNEMPLYRINFO",
  "domain":"Employer",
  "packageName":"egovframework.let.emp",
  "outputPath":"/Users/user/Desktop/egov-gen/emp",
  "llmProvider":"auto",
  "egovVersion":"5.0"
}
```

- 출력 예시:

```text
=== [auto] eGovFrame 5.x CRUD 소스 생성 완료 ===
DB: com | 테이블: COMTNEMPLYRINFO | 도메인: Employer
출력 경로: /Users/user/Desktop/egov-gen/emp
```

- 에러 처리 기준: 테이블이 없으면 `"테이블을 찾을 수 없습니다: database.tableName"`을 반환한다. `llmProvider`가 `auto`면 내부에서 생성/저장/검증/이력 저장을 수행하고, 그 외에는 프롬프트를 반환한다.

#### `buildMasterDetailPrompt(database, masterTable, detailTable, domain, packageName, outputPath)`

- 사용 조건: `getTableRelations()`에서 자식 테이블이 탐지된 1:N 구조일 때 사용한다.
- 사용 금지 조건: 단순 단일 테이블 CRUD에는 `buildFullCrudPrompt()`를 사용한다.
- 입력 예시:

```json
{
  "database":"com",
  "masterTable":"COMTNEMPLYRINFO",
  "detailTable":"COMTNEMPLYRATTRBINFO",
  "domain":"Employer",
  "packageName":"egovframework.let.emp",
  "outputPath":"/Users/user/Desktop/egov-gen/emp"
}
```

- 출력 예시:

```text
마스터-디테일 CRUD 생성 지시:
- 마스터 VO/Mapper/Service/Controller
- 디테일 VO/Mapper
- JSP 5개
```

- 에러 처리 기준: 마스터 또는 디테일 테이블이 없으면 `"마스터 테이블을 찾을 수 없습니다: ..."` 또는 `"디테일 테이블을 찾을 수 없습니다: ..."`를 반환한다.

#### `buildJoinSelectPrompt(database, tableName)`

- 사용 조건: 공통코드, 부서 등 JOIN 후보 컬럼이 있는 단일 테이블 SELECT 개선이 필요할 때 사용한다.
- 사용 금지 조건: JOIN 후보가 없는 단순 구조에는 사용하지 않는다.
- 입력 예시:

```json
{"database":"com","tableName":"COMTNEMPLYRINFO"}
```

- 출력 예시:

```text
JOIN SELECT 쿼리 초안
resultMap 추가 항목
VO 추가 필드 목록
```

- 에러 처리 기준: 테이블이 없으면 `"테이블을 찾을 수 없습니다: ..."`를 반환한다. JOIN 후보가 없으면 `buildFullCrudPrompt()` 사용 권장 메시지를 반환한다.

### DateTimeTool

#### `getCurrentDateTime(timezone)`

- 사용 조건: IANA 시간대 기준 현재 날짜/시간이 필요할 때 사용한다.
- 사용 금지 조건: `KST`, `서울시간` 같은 비표준 문자열을 그대로 넘기지 않는다. `Asia/Seoul`처럼 변환해서 호출한다.
- 입력 예시:

```json
{"timezone":"Asia/Seoul"}
```

- 출력 예시:

```text
2026-06-11 14:30:00 KST
```

- 에러 처리 기준: 잘못된 timezone은 `ZoneId.of()` 예외가 발생할 수 있다.

#### `celsiusToFahrenheit(celsius)`

- 사용 조건: 섭씨 온도를 화씨로 변환할 때 사용한다.
- 사용 금지 조건: 온도 변환 외 일반 수식 계산에는 사용하지 않는다.
- 입력 예시:

```json
{"celsius":30.0}
```

- 출력 예시:

```text
30.0°C = 86.0°F
```

- 에러 처리 기준: 숫자가 아닌 입력은 호출 계층의 파라미터 바인딩 오류로 처리된다.

### EmployeeTool

#### `getEmployeeList(keyword)`

- 사용 조건: 직원 이름, 이메일, 직위로 최대 20건 조회할 때 사용한다.
- 사용 금지 조건: 전체 대량 덤프 목적에는 사용하지 않는다.
- 입력 예시:

```json
{"keyword":"홍길동"}
```

- 출력 예시:

```text
직원 목록 (1건):
- [USER001] 홍길동 | 직위: 과장 | 이메일: hong@example.com | 휴대폰: 010-0000-0000
```

- 에러 처리 기준: 결과가 없으면 `"조회된 직원이 없습니다."`를 반환한다.

#### `getEmployee(emplyrId)`

- 사용 조건: 직원 ID로 단건 상세를 조회할 때 사용한다.
- 사용 금지 조건: 키워드 검색 목적에는 `getEmployeeList()`를 사용한다.
- 입력 예시:

```json
{"emplyrId":"USER001"}
```

- 출력 예시:

```text
직원 상세:
 ID: USER001
 이름: 홍길동
 직위: 과장
 이메일: hong@example.com
 휴대폰: 010-0000-0000
 상태: P
```

- 에러 처리 기준: 없으면 `"해당 직원을 찾을 수 없습니다: USER001"`을 반환한다.

#### `createEmployee(emplyrId, userNm, emailAdres, ofcpsNm, mbtlnum, esntlId)`

- 사용 조건: eGovFrame 직원 데이터를 신규 등록할 때 사용한다.
- 사용 금지 조건: `esntlId` 등 필수 식별자가 준비되지 않았거나 중복 ID 가능성이 있는 경우 사전 조회 없이 호출하지 않는다.
- 입력 예시:

```json
{
  "emplyrId":"USER001",
  "userNm":"홍길동",
  "emailAdres":"hong@example.com",
  "ofcpsNm":"과장",
  "mbtlnum":"010-0000-0000",
  "esntlId":"ESNTL000000000000001"
}
```

- 출력 예시:

```text
직원 등록 완료: USER001 (홍길동)
```

- 에러 처리 기준: 영향 행 수가 0이면 `"직원 등록 실패"`를 반환한다. DB 제약 오류는 서비스/호출 계층 예외로 처리될 수 있다.

#### `updateEmployee(emplyrId, userNm, emailAdres, ofcpsNm, mbtlnum)`

- 사용 조건: 기존 직원의 일부 필드를 수정할 때 사용한다. 수정하지 않을 항목은 빈 문자열로 전달한다.
- 사용 금지 조건: 직원 ID 없이 호출하지 않는다.
- 입력 예시:

```json
{"emplyrId":"USER001","userNm":"","emailAdres":"new@example.com","ofcpsNm":"","mbtlnum":""}
```

- 출력 예시:

```text
직원 수정 완료: USER001
```

- 에러 처리 기준: 대상 ID가 없으면 `"직원 수정 실패 (ID 없음)"`을 반환한다.

#### `deleteEmployee(emplyrId)`

- 사용 조건: 직원 ID로 삭제할 때 사용한다.
- 사용 금지 조건: 사용자의 명시적 삭제 의사 없이 호출하지 않는다.
- 입력 예시:

```json
{"emplyrId":"USER001"}
```

- 출력 예시:

```text
직원 삭제 완료: USER001
```

- 에러 처리 기준: 대상 ID가 없으면 `"직원 삭제 실패 (ID 없음)"`을 반환한다.

### GenerationHistoryTool

#### `saveGenerationHistory(tableName, domain, packageName, outputPath, generatedFiles)`

- 사용 조건: CRUD 소스 생성 완료 후 반드시 호출해 이력을 DB와 RAG Vector Store에 등록한다.
- 사용 금지 조건: 실제 파일 생성이 완료되지 않았거나 파일 목록이 불명확할 때 호출하지 않는다.
- 입력 예시:

```json
{
  "tableName":"COMTNEMPLYRINFO",
  "domain":"Employer",
  "packageName":"egovframework.let.emp",
  "outputPath":"/Users/user/Desktop/egov-gen/emp",
  "generatedFiles":"EmployerVO.java, EmployerMapper.java, EgovEmployerController.java"
}
```

- 출력 예시:

```text
생성 이력 저장 완료: COMTNEMPLYRINFO / Employer
```

- 에러 처리 기준: 저장 실패는 서비스 오류 메시지로 반환된다.

#### `getGenerationHistory(keyword)`

- 사용 조건: 기존 생성 이력, 유사 패턴, 재생성 기준을 확인할 때 사용한다.
- 사용 금지 조건: 소스 코드 본문 검색에는 RAG 검색 또는 프로젝트 스캔을 사용한다.
- 입력 예시:

```json
{"keyword":"Employer"}
```

- 출력 예시:

```text
최근 생성 이력:
- COMTNEMPLYRINFO | Employer | egovframework.let.emp | /Users/user/Desktop/egov-gen/emp
```

- 에러 처리 기준: 검색 결과 없음은 서비스 메시지로 반환된다.

### MenuTool

#### `getMenuStructure(menuNo)`

- 사용 조건: 메뉴 등록 위치와 신규 `MENU_NO`, `MENU_ORDR` 권장값을 확인할 때 사용한다.
- 사용 금지 조건: `menuNo`가 숫자 문자열이 아니면 호출하지 않는다.
- 입력 예시:

```json
{"menuNo":"6000000"}
```

- 출력 예시:

```text
=== 메뉴 구조 (MENU_NO: 6000000) ===
[6000000] 시스템관리
  ├── [6010000] 공통분류코드
...
【권장값】
신규 MENU_NO: 6320000
신규 MENU_ORDR: 32
```

- 에러 처리 기준: 잘못된 입력은 `"오류: ..."`, 없는 메뉴는 `"메뉴 번호 ... 를 찾을 수 없습니다."`를 반환한다.

#### `generateMenuInsertSql(upperMenuNo, urlPrefix, menuNm, progrmFileNm)`

- 사용 조건: `COMTNPROGRMLIST`와 `COMTNMENUINFO` 등록 SQL을 생성할 때 사용한다.
- 사용 금지 조건: 상위 메뉴 확인, 프로그램 파일명 중복 확인 없이 사용하지 않는다. SQL을 직접 실행하지 않는다.
- 입력 예시:

```json
{
  "upperMenuNo":"6000000",
  "urlPrefix":"/emp/employer",
  "menuNm":"직원관리",
  "progrmFileNm":"EgovEmployerList"
}
```

- 출력 예시:

```sql
INSERT INTO COMTNPROGRMLIST (...);
INSERT INTO COMTNMENUINFO (...);
```

- 에러 처리 기준: 상위 메뉴 없음, 프로그램 파일명 중복, URL 중복 등은 `"오류: ..."` 형식으로 반환한다.

### OutputPathResolverTool

#### `getDefaultOutputPath(domain)`

- 사용 조건: 사용자가 저장 경로를 지정하지 않았을 때 기본 경로를 확정한다.
- 사용 금지 조건: 기존 프로젝트에 직접 넣어야 하는 경우에는 `resolveProjectOutputPath()`를 먼저 사용한다.
- 입력 예시:

```json
{"domain":"Employer"}
```

- 출력 예시:

```text
/Users/user/Desktop/egov-generated/employer
```

- 에러 처리 기준: 서비스가 환경변수 `EGOV_OUTPUT_PATH` 또는 기본 경로 계산 결과를 반환한다.

#### `resolveProjectOutputPath(projectRootPath, packageName, domain)`

- 사용 조건: 기존 eGovFrame 프로젝트에 소스를 직접 추가할 때 레이어별 실제 저장 위치를 찾는다.
- 사용 금지 조건: 프로젝트 루트가 아니거나 읽을 수 없는 경로를 넘기지 않는다.
- 입력 예시:

```json
{
  "projectRootPath":"/Users/user/workspace/my-egov-project",
  "packageName":"egovframework.let.emp",
  "domain":"Employer"
}
```

- 출력 예시:

```text
VO: src/main/java/egovframework/let/emp/service
Controller: src/main/java/egovframework/let/emp/web
JSP: src/main/webapp/WEB-INF/jsp/employer
권장 outputPath: ...
```

- 에러 처리 기준: 스캔 불가 시 `"프로젝트 경로를 스캔할 수 없습니다: ..."`를 반환한다.

### ProjectHealthTool

#### `checkProjectHealth(projectRootPath, domain)`

- 사용 조건: CRUD 생성 완료 후 파일 존재, 표준 준수, 메뉴/권한 등록, 테스트 존재 여부를 점검한다.
- 사용 금지 조건: 생성 전 경로 결정 목적으로 사용하지 않는다.
- 입력 예시:

```json
{"projectRootPath":"/Users/user/Desktop/egov-gen/emp","domain":"employer"}
```

- 출력 예시:

```text
완성도: 82%
✅ VO 존재
✅ Mapper XML 검증 통과
❌ 메뉴 등록 없음
다음 권장 작업: generateMenuInsertSql()
```

- 에러 처리 기준: 프로젝트 디렉터리 접근 실패 시 `"프로젝트 디렉터리 읽기 실패: ..."`를 반환한다.

### ProjectInitializrTool

#### `initializeProject(projectName, groupId, artifactId, packageName, buildTool, projectType, egovVersion, outputPath)`

- 사용 조건: 신규 eGovFrame 프로젝트 골격을 생성할 때 반드시 사용한다.
- 사용 금지 조건: `projectType` 또는 `egovVersion`이 불명확한 상태에서 추측 호출하지 않는다. Bash, Desktop Commander, 수동 파일 생성으로 대체하지 않는다.
- 입력 예시:

```json
{
  "projectName":"egov-myproject",
  "groupId":"kr.go.myorg",
  "artifactId":"myproject",
  "packageName":"egovframework.let.myproject",
  "buildTool":"gradle",
  "projectType":"boot",
  "egovVersion":"5.0",
  "outputPath":"/Users/user/Desktop"
}
```

- 출력 예시:

```text
프로젝트 생성 완료: /Users/user/Desktop/egov-myproject
- build.gradle
- application.yml
- MyprojectApplication.java
```

- 에러 처리 기준: 파일 생성 IO 오류나 지원하지 않는 조합은 서비스 오류 메시지로 반환된다.

#### `getConfigTemplate(configType, packageName)`

- 사용 조건: 설정 파일 하나만 보완하거나 신규 프로젝트 설정 템플릿을 확인할 때 사용한다.
- 사용 금지 조건: 전체 프로젝트 생성 목적에는 `initializeProject()`를 사용한다.
- 입력 예시:

```json
{"configType":"applicationYml","packageName":"egovframework.let.sample"}
```

- 출력 예시:

```yaml
spring:
  datasource:
    url: jdbc:mysql://...
```

- 에러 처리 기준: 지원하지 않는 `configType`은 서비스 오류 메시지로 반환된다.

### ProjectScannerTool

#### `scanProjectStructure(projectRootPath)`

- 사용 조건: 기존 eGovFrame 프로젝트의 패키지 루트, URL 패턴, 레이어별 경로, 설정 파일, 파일 통계를 파악할 때 사용한다.
- 사용 금지 조건: 신규 프로젝트 생성에는 사용하지 않는다.
- 입력 예시:

```json
{"projectRootPath":"/Users/user/workspace/my-egov-project"}
```

- 출력 예시:

```text
패키지 루트: egovframework.let
도메인 목록: emp, bbs, board
Controller: 12개
Mapper XML: 10개
```

- 에러 처리 기준: 경로 접근 실패 또는 스캔 실패는 서비스 오류 메시지로 반환된다.

### RagTool

#### `ragIngest(docId, content, type)`

- 사용 조건: 문서, 소스코드 설명, 생성 이력을 Vector Store 검색 대상으로 등록할 때 사용한다.
- 사용 금지 조건: 비어 있는 content, 식별 불가능한 docId, 민감정보 포함 외부 문서 무검토 등록에는 사용하지 않는다.
- 입력 예시:

```json
{"docId":"egov-login-guide","content":"로그인 처리 방법...","type":"document"}
```

- 출력 예시:

```text
임베딩 완료: egov-login-guide (3개 청크 / 2450 chars)
```

- 에러 처리 기준: 임베딩 실패는 서비스 예외 메시지로 반환된다.

#### `ragIngestDirectory(directoryPath)`

- 사용 조건: 기존 프로젝트의 `.java` 파일을 RAG 참조 컨텍스트로 등록할 때 사용한다.
- 사용 금지 조건: Java 소스가 아닌 대용량 임의 디렉터리 또는 읽을 수 없는 경로에는 사용하지 않는다.
- 입력 예시:

```json
{"directoryPath":"/Users/user/project/src/main/java"}
```

- 출력 예시:

```text
Java 디렉터리 임베딩 완료: 42개 파일
```

- 에러 처리 기준: Java 파일이 없으면 `"Java 파일이 없습니다: ..."`, 스캔 실패는 `"디렉터리 스캔 실패: ..."`를 반환한다.

#### `ragIngestUrl(url, docId)`

- 사용 조건: 공식 문서나 가이드 HTML을 크롤링해 RAG 문서로 등록할 때 사용한다.
- 사용 금지 조건: 내부망, localhost, 파일 URL, 검증되지 않은 URL, 로그인이 필요한 페이지에는 사용하지 않는다.
- 입력 예시:

```json
{"url":"https://www.egovframe.go.kr/docs/5.0/getting-started/","docId":"egov-getting-started"}
```

- 출력 예시:

```text
임베딩 완료: egov-getting-started (8개 청크 / 7600 chars)
```

- 에러 처리 기준: 잘못된 URL, 허용되지 않는 스킴, 내부 네트워크 접근, 알 수 없는 호스트, HTTP 오류, 빈 텍스트는 실패 문자열로 반환한다.

#### `ragIngestUrls(urls)`

- 사용 조건: 여러 공식 문서 URL을 일괄 등록할 때 사용한다.
- 사용 금지 조건: 실패 시 롤백이 필요한 작업에는 사용하지 않는다. 각 URL을 순차 처리하고 성공/실패를 함께 반환한다.
- 입력 예시:

```json
{"urls":"https://url1.example/docs, https://url2.example/docs"}
```

- 출력 예시:

```text
[1/2] 성공: https://url1.example/docs
[2/2] 실패: https://url2.example/docs → HTTP 404
```

- 에러 처리 기준: URL별 처리 결과를 반환한다.

#### `ragSearch(query, topK)`

- 사용 조건: 등록된 문서, 소스코드, 생성 이력 중 관련 컨텍스트를 검색할 때 사용한다.
- 사용 금지 조건: Vector Store에 등록된 자료가 없거나 최신성이 필요한 웹 검색 대체로 사용하지 않는다.
- 입력 예시:

```json
{"query":"eGovFrame 로그인 처리 방법","topK":3}
```

- 출력 예시:

```text
[RAG Context]
문서: egov-login-guide
내용: ...
```

- 에러 처리 기준: 검색 결과가 없으면 빈 컨텍스트 또는 서비스 메시지를 반환한다.

### SchemaReaderTool

#### `getTableList(database)`

- 사용 조건: CRUD 생성 전 대상 DB의 테이블 목록을 확인할 때 사용한다.
- 사용 금지 조건: 테이블 컬럼 상세가 필요하면 `getTableSchema()`를 사용한다.
- 입력 예시:

```json
{"database":"com"}
```

- 출력 예시:

```text
테이블 목록:
- COMTNEMPLYRINFO
- COMTNMENUINFO
```

- 에러 처리 기준: DB 접근 오류는 서비스 오류 메시지로 반환된다.

#### `getTableSchema(database, tableName)`

- 사용 조건: VO, Mapper, Service, Controller 생성을 위한 컬럼명, 타입, PK, Null, 기본값, 설명을 조회한다.
- 사용 금지 조건: JOIN 관계 분석 목적에는 `getTableRelations()`를 함께 사용한다.
- 입력 예시:

```json
{"database":"com","tableName":"COMTNEMPLYRINFO"}
```

- 출력 예시:

```text
EMPLYR_ID | varchar(20) | PK | NOT NULL | 직원ID
USER_NM   | varchar(60) |    | NULL     | 사용자명
```

- 에러 처리 기준: 결과가 없으면 `"테이블을 찾을 수 없습니다: database.tableName"`을 반환한다.

#### `getTableRelations(database, tableName)`

- 사용 조건: FK, 암묵 JOIN, 공통코드 JOIN 후보, 마스터-디테일 구조 판단이 필요할 때 사용한다.
- 사용 금지 조건: 단순 컬럼 목록만 필요하면 `getTableSchema()`를 사용한다.
- 입력 예시:

```json
{"database":"com","tableName":"COMTNEMPLYRINFO"}
```

- 출력 예시:

```text
부모 테이블:
- COMTNORGNZTINFO via ORGNZT_ID
공통코드 JOIN 후보:
- EMPLYR_STTUS_CODE → COMTCCMMNDETAILCODE
권장: buildJoinSelectPrompt()
```

- 에러 처리 기준: 분석 실패는 서비스 오류 메시지로 반환된다.

### SecurityTemplateTool

#### `getSecurityTemplate(securityType, packageName, egovVersion, outputPath, projectType)`

- 사용 조건: eGovFrame 4.3/5.0 Security 설정, 필터, 핸들러, 로그인 JSP, securityMapper SQL을 생성하거나 템플릿으로 받을 때 사용한다.
- 사용 금지 조건: XML Security `<http>`와 Java Config `SecurityFilterChain`을 동시에 선언하지 않는다. 4.3 전용 키워드에 5.0을, 5.0 전용 키워드에 4.3을 지정하지 않는다.
- 입력 예시:

```json
{
  "securityType":"setup-all-war-50",
  "packageName":"egovframework.let.emp",
  "egovVersion":"5.0",
  "outputPath":"/Users/user/workspace/my-egov-project",
  "projectType":"war"
}
```

- 출력 예시:

```text
Security 템플릿 생성 완료:
- context-security.xml
- EgovProjectSecurityConfig.java
- EgovRoleHierarchyConfig.java
- egovLoginUsr.jsp
- security-mapper.sql
```

- 에러 처리 기준: 버전/조합 불일치, 지원하지 않는 securityType, 파일 저장 실패는 서비스 오류 메시지 또는 예외 메시지로 반환된다.

### SqlTool

#### `executeQuery(sql)`

- 사용 조건: CRUD 생성 후 실제 데이터, 컬럼 값 범위, SELECT 결과를 최대 100행까지 확인할 때 사용한다.
- 사용 금지 조건: `INSERT`, `UPDATE`, `DELETE`, `DDL` 실행에는 사용하지 않는다.
- 입력 예시:

```json
{"sql":"SELECT * FROM com.COMTNEMPLYRINFO LIMIT 10"}
```

- 출력 예시:

```text
| EMPLYR_ID | USER_NM |
| USER001   | 홍길동 |
```

- 에러 처리 기준: 허용되지 않는 SQL은 차단된다. 실행 오류는 `"SQL 실행 실패: ..."`를 반환한다.

#### `getSampleData(database, tableName, limit)`

- 사용 조건: 특정 테이블의 실제 값 샘플을 확인할 때 사용한다.
- 사용 금지 조건: 대량 조회 목적으로 사용하지 않는다. `limit`은 1~100 범위로 사용한다.
- 입력 예시:

```json
{"database":"com","tableName":"COMTNEMPLYRINFO","limit":10}
```

- 출력 예시:

```text
샘플 데이터 (10건):
| EMPLYR_ID | USER_NM |
```

- 에러 처리 기준: 내부적으로 읽기 전용 SELECT로 처리되며 SQL 오류는 서비스 메시지로 반환된다.

#### `explainQuery(sql)`

- 사용 조건: SELECT 쿼리의 인덱스 사용, 풀스캔, 조인 순서를 점검할 때 사용한다.
- 사용 금지 조건: DML/DDL 분석에는 사용하지 않는다.
- 입력 예시:

```json
{"sql":"SELECT * FROM com.COMTNEMPLYRINFO WHERE ORGNZT_ID = 'ORG001'"}
```

- 출력 예시:

```text
EXPLAIN 결과:
type: ref
key: IDX_ORGNZT_ID
rows: 12
```

- 에러 처리 기준: 실행 계획 조회 실패는 `"EXPLAIN 실패: ..."`를 반환한다.

### WorkflowGuideTool

#### `suggestNextStep(currentContext)`

- 사용 조건: CRUD 생성 작업의 다음 단계를 판단할 때 사용한다. 빈 문자열이면 전체 14단계를 반환한다.
- 사용 금지 조건: 실제 파일 생성이나 DB 조회를 대체하지 않는다.
- 입력 예시:

```json
{"currentContext":"getTableSchema 완료, VO 생성 완료"}
```

- 출력 예시:

```text
다음 단계: Mapper 생성
권장 Tool: generateSource(layer=mapper)
```

- 에러 처리 기준: 컨텍스트가 비어도 오류가 아니라 전체 워크플로우를 반환한다.

#### `suggestSecurityMenuAuthWorkflow(currentContext)`

- 사용 조건: SecurityTemplateTool → MenuTool → AuthTool 순서로 보안/메뉴/권한 작업을 진행할 때 사용한다.
- 사용 금지 조건: SQL 직접 실행 도구로 사용하지 않는다.
- 입력 예시:

```json
{"currentContext":"SecurityTemplateTool setup-all-war-50 완료"}
```

- 출력 예시:

```text
다음 단계: 상위 메뉴 조회
권장 Tool: getMenuStructure("6000000")
```

- 에러 처리 기준: 컨텍스트가 비어도 오류가 아니라 전체 9단계 워크플로우를 반환한다.
