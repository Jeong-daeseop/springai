# buildBoardFeature 게시판 전용 생성 기능 구현계획

> **용어 정리**: 초기 논의는 `featureType: board`라는 파라미터 방식으로 시작했지만, 검토 결과 기존 `buildFullCrudPrompt()` 시그니처에 끼워 넣기보다 `buildBoardFeature()` 전용 Tool 메서드를 추가하는 방식이 더 명확하다고 결론 냈다. 이 문서에서 "featureType: board"는 기능 개념명이고, 실제 구현 진입점은 `buildBoardFeature()` 메서드다.

## 1. 배경 및 목적

`CrudPromptBuilderTool`은 현재 단일 테이블 기반 CRUD만 생성한다. `COMTNBBS`를 입력하면 게시판 업무가 아닌 단순 CRUD가 나온다.

`buildBoardFeature()`는 이 한계를 보완해 BBS 업무 단위(목록/상세/등록/수정/논리삭제 + 조회수 + 마스터 연동)를 한 번에 생성하는 전용 Tool 메서드다.

### 설계 원칙
- **기존 CRUD 경로 보존**: `CrudOrchestrationService`, `CrudTemplateModel`, `CrudLayerDefinition`은 수정하지 않는다
- **Board 전용 오케스트레이터 분리**: `BoardOrchestrationService`로 완전 분리
- **템플릿 완전 분리**: `templates/board/` 별도 디렉터리

---

## 2. 목표 아키텍처

```
CrudPromptBuilderTool
 ├─ buildFullCrudPrompt(...)   → (기존) CrudOrchestrationService
 │                                ├─ CrudSchemaQueryService
 │                                ├─ CrudModelFactory
 │                                ├─ CrudTemplateRenderer
 │                                └─ templates/crud/*.ftl
 │
 └─ buildBoardFeature(...)     → BoardOrchestrationService [신규]
                                  ├─ BoardSchemaService      [신규]
                                  ├─ BoardModelFactory       [신규]
                                  ├─ BoardTemplateRenderer   [신규]
                                  └─ templates/board/*.ftl   [신규]
```

---

## 3. 입력 파라미터 설계

`CrudPromptBuilderTool`에 `buildBoardFeature()` 메서드를 추가한다 (기존 `buildFullCrudPrompt()` 보존).

```java
@Tool(description = "eGovFrame 게시판(BBS) 소스를 업무 단위로 생성합니다...")
public String buildBoardFeature(
    String database,              // DB명. 예: com
    String domain,                // 도메인명 PascalCase. 예: Bbs
    String packageName,           // 패키지명. 예: egovframework.let.bbs
    String outputPath,            // 저장 경로 절대경로
    @Nullable String mainTable,   // 기본값: COMTNBBS
    @Nullable String masterTable, // 기본값: COMTNBBSMASTER
    @Nullable String useTable,    // 기본값: COMTNBBSUSE
    @Nullable String fileTable,       // 기본값: COMTNFILE
    @Nullable String fileDetailTable, // 기본값: COMTNFILEDETAIL
    @Nullable String egovVersion,     // 기본값: "5.0"
    @Nullable String viewType         // 기본값: "jsp"
)
```

**별도 메서드로 분리한 이유**: 기존 `buildFullCrudPrompt()`는 파라미터가 이미 8개이고, 게시판은 입력 테이블이 복수라 인터페이스가 크게 달라진다. 안 B(별도 메서드)가 더 명확하다.

**파라미터 기본값**

| 파라미터 | `null` 시 기본값 |
|---|---|
| `mainTable` | `COMTNBBS` |
| `masterTable` | `COMTNBBSMASTER` |
| `useTable` | `COMTNBBSUSE` |
| `fileTable` | `COMTNFILE` |
| `fileDetailTable` | `COMTNFILEDETAIL` |
| `egovVersion` | `5.0` |
| `viewType` | `jsp` |

---

## 4. 신규 파일 목록

### 4.1 Tool 계층

| 파일 | 변경 유형 | 내용 |
|---|---|---|
| `tools/CrudPromptBuilderTool.java` | **수정** | `buildBoardFeature()` 메서드 추가 |

### 4.2 Model 계층

| 파일 | 내용 |
|---|---|
| `model/board/BoardTemplateModel.java` | 게시판 전용 FreeMarker 변수 record |
| `model/board/BoardLayerDefinition.java` | board 레이어 정의 (공통 8개 + JSP/Thymeleaf 각 4개) |

> **FeatureType enum 불필요**: `buildBoardFeature()` 별도 메서드 방식을 채택했으므로 enum으로 분기할 필요가 없다. 추가하면 오히려 혼란을 준다.

### 4.3 Service 계층

| 파일 | 내용 |
|---|---|
| `service/BoardSchemaService.java` | 복수 테이블 컬럼 조회 |
| `service/BoardModelFactory.java` | 복수 스키마 → `BoardTemplateModel` 변환 |
| `service/BoardTemplateRenderer.java` | FreeMarker `templates/board/` 렌더링 |
| `service/BoardOrchestrationService.java` | 게시판 생성 전체 흐름 조율 |
| `service/BoardOrchestrationResult.java` | 결과 DTO |
| `service/ThymeleafRuntimeConfigurer.java` | pom.xml·ViewResolver 보강 로직 — `CrudOrchestrationService` private 메서드에서 분리 |

### 4.4 FreeMarker 템플릿 (`templates/board/`)

```
vo.java.ftl                 — BbsVO (BBS_ID, NTT_ID 포함)
search-vo.java.ftl          — BbsSearchVO extends DefaultVO
mapper.java.ftl             — BbsMapper interface
mapper.xml.ftl              — BbsMapper.xml (JOIN + 조회수 UPDATE)
service.java.ftl            — BbsService interface
service-impl.java.ftl       — EgovBbsServiceImpl
controller.java.ftl         — EgovBbsController (8개 URL, 1차 범위)
validation-handler.java.ftl — EgovBbsValidationHandler
jsp-list.jsp.ftl
jsp-detail.jsp.ftl
jsp-regist.jsp.ftl
jsp-updt.jsp.ftl
thymeleaf-list.html.ftl
thymeleaf-detail.html.ftl
thymeleaf-regist.html.ftl
thymeleaf-updt.html.ftl
```

> **생성 파일 수**: 템플릿은 16개이지만 1회 실제 생성은 `공통 8개 + 화면 4개(viewType에 따라 JSP 또는 Thymeleaf) = 12개`다.

---

## 5. 핵심 설계

### 5.1 BoardTemplateModel

`CrudTemplateModel`과 독립된 record. 모델 오염 방지.

```java
public record BoardTemplateModel(
    // 공통
    String packageName,        // egovframework.let.bbs
    String domain,             // Bbs
    String domainLc,           // bbs
    String domainKr,           // 게시판
    String tableName,          // COMTNBBS
    String masterTableName,    // COMTNBBSMASTER
    String urlPrefix,          // /bbs/bbs
    String date,
    String egovVersion,
    boolean jakartaValidation,
    // 복합 PK — Mapper XML은 columnName, Controller/화면은 javaName 사용
    FieldModel bbsId,          // BBS_ID / bbsId
    FieldModel nttId,          // NTT_ID / nttId
    // 첨부파일
    boolean hasFile,
    FieldModel atchFileId,     // ATCH_FILE_ID / atchFileId (hasFile=false면 null)
    String fileDetailTableName,    // COMTNFILEDETAIL (없으면 null)
    // 필드
    List<FieldModel> fields,
    List<FieldModel> listFields,
    List<FieldModel> formFields,
    List<FieldModel> searchFields
)
```

### 5.2 BoardSchemaService

기존 `CrudSchemaQueryService.fetchColumns()`를 직접 수정하지 않고 래핑해서 재사용.

```java
@Service
@RequiredArgsConstructor
public class BoardSchemaService {

    private final CrudSchemaQueryService schemaQueryService;

    public Map<String, List<Map<String, Object>>> fetchBoardSchemas(
            String database,
            String mainTable, String masterTable,
            String useTable, String fileTable, String fileDetailTable) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        result.put("main",   schemaQueryService.fetchColumns(database, mainTable));
        result.put("master", schemaQueryService.fetchColumns(database, masterTable));
        if (useTable != null) {
            result.put("use", schemaQueryService.fetchColumns(database, useTable));
        }
        if (fileTable != null) {
            result.put("file", schemaQueryService.fetchColumns(database, fileTable));
        }
        if (fileDetailTable != null) {
            result.put("fileDetail", schemaQueryService.fetchColumns(database, fileDetailTable));
        }
        return result;
    }
}
```

**테이블별 필수/옵션 처리 기준**

| 테이블 키 | 처리 | 실패 기준 |
|---|---|---|
| `main` (COMTNBBS) | **필수** | 비어 있으면 `IllegalArgumentException` |
| `master` (COMTNBBSMASTER) | **필수** | 비어 있으면 `IllegalArgumentException` |
| `use` (COMTNBBSUSE) | 옵션 | null 허용, 비어 있어도 계속 진행 |
| `file` (COMTNFILE) | 옵션 | null 허용, `hasFile=false`로 설정 |
| `fileDetail` (COMTNFILEDETAIL) | 옵션 | null 허용, `hasFile=false`로 설정 |
```

### 5.3 SQL 전략 (mapper.xml.ftl)

목록 조회 (JOIN + 공지 우선 정렬):
```sql
SELECT b.*, m.BBS_NM
FROM ${tableName} b
LEFT JOIN ${masterTableName} m ON b.BBS_ID = m.BBS_ID
WHERE b.BBS_ID = #{bbsId}
  AND b.USE_AT = 'Y'
ORDER BY b.NOTICE_AT DESC, b.SORT_ORDR DESC, b.NTT_ID DESC
LIMIT #{paginationInfo.firstRecordIndex}, #{paginationInfo.recordCountPerPage}
```

조회수 증가 (상세 조회 직후 별도 호출):
```sql
UPDATE ${tableName}
   SET RDCNT = COALESCE(RDCNT, 0) + 1
 WHERE BBS_ID = #{bbsId}
   AND NTT_ID = #{nttId}
```

게시판 사용 여부 확인 (`selectBoardUseAt` — 목록 진입 시 호출):
```sql
SELECT USE_AT
  FROM ${useTableName}
 WHERE BBS_ID = #{bbsId}
```

> `USE_AT = 'Y'`가 아니면 Controller에서 접근 불가 메시지를 반환한다. 인증 사용자 연동은 TODO 주석으로 남긴다.

### 5.4 Controller URL 목록

**1차 필수 (7개)**

| URL | HTTP | 설명 |
|---|---|---|
| `/bbs/bbsList.do` | GET | 게시글 목록 |
| `/bbs/bbsDetail.do` | GET | 게시글 상세 + 조회수 증가 |
| `/bbs/bbsRegistView.do` | GET | 등록 화면 |
| `/bbs/bbsRegist.do` | POST | 등록 처리 |
| `/bbs/bbsUpdtView.do` | GET | 수정 화면 |
| `/bbs/bbsUpdt.do` | POST | 수정 처리 |
| `/bbs/bbsDelete.do` | POST | 논리삭제 처리 (`USE_AT = 'N'`) |

**조건부 생성 (`hasFile=true`일 때만 controller.java.ftl에서 렌더링)**

| URL | HTTP | 설명 |
|---|---|---|
| `/bbs/bbsFileDownload.do` | GET | 첨부파일 다운로드 |

> `/bbs/bbsFileDelete.do` (첨부파일 삭제)는 Phase 3 이후. DB 정합성 처리 복잡.

### 5.5 BoardLayerDefinition

`CrudLayerDefinition`과 동일한 패턴. 상속 없이 독립 record.

```java
public record BoardLayerDefinition(String layerKey, String fileNameSuffix, String subPathTemplate) {

    private static final List<BoardLayerDefinition> COMMON_LAYERS = List.of(
        new BoardLayerDefinition("vo",             "VO.java",               "src/main/java/egovframework/let/{PKG}/service/"),
        new BoardLayerDefinition("searchVo",       "SearchVO.java",         "src/main/java/egovframework/let/{PKG}/service/"),
        new BoardLayerDefinition("mapper",         "Mapper.java",           "src/main/java/egovframework/let/{PKG}/service/impl/"),
        new BoardLayerDefinition("mapperXml",      "Mapper.xml",            "src/main/resources/egovframework/mapper/{DOMAIN_LC}/"),
        new BoardLayerDefinition("service",        "Service.java",          "src/main/java/egovframework/let/{PKG}/service/"),
        new BoardLayerDefinition("serviceImpl",    "ServiceImpl.java",      "src/main/java/egovframework/let/{PKG}/service/impl/"),
        new BoardLayerDefinition("controller",     "Controller.java",       "src/main/java/egovframework/let/{PKG}/web/"),
        new BoardLayerDefinition("validHandler",   "ValidationHandler.java","src/main/java/egovframework/let/{PKG}/web/")
    );

    public static final List<BoardLayerDefinition> JSP_LAYERS = ...;        // COMMON + 4 JSP
    public static final List<BoardLayerDefinition> THYMELEAF_LAYERS = ...;  // COMMON + 4 HTML
}
```

---

## 6. 재사용 가능한 기존 코드

| 기존 코드 | 위치 | 재사용 방법 |
|---|---|---|
| `CrudSchemaQueryService.fetchColumns()` | `service/` | BoardSchemaService에서 호출 — 수정 없음 |
| `FieldModel`, `PkModel` record | `model/crud/` | BoardTemplateModel 필드 타입으로 재사용 |
| `CrudMappingUtils` (DB→Java 타입 매핑) | `util/` | BoardModelFactory에서 직접 호출 |
| FreeMarker `Configuration` 빈 | `config/FreemarkerConfig.java` | BoardTemplateRenderer에 `@Autowired` |
| `CodeService.saveGeneratedCode()` | `service/` | BoardOrchestrationService에서 재사용 |
| `CodeValidatorService.validateDirectory()` | `service/` | Java/XML/JSP 검증만 수행. `viewType=thymeleaf`이면 .html 파일 검증은 별도 처리 또는 확장 포인트 추가 필요 |
| `GenerationHistoryService.saveHistory()` | `service/` | BoardOrchestrationService에서 재사용 |
| `CrudViewType` enum | `model/crud/` | BoardLayerDefinition에서 재사용 |

---

## 7. eGovVersion 분기

기존 `CrudModelFactory`와 동일한 로직을 `BoardModelFactory`에 적용한다.

```java
boolean jakartaValidation = egovVersion != null
    && (egovVersion.startsWith("5") || "latest".equalsIgnoreCase(egovVersion));
```

---

## 8. 단계별 구현

### Phase 1 — 기반 구조 (필수)

| 순서 | 작업 |
|---|---|
| 1 | `CrudOrchestrationService`의 Thymeleaf 보강 로직을 `ThymeleafRuntimeConfigurer`로 분리 **(사전 리팩토링)** |
| 2 | `model/board/BoardTemplateModel.java` record 생성 |
| 3 | `model/board/BoardLayerDefinition.java` 생성 |
| 4 | `service/BoardSchemaService.java` 생성 |
| 5 | `service/BoardModelFactory.java` 생성 |
| 6 | `service/BoardTemplateRenderer.java` 생성 |
| 7 | `service/BoardOrchestrationService.java` 생성 |
| 8 | `tools/CrudPromptBuilderTool.java`에 `buildBoardFeature()` 추가 |

> **McpConfig 수정 불필요**: `buildBoardFeature()`가 기존 `CrudPromptBuilderTool` 클래스에 추가되므로 MCP Tool 등록은 자동. 신규 Service 빈들은 `@Service`로 자동 등록.

### Phase 2 — 템플릿 작성 (필수)

`templates/board/*.ftl` 16개 작성. 기존 `templates/crud/*.ftl` 참고.
1회 실제 생성 파일: **공통 8개 + 화면 4개(viewType에 따라 JSP 또는 Thymeleaf) = 12개**.

주요 차이점:
- VO에 `BBS_ID`, `NTT_ID` 복합 키 포함 + `SearchVO` 분리
- controller에 8개 URL (GET/POST 구분, 1차 범위)
- mapper.xml에 LEFT JOIN + 조회수 UPDATE
- 화면에 `NOTICE_AT`(공지) 강조 렌더링

### Phase 3 — 첨부파일 기본 연동 (선택)

- `ATCH_FILE_ID` 기반 파일 목록 조회 SQL
- 상세 화면 첨부파일 목록 표시
- 다운로드 URL 생성
- **업로드/파일삭제는 제외**: MultipartFile + 저장 경로 환경 의존성이 크다. `/bbs/bbsFileDelete.do`도 Phase 3 이후

### Phase 4 — 테스트 (필수)

| 테스트 클래스 | 검증 항목 |
|---|---|
| `BoardSchemaServiceTest` | 다중 테이블 컬럼 조회 |
| `BoardModelFactoryTest` | BBS_ID/NTT_ID 복합 키 처리 |
| `BoardTemplateRendererTest` | FreeMarker 템플릿 렌더링 |
| `BoardOrchestrationServiceTest` | 전체 흐름 통합 |
| 기존 CRUD 회귀 | `buildFullCrudPrompt()` 기존 동작 유지 (board 코드와 완전 분리 확인) |

---

## 9. 리스크 및 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| `BBS_ID + NTT_ID` 복합 PK | `CrudTemplateModel` 재사용 불가 | `BoardTemplateModel`에 `FieldModel bbsId`, `FieldModel nttId`로 보유 (columnName + javaName 모두 포함) |
| 첨부파일 업로드 환경 의존 | 저장 경로·multipart 설정 프로젝트마다 다름 | Phase 1에서 다운로드 URL 생성까지만, 업로드는 Phase 3 이후 |
| `COMTNBBSUSE` 권한 처리 | 활용 지점이 불명확 | 1차 범위: 목록 진입 시 `USE_AT = 'Y'` 게시판인지 확인하는 SQL 하나만 생성. 인증 사용자 연동은 TODO 주석으로 표시 |
| eGovFrame 4.3 대응 | javax vs jakarta 분기 | 기존 `jakartaValidation` 플래그 그대로 적용 |
| Thymeleaf viewType 런타임 설정 | `CrudOrchestrationService`의 보강 로직이 private 메서드라 직접 재사용 불가 | Phase 1 사전 리팩토링으로 `ThymeleafRuntimeConfigurer` 분리 후 CRUD·Board 양쪽에서 호출 |
| Thymeleaf HTML 검증 누락 | `CodeValidatorService`는 Java/XML/JSP 중심, `.html` 검증 없음 | `viewType=thymeleaf`이면 HTML 검증을 별도 처리하거나 Board 전용 검증 메서드 추가 |

---

## 10. 1차 구현 제외 범위

다음은 안정성·환경 의존성 이유로 1차 구현에서 제외한다.

- 첨부파일 **업로드** (MultipartFile, 저장 경로)
- 첨부파일 **삭제** (`/bbs/bbsFileDelete.do`, DB 정합성 처리 복잡)
- 권한 체크 고도화 (`COMTNBBSUSE` + Spring Security)
- 답글 정렬 (`ANSWER_AT`, `ANSWER_NO`)
- eGovFrame 4.3 Thymeleaf 화면 지원

---

## 11. 검증 방법

1. `./gradlew build` — 기존 테스트 전체 통과 확인
2. 기존 CRUD 회귀: Claude Desktop에서 `buildFullCrudPrompt()`로 `COMTNEMPLYRINFO` 생성 → 기본 `viewType=jsp` 기준 11개 파일 동일 출력
3. 게시판 생성 호출:
   ```
   buildBoardFeature(
     database=com, domain=Bbs,
     packageName=egovframework.let.bbs,
     outputPath=/path/to/project,
     mainTable=COMTNBBS
   )
   ```
4. 생성 파일 체크: VO에 `BBS_ID`/`NTT_ID`, Controller에 8개 URL, mapper.xml에 JOIN SQL + 조회수 UPDATE
5. 대상 프로젝트에서 생성 Java 파일 컴파일 — import/타입 오류 없음 확인

---

## 12. 관련 문서

- [featureType-board-impact-review.md](./featureType-board-impact-review.md) — 영향평가 원문
- [buildFullCrudPrompt_사용가이드.md](./buildFullCrudPrompt_사용가이드.md) — 기존 CRUD Tool 가이드
- [CrudPromptBuilderTool_기능및역할_상세설명.md](../tool-reference/CrudPromptBuilderTool_기능및역할_상세설명.md) — Tool 레퍼런스