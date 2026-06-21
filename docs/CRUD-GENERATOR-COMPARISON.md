# CRUD 생성기 비교 분석

> **대상:** egovframe-vscode-initializr vs springai MCP CRUD 생성기  
> **공통점:** eGovFrame 5.0 기반 Java CRUD 코드 자동 생성  
> **분석 기준:** 아키텍처, DDL 파싱, 템플릿 구조, 생성 코드 품질

> **문서 위치:** `docs/CRUD-GENERATOR-COMPARISON.md`  
> **검증 범위:** vscode-initializr 쪽은 `/Users/jeongdaeseob/workspace-spring-ai/egovframe-vscode-initializr` 코드 기준이다. springai MCP 쪽은 현재 `springai` 레포 내부의 `src/main/java/com/krdevops/springai/**` 및 `src/main/resources/templates/crud/**` 기준으로 확인한 내용이다. 다른 외부 MCP 레포/배포본을 기준으로 볼 경우 springai MCP 항목은 별도 코드 링크가 필요한 비교 메모로 취급한다.

주요 근거 파일:

- vscode-initializr: `src/shared/ddlParser.ts`, `src/shared/dataTypes.ts`, `src/shared/templateContext.ts`, `src/utils/codeGenerator.ts`, `templates/code/sample-vo-template.hbs`
- springai: `CrudOrchestrationService.java`, `CrudModelFactory.java`, `CrudLayerDefinition.java`, `CrudMappingUtils.java`, `CrudTemplateRenderer.java`, `src/main/resources/templates/crud/*.ftl`

---

## 1. 아키텍처 개요

| 항목 | vscode-initializr | springai MCP |
|---|---|---|
| **실행 환경** | VS Code Extension (TypeScript/Node.js) | Spring Boot MCP Server (Java) |
| **호출 방식** | VS Code 명령팔레트 / Webview UI | Claude Desktop AI 에이전트 도구 호출 |
| **템플릿 엔진** | Handlebars (`.hbs`) | FreeMarker (`.ftl`) |
| **입력 소스** | DDL 텍스트 직접 붙여넣기 | 실제 DB 커넥션 (JdbcTemplate → INFORMATION_SCHEMA) |
| **파일 저장** | VS Code 워크스페이스 직접 저장 | MCP Tool → `CodeSaverTool`로 저장 |

### 처리 흐름

```
[vscode-initializr]
DDL 텍스트 입력 (Webview UI)
  → parseDDL() [ddlParser.ts]
  → getTemplateContext() [templateContext.ts]
  → Handlebars.compile(.hbs)
  → VS Code 워크스페이스에 파일 저장

[springai MCP]
Claude Desktop → MCP 도구 호출
  → SchemaReaderTool: DB 커넥션 → INFORMATION_SCHEMA 조회
  → CrudPromptBuilderTool: 스키마 → 템플릿 컨텍스트 조립
  → CodeTemplateTool: FreeMarker 렌더링 (.ftl)
  → CodeSaverTool: 파일 저장
```

---

## 2. DDL / 스키마 파싱

### vscode-initializr — `ddlParser.ts`

```
DDL 텍스트 → parseDDL() → {tableName, tableComment, attributes[], pkAttributes[]}
```

**강점:**
- MySQL/PostgreSQL DDL 중심 파싱. Oracle은 DDL 방언 전체 지원이 아니라 `NUMBER`, `VARCHAR2`, `RAW`, `CLOB` 등 타입 매핑 일부를 제공
- `COMMENT ON COLUMN` (PostgreSQL), 인라인 `COMMENT '...'` (MySQL) 모두 파싱
- `extractCreateTableStatements()`: 중첩 괄호 depth 추적으로 여러 `CREATE TABLE` statement를 인식하지만, CRUD 생성 경로의 `parseDDL()`은 첫 번째 statement만 사용
- **복합 PK**: `PRIMARY KEY (col1, col2)` 제약조건 파싱 → `pkAttributes[]`에 모두 포함
- `validateDDL()`: DDL 유효성 검사 함수 별도 제공

**컬럼 객체:**
```typescript
interface Column {
  ccName: string       // camelCase: column_name → columnName
  columnName: string   // 원본 컬럼명
  isPrimaryKey: boolean
  pcName: string       // PascalCase: column_name → ColumnName
  dataType: string     // SQL 타입
  javaType: string     // Java 타입
  comment: string      // DDL COMMENT 절에서 추출
}
```

### springai MCP — `SchemaService` (JdbcTemplate)

```
DB 커넥션 → INFORMATION_SCHEMA.COLUMNS → {columnName, dataType, nullable, maxLength, comment, pkOrdinal}
```

**강점:**
- DDL 없이도 동작, **실제 DB 스키마 그대로 반영**
- `IS_NULLABLE`, `CHARACTER_MAXIMUM_LENGTH` 등 제약 정보 자동 추출
- `jdbcType` 필드도 함께 생성 (MyBatis `jdbcType=VARCHAR` 지원)
- `stringType`, `required`, `maxLength` 메타 정보 → Bean Validation 자동 적용

**한계:**
- 현재 구현은 MySQL `information_schema` 컬럼명(`CHARACTER_MAXIMUM_LENGTH`, `COLUMN_COMMENT`) 중심
- 현재 `CrudTemplateModel`은 대표 PK 단일 객체를 사용하므로 복합 PK 전체를 모델 중심으로 다루지 못함

---

## 3. 타입 매핑

### vscode-initializr — `dataTypes.ts` (전용 파일)

```typescript
const predefinedDataTypes = {
  VARCHAR: "java.lang.String",
  BIGINT:  "java.lang.Long",
  // ... 27개 명시적 매핑
}

// Oracle NUMBER(p,s) 정밀도/스케일 별도 처리
function getJavaTypeByNumberPrecision(upperType: string): string {
  // scale > 0  → BigDecimal
  // precision ≤ 9  → Integer
  // precision ≤ 18 → Long
  // 그 외         → BigDecimal
}
```

지원 범위: MySQL/PostgreSQL 계열 타입과 Oracle 계열 일부 타입(`NUMBER`, `RAW`, `CLOB`, `VARCHAR2`) 매핑. Oracle DDL 문법 전체 지원은 아님.

### springai MCP — `SchemaService` (Java switch/map)

```java
// jdbcType 필드도 함께 반환
Field {
  javaType: "String",
  jdbcType: "VARCHAR",   // MyBatis jdbcType 속성용
  stringType: true,      // @NotBlank vs @NotNull 판단용
  maxLength: 200,        // @Size(max=200) 생성용
  required: true         // nullable=NO
}
```

---

## 4. 템플릿 컨텍스트 변수 비교

| 변수 용도 | vscode-initializr (Handlebars) | springai MCP (FreeMarker) |
|---|---|---|
| 클래스명 (PascalCase) | `{{className}}` | `${domain}` |
| 클래스명 (camelCase) | `{{classNameFirstCharLower}}` | `${domainLc}` |
| 한국어 도메인명 | 없음 | `${domainKr}` |
| 패키지명 | `{{packageName}}` | `${packageName}` |
| 패키지 경로 | 템플릿 변수가 아니라 `generateCrudFromDDL()` 내부 출력 경로 계산용 `packagePath` | `packageName`에서 변환 |
| 테이블명 | `{{tableName}}` | `${tableName}` |
| 전체 컬럼 | `{{attributes}}` (배열) | `${fields}` (배열) |
| PK 컬럼 | `{{pkAttributes}}` (**배열**, 복합 PK 표현 가능) | `${pk}` (**단일 객체**, 대표 PK 중심) |
| 비PK 컬럼 | 없음 (템플릿에서 `unless isPrimaryKey`로 필터) | `${nonPkFields}` (미리 분리) |
| URL 접두어 | 없음 | `${urlPrefix}` |
| jakarta 플래그 | 없음 | `${jakartaValidation}` (boolean) |
| 날짜 | `{{date}}` | `${date}` |
| 작성자 | `{{author}}` | (없음, "Claude AI" 고정) |
| 버전 | `{{version}}` | (없음) |

---

## 5. 생성 파일 비교

| 파일 | vscode-initializr | springai MCP | 비고 |
|---|---|---|---|
| `XxxVO.java` | ✅ (`extends XxxDefaultVO`) | ✅ (단일 클래스, Lombok) | 구조 상이 |
| `XxxDefaultVO.java` | ✅ (페이징 필드 분리) | ❌ (VO에 통합) | |
| `XxxController.java` | ✅ | ✅ | |
| `XxxService.java` (interface) | ✅ | ✅ | |
| `XxxServiceImpl.java` | ✅ | ✅ | |
| `XxxMapper.java` (interface) | ✅ | ✅ | |
| `Xxx_SQL.xml` | ✅ | ✅ | |
| JSP 목록 | ✅ | ✅ | |
| JSP 상세 | ❌ | ✅ | springai 우위 |
| JSP 등록 | ✅ | ✅ | |
| JSP 수정 | ❌ | ✅ (regist/updt 분리) | springai 우위 |
| Thymeleaf list/register | ✅ | ❌ | vscode 우위 |
| ValidationHandler | ❌ | ✅ (`controller-advice.java.ftl`) | springai 우위 |

---

## 6. 생성 코드 품질 비교

### 6-1. VO 구조

```java
// vscode-initializr: eGovFrame 클래식 패턴 (DefaultVO 분리)
public class BoardVO extends BoardDefaultVO {
    /** 제목 */
    // @EgovNullCheck(message="title is required")  // 템플릿에서 주석 처리된 validation 힌트
    private String title;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}

public class BoardDefaultVO implements Serializable {
    private String searchCondition = "";
    private String searchKeyword   = "";
    private int    pageIndex       = 1;
    private int    pageUnit        = 10;
    private int    firstIndex      = 1;
    private int    recordCountPerPage = 10;
    // ... getters/setters
}
```

```java
// springai MCP: 현대적 패턴 (Lombok + Bean Validation + 페이징 통합)
@Getter @Setter
public class BoardVO {
    // 제목
    @NotBlank
    @Size(max = 200)
    private String title;

    // 페이징/검색 공통 필드
    private int            pageIndex      = 1;
    private int            pageUnit       = 10;
    private PaginationInfo paginationInfo;     // 페이징 객체 직접 참조
    private String         searchCondition = "";
    private String         searchKeyword   = "";
}
```

### 6-2. 의존성 주입 방식

```java
// vscode-initializr: eGovFrame 표준 @Resource (필드 주입)
@Controller
public class BoardController {
    @Resource(name = "boardService")
    private BoardService boardService;

    @Resource(name = "propertiesService")
    private EgovPropertyService propertiesService;
}

// springai MCP: Lombok 생성자 주입 (Spring 권장)
@Controller
@RequiredArgsConstructor
public class EgovBoardController {
    private final BoardService        boardService;
    private final EgovPropertyService propertiesService;
}
```

### 6-3. Controller HTTP 메서드 매핑

```java
// vscode-initializr: @GetMapping / @PostMapping 명확히 구분
@GetMapping("/board/boardList.do")
public String selectBoardList(...) { }

@PostMapping("/board/addBoard.do")
public String addBoard(...) { }

// springai MCP: @RequestMapping 통합 (GET/POST 미구분)
@RequestMapping("/board/BoardList.do")
public String selectBoardList(...) { }

@RequestMapping("/board/BoardRegist.do")
public String insertBoard(...) { }
```

### 6-4. ServiceImpl 트랜잭션 처리

```java
// vscode-initializr: @Transactional 없음, EgovIdGnrService 포함
@Service("boardService")
public class BoardServiceImpl extends EgovAbstractServiceImpl implements BoardService {

    @Resource(name = "egovIdGnrService")
    private EgovIdGnrService egovIdGnrService;   // ID 자동 생성

    @Override
    public void insertBoard(BoardVO vo) throws Exception {
        String id = egovIdGnrService.getNextStringId();
        vo.setBoardId(id);
        boardMapper.insertBoard(vo);
    }
}

// springai MCP: @Transactional 명시, ID 생성 없음
@Service("boardService")
@RequiredArgsConstructor
public class EgovBoardServiceImpl extends EgovAbstractServiceImpl implements BoardService {

    private final BoardMapper boardMapper;

    @Override
    @Transactional
    public void insertBoard(BoardVO boardVO) throws Exception {
        boardMapper.insertBoard(boardVO);
    }
}
```

### 6-5. Mapper XML — UPDATE 절

```xml
<!-- vscode-initializr: setVar 헬퍼로 첫 컬럼 수동 처리 -->
UPDATE BOARD
SET
{{~setVar "isFirstSet" true~}}
{{#each attributes}}
  {{#unless isPrimaryKey}}
    {{#if @root.isFirstSet}}
    TITLE={{concat "#{" ccName "}"}}
    {{~setVar "isFirstSet" false~}}
    {{else}}
    , CONTENT={{concat "#{" ccName "}"}}
    {{/if}}
  {{/unless}}
{{/each}}
WHERE BOARD_ID=#{boardId}

<!-- springai MCP: MyBatis <set> 태그 활용 (자동 콤마/trailing comma 처리) -->
UPDATE BOARD
<set>
    TITLE    = #{title},
    CONTENT  = #{content}
</set>
WHERE BOARD_ID = #{boardId}
```

### 6-6. Mapper XML — resultMap PK 처리

```xml
<!-- vscode-initializr: PK/비PK 구분 없이 모두 <result> -->
<resultMap id="board" type="...BoardVO">
    <result property="boardId" column="BOARD_ID"/>
    <result property="title"   column="TITLE"/>
</resultMap>

<!-- springai MCP: PK는 <id>, 비PK는 <result> 구분 -->
<resultMap id="boardMap" type="...BoardVO">
    <id     property="boardId" column="BOARD_ID"/>
    <result property="title"   column="TITLE"/>
</resultMap>
```

### 6-7. 페이지네이션 SQL

```sql
-- vscode-initializr: LIMIT/OFFSET 표준 SQL (VO 필드 직접 참조)
LIMIT #{recordCountPerPage} OFFSET #{firstIndex}

-- springai MCP: MySQL/MariaDB 스타일 LIMIT x,y (PaginationInfo 객체 통해 참조)
LIMIT #{paginationInfo.firstRecordIndex}, #{paginationInfo.recordCountPerPage}
```

---

## 7. 복합 PK 지원

| 항목 | vscode-initializr | springai MCP |
|---|---|---|
| 복합 PK 파싱 | ✅ `pkAttributes[]` 배열로 표현 가능 | ⚠️ 현재 모델은 `pk` 단일 객체 중심 |
| WHERE 절 생성 | Mapper 템플릿에서 `pkAttributes[]`를 반복해 `pk1=#{} AND pk2=#{}` 형태 생성 가능 | 현재 템플릿은 대표 PK 기준 |
| resultMap | `<result>` 동일 처리 | `<id>` / `<result>` 구분 ✅ |

---

## 8. Jakarta EE 대응

| | vscode-initializr | springai MCP |
|---|---|---|
| import 방식 | `jakarta.*` 고정 | `${jakartaValidation}` 플래그로 전환 |
| 코드 예시 | `import jakarta.annotation.Resource;` | `<#if jakartaValidation>import jakarta.validation...` |
| 적용 위치 | Controller, ServiceImpl | VO (Validation), Controller |

---

## 9. 커스텀 템플릿 확장성

| | vscode-initializr | springai MCP |
|---|---|---|
| 커스텀 템플릿 업로드 | ✅ `uploadTemplates()`: 외부 `.hbs` 파일 업로드 후 즉시 렌더링 | ❌ 서버 재배포 필요 |
| 컨텍스트 JSON 다운로드 | ✅ `downloadTemplateContext()`: 템플릿 변수 JSON 내보내기 | ❌ |
| 파일 선택 저장 | ✅ QuickPick으로 생성할 파일 체크박스 선택 | ❌ 전체 생성 |
| 프로젝트 초기화 | ❌ | ✅ `ProjectInitializrTool`: WAR/Boot × 4.3/5.0 골격 생성 |
| 보안 설정 생성 | ❌ | ✅ `SecurityTemplateTool`: Spring Security 설정 파일 생성 |

---

## 10. 종합 비교표

| 항목 | vscode-initializr 우위 | springai MCP 우위 |
|---|---|---|
| **입력 방식** | DDL 텍스트만 있으면 동작 (DB 불필요) | 실제 DB에서 nullable/maxLength 자동 추출 |
| **DB 방언** | MySQL/PostgreSQL DDL 중심, Oracle 계열 타입 일부 매핑 | 현재 구현은 MySQL information_schema 중심 |
| **복합 PK** | `pkAttributes[]` 배열로 표현 가능 | 대표 PK 단일 객체 중심 |
| **Bean Validation** | VO 템플릿에 주석 처리된 `@EgovNullCheck` 힌트 제공 | `@NotBlank`/`@Size` 자동 생성 |
| **VO 구조** | DefaultVO 분리 (eGovFrame 클래식) | Lombok으로 간결, 페이징 통합 |
| **jakarta 대응** | jakarta 고정 | boolean 플래그로 javax/jakarta 전환 |
| **ID 생성** | EgovIdGnrService 자동 연동 | 없음 (직접 구현 필요) |
| **HTTP 메서드** | @Get/@Post 명확히 분리 | @RequestMapping 통합 (단순) |
| **MyBatis UPDATE** | 수동 첫 컬럼 처리 (setVar) | `<set>` 태그 활용 (더 안전) |
| **resultMap** | `<result>` 통일 | `<id>` / `<result>` 구분 |
| **뷰 완성도** | Thymeleaf 추가 제공 | 상세/등록/수정/목록 JSP 4개 완비 |
| **트랜잭션** | 없음 | `@Transactional` 자동 적용 |
| **에러 핸들링** | 없음 | 생성 CRUD용 ValidationHandler 템플릿 포함 |
| **의존성 주입** | @Resource (eGovFrame 표준) | @RequiredArgsConstructor (Spring 권장) |
| **커스텀 템플릿** | 외부 .hbs 업로드 즉시 실행 | 서버 재배포 필요 |
| **파일 선택** | QuickPick 체크박스 선택 저장 | 전체 일괄 생성 |

---

## 11. 통합 제안

두 시스템이 **상호 보완 관계**에 있어, 다음 요소를 결합하면 더 완성도 높은 생성기가 됩니다.

| 도입할 기능 | 출처 | 적용 대상 |
|---|---|---|
| 복합 PK 파싱 (`pkAttributes[]` 배열) | vscode-initializr | springai MCP `SchemaService` |
| PostgreSQL 타입 및 Oracle 계열 일부 타입 매핑 | vscode-initializr `dataTypes.ts` | springai MCP |
| `<id>` / `<result>` 구분 | springai MCP | vscode-initializr `sample-mapper-template.hbs` |
| `<set>` 태그 UPDATE | springai MCP | vscode-initializr |
| `@Transactional` per-method | springai MCP | vscode-initializr `sample-service-impl-template.hbs` |
| `${jakartaValidation}` 플래그 | springai MCP | vscode-initializr (현재 jakarta 고정) |
| Bean Validation 자동 생성 | springai MCP | vscode-initializr VO 템플릿 |
| EgovIdGnrService 연동 | vscode-initializr | springai MCP ServiceImpl 템플릿 |
| ValidationHandler 생성 | springai MCP | vscode-initializr 생성 파일 목록 추가 |
