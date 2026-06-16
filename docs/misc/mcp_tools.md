# MCP Tool 전체 가이드

> 총 19개 클래스 / 44개 메서드

---

## 1. Tool 목록 및 요청 예시

### 직원 관리 — `EmployeeTool` → `EmployeeService`

| 메서드 | 설명 |
| --- | --- |
| `getEmployeeList(keyword)` | 직원 목록 조회 (최대 20건) |
| `getEmployee(emplyrId)` | 직원 단건 조회 |
| `createEmployee(...)` | 직원 신규 등록 |
| `updateEmployee(...)` | 직원 정보 수정 |
| `deleteEmployee(emplyrId)` | 직원 삭제 |

**요청 예시**
```
# 목록 조회
직원 목록 보여줘
"김" 이름으로 직원 검색해줘
전체 직원 조회해줘

# 단건 조회
직원 ID가 USER001인 직원 정보 보여줘

# 등록
직원 등록해줘.
- ID: USER010
- 이름: 홍길동
- 이메일: hong@example.com
- 직위: 대리
- 휴대폰: 010-1234-5678
- 고유ID: ESNTL00000000000010

# 수정
USER001 직원의 직위를 "과장"으로 수정해줘

# 삭제
USER010 직원 삭제해줘
```

---

### 스키마/DB — `SchemaReaderTool` → `SchemaService`, `TableRelationService`

| 메서드 | 설명 |
| --- | --- |
| `getTableList(database)` | 테이블 목록 조회 |
| `getTableSchema(...)` | 컬럼 상세 정보 조회 |
| `getTableRelations(database, tableName)` | 테이블 연관관계 분석 |

**요청 예시**
```
com 데이터베이스의 테이블 목록 보여줘
COMTNEMPLYRINFO 테이블의 컬럼 정보 알려줘
COMTNEMPLYRINFO 테이블의 연관관계 분석해줘
```

---

### SQL 실행 — `SqlTool` → `SqlService`

| 메서드 | 설명 |
| --- | --- |
| `executeQuery(sql)` | 읽기 전용 SQL 실행 (`SELECT`, `SHOW`, `EXPLAIN`, `DESC`) |
| `getSampleData(database, tableName, limit)` | 샘플 데이터 조회 |
| `explainQuery(sql)` | 쿼리 실행계획 분석 |

**요청 예시**
```
SELECT * FROM COMTNEMPLYRINFO WHERE USER_NM LIKE '%홍%' 실행해줘
COMTNBBSMASTER 테이블 샘플 데이터 보여줘
SELECT * FROM COMTNEMPLYRINFO 쿼리의 실행계획 분석해줘
```

---

### 코드 생성 — `CodeTemplateTool` · `CrudPromptBuilderTool` · `CodeSaverTool`

| 메서드 | 설명 |
| --- | --- |
| `getCodeTemplate(layer)` | eGovFrame 레이어별 표준 템플릿 반환 |
| `buildFullCrudPrompt(...)` | CRUD 통합 프롬프트 생성 또는 자동 생성 |
| `buildMasterDetailPrompt(...)` | 1:N 마스터-디테일 프롬프트 생성 |
| `buildJoinSelectPrompt(database, tableName)` | JOIN 쿼리/VO 자동 생성 |
| `saveGeneratedCode(filePath, code)` | 생성 소스 파일 저장 |
| `checkOutputDirectory(baseDir)` | 저장 디렉터리 상태 확인 |
| `generateSource(layer, valuesJson)` | 서버 템플릿 기반 소스 생성 |

**요청 예시**
```
# 템플릿 조회
eGovFrame Controller 템플릿 보여줘
eGovFrame ServiceImpl 템플릿 보여줘

# CRUD 프롬프트 생성
COMTNEMPLYRINFO 테이블로 eGovFrame CRUD 소스 생성 프롬프트 만들어줘
도메인명: board, 테이블: COMTNBBSMASTER 로 CRUD 프롬프트 생성해줘

# 1:N 마스터-디테일
주문(ORDR_MST) - 주문상세(ORDR_DTL) 구조로 마스터-디테일 CRUD 프롬프트 만들어줘

# JOIN
COMTNEMPLYRINFO에 부서코드(DEPT_CD)로 COMTCCMMNDETAILCODE JOIN하는 쿼리/VO 생성해줘

# 저장 경로 확인
CRUD 소스 저장 기본 경로 알려줘

# 파일 저장
아래 코드를 /output/src/main/java/egovframework/let/emp/web/EgovEmpController.java 에 저장해줘
[코드 내용]

# 서버 직접 생성
COMTNEMPLYRINFO 테이블로 eGovFrame 5.x CRUD 소스를 서버에서 직접 생성해줘
```

---

### 코드 검증 — `CodeValidatorTool` → `CodeValidatorService`

| 메서드 | 설명 |
| --- | --- |
| `validateGeneratedCode(filePath)` | 단건 파일 검증 |
| `validateGeneratedCodeDirectory(directoryPath)` | 디렉토리 전체 일괄 검증 |

**요청 예시**
```
/output/src/main/java/egovframework/let/emp/web/EgovEmpController.java 파일 검증해줘
/output/src/main/java/egovframework/let/emp/ 디렉토리 전체 소스 검증해줘
```

---

### 프로젝트 관리 — `ProjectInitializrTool` · `ProjectScannerTool` · `ProjectHealthTool` · `OutputPathResolverTool`

| 메서드 | 설명 |
| --- | --- |
| `initializeProject(...)` | 프로젝트 파일 직접 생성 |
| `getConfigTemplate(configType, packageName)` | eGovFrame 설정 템플릿 반환 |
| `scanProjectStructure(projectRootPath)` | 프로젝트 구조 스캔 |
| `checkProjectHealth(projectRootPath, domain)` | 도메인 소스 완성도 점검 |
| `getDefaultOutputPath(domain)` | CRUD 저장 기본 경로 반환 |
| `resolveProjectOutputPath(projectRootPath, packageName, domain)` | 실제 저장 경로 분석 |

**요청 예시**
```
# 프로젝트 초기화
/output 경로에 eGovFrame 5.x 프로젝트 기본 구조 생성해줘
그룹ID: kr.go.agency, 아티팩트ID: portal

# 설정 템플릿
eGovFrame 설정 파일 템플릿 보여줘

# 프로젝트 스캔
/Users/project/src 경로 프로젝트 구조 스캔해줘

# 도메인 완성도 점검
emp 도메인 소스 생성 완성도 점검해줘

# 저장 경로 분석
/Users/myproject 경로 분석해서 CRUD 소스 저장할 실제 경로 알려줘
```

---

### RAG — `RagTool` → `RagService`

| 메서드 | 설명 |
| --- | --- |
| `ragIngest(docId, content, type)` | 문서 Vector Store 임베딩 |
| `ragIngestDirectory(directoryPath)` | 로컬 디렉터리 일괄 임베딩 |
| `ragIngestUrl(url, docId)` | URL 크롤링 후 임베딩 |
| `ragIngestUrls(urls)` | 다중 URL 일괄 임베딩 |
| `ragSearch(query, topK)` | 유사 문서 검색 |

**요청 예시**
```
# 문서 임베딩
아래 내용을 RAG에 등록해줘
[문서 내용]

# Java 파일 임베딩
/src/main/java/egovframework/let/emp 디렉토리 .java 파일 전부 RAG에 등록해줘

# URL 임베딩
https://www.egovframe.go.kr/docs/5.0/ 페이지를 RAG에 등록해줘

# 다중 URL 임베딩
아래 URL들을 RAG에 일괄 등록해줘
- https://www.egovframe.go.kr/docs/5.0/mvc/
- https://www.egovframe.go.kr/docs/5.0/persistence/

# 검색
eGovFrame MyBatis 설정 방법 검색해줘
Spring Security 설정 관련 문서 찾아줘
```

---

### 공통코드/메뉴/권한 — `CommonCodeTool` · `MenuTool` · `AuthTool`

| 메서드 | 설명 |
| --- | --- |
| `getCommonCode(codeId)` | 공통코드 상세 조회 |
| `searchCommonCode(keyword)` | 공통코드 그룹 검색 |
| `getMenuStructure(menuNo)` | 메뉴 트리 조회 |
| `generateMenuInsertSql(...)` | 메뉴 등록 SQL 반환 |
| `getProgramList(keyword)` | 프로그램 목록 검색 |
| `generateAuthInsertSql(urlPrefix, programNm, domain)` | 접근제어 SQL 반환 |

**요청 예시**
```
# 공통코드
COM001 그룹의 공통코드 상세 목록 보여줘
"성별" 키워드로 공통코드 그룹 검색해줘

# 메뉴
전체 메뉴 트리 보여줘
메뉴ID: MENU_EMP, 상위메뉴ID: MENU_ADM, 메뉴명: 직원관리 로 메뉴 등록 SQL 만들어줘

# 권한
"직원" 키워드로 프로그램 목록 검색해줘
/emp/list.do, /emp/detail.do URL에 대한 접근제어 SQL 만들어줘
```

---

### 보안/이력/기타 — `SecurityTemplateTool` · `GenerationHistoryTool` · `WorkflowGuideTool` · `DateTimeTool`

| 메서드 | 설명 |
| --- | --- |
| `getSecurityTemplate(...)` | Spring Security 설정 템플릿 반환 |
| `saveGenerationHistory(...)` | 소스 생성 이력 저장 |
| `getGenerationHistory(keyword)` | 소스 생성 이력 조회 |
| `suggestNextStep(currentContext)` | 워크플로우 다음 단계 제안 |
| `suggestSecurityMenuAuthWorkflow(currentContext)` | Security/Menu/Auth 워크플로우 제안 |
| `getCurrentDateTime(timezone)` | 현재 날짜/시간 반환 |
| `celsiusToFahrenheit(celsius)` | 섭씨→화씨 변환 |

**요청 예시**
```
# 보안 템플릿
eGovFrame Spring Security 설정 템플릿 보여줘

# 생성 이력
COMTNEMPLYRINFO 테이블 CRUD 소스 생성 이력 저장해줘
emp 도메인 소스 생성 이력 조회해줘

# 워크플로우 가이드
CRUD 소스 생성 완료 후 다음 단계 뭐야?
현재 단계: 코드생성 완료, 다음 할 일 알려줘

# 날짜/시간
현재 서울 시간 알려줘
현재 시간 Asia/Seoul 기준으로 알려줘

# 온도 변환
36.5도를 화씨로 변환해줘
```

---

## 2. Tool ↔ Service 매핑

| Tool 클래스 | 의존 Service |
| --- | --- |
| `EmployeeTool` | `EmployeeService` |
| `SchemaReaderTool` | `SchemaService`, `TableRelationService` |
| `SqlTool` | `SqlService` |
| `CrudPromptBuilderTool` | `CrudPromptBuilderService`, `MasterDetailService` |
| `CodeSaverTool` | `CodeService` |
| `CodeTemplateTool` | *(서비스 없음 — 템플릿 문자열 직접 반환)* |
| `CodeValidatorTool` | `CodeValidatorService` |
| `ProjectInitializrTool` | `ProjectInitializrService` |
| `ProjectScannerTool` | `ProjectScannerService` |
| `ProjectHealthTool` | `ProjectHealthService` |
| `OutputPathResolverTool` | `OutputPathResolverService` |
| `RagTool` | `RagService` |
| `CommonCodeTool` | `CommonCodeService` |
| `MenuTool` | `MenuService` |
| `AuthTool` | `AuthService` |
| `SecurityTemplateTool` | `SecurityTemplateService` |
| `GenerationHistoryTool` | `GenerationHistoryService` |
| `WorkflowGuideTool` | `WorkflowGuideService` |
| `DateTimeTool` | *(서비스 없음 — 순수 유틸리티)* |

### 특이사항

- `CrudPromptBuilderTool` — 서비스 **2개** 의존 (`CrudPromptBuilderService`, `MasterDetailService`)
- `SchemaReaderTool` — 서비스 **2개** 의존 (`SchemaService`, `TableRelationService`)
- `CodeSaverTool` — `ObjectMapper`(Jackson) 추가 의존
- `CodeTemplateTool`, `DateTimeTool` — 서비스 의존 없음
