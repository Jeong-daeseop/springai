# OpenAI 비전 모델을 활용한 화면 명세서 자동 생성

Figma/이미지 디자인 참조를 비전 LLM(OpenAI/Ollama)으로 분석한 `UiDesignSpec`이 실제 DB 스키마와 결합되어 `ScreenSpecification`(화면 명세서)으로 조립되고, 그 명세서가 다시 CRUD 템플릿 모델로 변환되는 네 단계를 정리한다.

```
입력 이미지/스크린샷 준비 (검증·전처리)
        │
        ▼
  VisionAnalysisClient.analyze()  ──→  UiDesignSpec (비전 분석 결과)
        │            rawColumns(DB 스키마)
        ▼                  │
  ScreenSpecAssembler.assemble() ◀────┘
        │
        ▼
  CrudModelFactory.fromSchema()  ──→  CrudTemplateModel (FreeMarker 렌더링용 모델)
```

---

## 1. 입력 이미지/스크린샷 준비

**지원 형식**: PNG/JPEG 이미지, 또는 PDF(페이지별로 PNG로 래스터화 후 처리).

### 검증 — `ReferencePathValidator`

`src/main/java/com/krdevops/springai/service/ReferencePathValidator.java`

```java
private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "pdf");

public Path validate(String referencePath) {
    Path path = Path.of(referencePath).toRealPath();   // 경로 정규화(심볼릭 링크 등 해석)
    if (!Files.isRegularFile(path)) throw ...;
    ensureAllowedRoot(path);                             // 허용된 루트 디렉터리 화이트리스트 검사
    String extension = extension(path);
    if (!ALLOWED_EXTENSIONS.contains(extension)) throw ...;
    long maxBytes = properties.getMaxFileMb() * 1024L * 1024L;
    if (Files.size(path) > maxBytes) throw ...;          // 파일 크기 제한
    verifyMagic(path, extension);                        // 매직 바이트로 실제 파일 형식 확인(확장자 위조 방지)
    return path;
}
```

확장자만 보지 않고 **매직 바이트(`verifyMagic`)로 실제 파일 포맷을 재확인**한다 — 확장자를 `.png`로 바꾼 다른 형식 파일을 걸러내기 위함이다. 경로는 `toRealPath()`로 정규화한 뒤 허용 루트 밖이면 차단한다(경로 탈출 방어).

### PDF 래스터화 — `PdfPageRasterizer`

`src/main/java/com/krdevops/springai/service/PdfPageRasterizer.java`

```java
public List<VisionAnalysisRequest.VisionImage> rasterize(Path pdfPath, String pageRange) {
    // pageRange: null/blank면 앞에서부터 maxPdfPages까지, "1,3,5-7" 같은 명시 지정도 지원
    // PDFBox PDFRenderer로 페이지마다 DesignVisionProperties.getRenderDpi() DPI의 PNG 생성
    // 페이지 수가 maxPdfPages를 초과하면 IllegalArgumentException
}
```

### 전처리 — `ImagePreprocessor`

`src/main/java/com/krdevops/springai/service/ImagePreprocessor.java`

- 이미지가 `maxImageDimension`을 초과하면 JPEG로 리사이즈
- 이미지 개수가 `maxImagesPerRequest`를 초과하면 여러 이미지를 격자로 합친 **contact sheet** 한 장 + 앞쪽 일부 원본으로 압축(비전 모델 호출 1회당 이미지 개수 제한 대응)

모든 이미지는 `VisionAnalysisRequest.VisionImage(pageNumber, mimeType, content)` record로 캡슐화되어 다음 단계(비전 모델 분석)로 전달된다.

---

## 2. 비전 모델 분석 테스트

### `VisionAnalysisClient` — 실제 인터페이스

`src/main/java/com/krdevops/springai/service/VisionAnalysisClient.java`

```java
public interface VisionAnalysisClient {
    UiDesignSpec analyze(VisionAnalysisRequest request);
    String providerId();
    String modelId();
    default boolean supportsVision() {
        return VisionModelCapability.supports(providerId(), modelId());
    }
}
```

구현체는 `OpenAiVisionAnalysisClient`/`OllamaVisionAnalysisClient`(둘 다 `AbstractChatVisionAnalysisClient` 공통 로직 상속)와, Vision 비활성 설정일 때 쓰이는 `DisabledVisionAnalysisClient` 3종이다.

### Mockito로 모킹하는 실제 테스트 패턴

`src/test/java/com/krdevops/springai/service/DesignReferenceAnalysisServiceTest.java`에서 실제로 쓰는 방식이다. **주의**: `UiDesignSpec`은 불변 **record**라서 `new UiDesignSpec()` 후 `setArchetype(...)`처럼 setter로 채우는 방식은 실제 API와 맞지 않는다. 정적 팩토리 `UiDesignSpec.empty(archetype)`을 쓰거나, 필드가 필요하면 record 생성자에 직접 값을 채운다.

```java
// 단위 테스트에서 VisionAnalysisClient를 mock 처리
VisionAnalysisClient client = mock(VisionAnalysisClient.class);

// 가장 단순한 형태: archetype만 있는 빈 스펙
when(client.analyze(any())).thenAnswer(invocation -> UiDesignSpec.empty("CRUD_LIST"));

// 컴포넌트까지 채운 응답이 필요하면 record 생성자를 직접 사용
// (components는 List<String>이 아니라 List<UiDesignSpec.ComponentSpec>)
UiDesignSpec response = new UiDesignSpec(
        "CRUD_LIST",                                                   // archetype: 목록 화면
        null,                                                          // layout
        List.of(
                new UiDesignSpec.ComponentSpec("Table", List.of("title", "status")),
                new UiDesignSpec.ComponentSpec("Pagination", List.of()),
                new UiDesignSpec.ComponentSpec("SearchBar", List.of())
        ),
        List.of(), List.of(), Map.of(), List.of(), List.of()
);
when(client.analyze(any())).thenReturn(response);
```

provider/model별로 다르게 응답하려면 익명 구현체로 고정하는 방식도 실제 테스트에 있다:

```java
VisionAnalysisClient gpt4o = new VisionAnalysisClient() {
    public UiDesignSpec analyze(VisionAnalysisRequest request) { ... }
    public String providerId() { return "openai"; }
    public String modelId() { return "gpt-4o-mini"; }
};
```

### 분석 결과 검증

`UiDesignSpec`이 올바르게 구조화됐는지는 record 접근자로 직접 검증한다 — 예: `assertThat(spec.archetype()).isEqualTo("CRUD_LIST")`, `assertThat(spec.components()).extracting(ComponentSpec::type).containsExactly("Table", "Pagination", "SearchBar")`. `uncertainties()`가 비어있는지, `fieldHints()`의 `role()`이 기대한 `UiFieldRole`인지도 같은 방식으로 검증 대상이 된다.

---

## 3. ScreenSpecAssembler

`src/main/java/com/krdevops/springai/service/ScreenSpecAssembler.java`

**`DesignReferenceTool`이 만든 `UiDesignSpec`(비전 분석 결과)과 실제 DB 스키마(`rawColumns`)를 결합해서, 검증 가능한 `ScreenSpecification` 초안을 조립하는 클래스**. `service` 패키지의 `@Service` 빈이고 `ScreenSpecValidator`를 주입받는다.

### 파이프라인에서의 위치

`ScreenSpecAssembler`를 실제로 호출하는 건 어셈블러 자신이 아니라 **`ScreenSpecificationService.create()`**다 — 오케스트레이션 책임은 서비스 레이어에 있다.

```
DesignReferenceTool.analyzeFigmaReference()
   → DesignAnalysisResult (uiSpec: UiDesignSpec 필드로 보유 — archetype·필드힌트·액션·레이아웃밀도·tokens·geometryTree 등)
   → ScreenSpecificationService.create(database, tableName, screenName, featureType, uiSpec, [listColumns], [detailColumns])
        (1) schemaQueryService.fetchColumns(database, tableName) → rawColumns
        (2) ScreenSpecAssembler.assemble(..., rawColumns, uiSpec, ...) → ScreenSpecification
                └─ 어셈블러 내부에서 이미 1차 validator.validate(draft) 수행 (DRAFT → 상태 판정)
        (3) ScreenDataBindingResolver.resolve(specification)  → 데이터 바인딩 재해석
        (4) validator.validate(...)  2차 검증   ← 최종 APPROVED/REVIEW_REQUIRED는 여기서 확정
        (5) repository.save(specification)      → DB 영속화
   → ScreenSpecification (최종 반환값)
```

즉 `ScreenSpecValidator.validate()`는 (2)의 어셈블러 내부와 (4)의 서비스 레벨, **총 두 번** 호출된다. `DesignReferenceTool.analyzeFigmaReference()`가 곧바로 `UiDesignSpec`을 반환하는 게 아니라, `UiDesignSpec`을 필드로 담은 `DesignAnalysisResult` record를 반환한다는 점도 유의할 것.

`uiSpec`이 `null`이면 `UiDesignSpec.empty(defaultArchetype(featureType))`로 대체해서, 디자인 참조 없이 순수 스키마만으로도 CRUD 생성이 되도록 만들어둔 게 특징이다.

### 1단계 — 필드 바인딩 두 갈래를 동시에 준비

`BindingAssemblyResult` 레코드에 세 가지를 한 번에 담는다.

- **`schemaBindings`** — `rawColumns` 전체를 그대로 훑어서(`bindingsFromSchema`), 컬럼마다 `UiFieldRolePolicy.inferRole(컬럼명)`으로 역할(TITLE/STATUS/CREATED_AT 등)을 추론해 만든 바인딩. **항상 물리 컬럼 전체**를 포함.
- **`hintBindings`** — `uiSpec.fieldHints()`가 있을 때만(`bindingsFromHints`), Figma에서 뽑은 필드 힌트 하나하나를 실제 컬럼에 매칭 시도. 매칭 규칙은 `findColumn()`:
  1. `UiFieldRolePolicy.candidateColumns(role)`이 주는 이름 후보 목록과 정확히 일치하는 컬럼을 우선
  2. 없으면 힌트 라벨을 정규화(`normalize()` — 영문/숫자/한글만 남기고 소문자화)해서 `COLUMN_COMMENT` 또는 `COLUMN_NAME`과 비교
  3. 그래도 못 찾으면 `FieldSource.unmapped()`로 만들고 `NO_COLUMN_CANDIDATE` ERROR 이슈를 남김(자리는 유지, 데이터 소스는 없음)
- **`pkColumns`** — `COLUMN_KEY == "PRI"`인 컬럼명 목록. 뒤에서 "화면에 PK는 항상 포함" 규칙에 사용.

역할이 STATUS/CATEGORY이면서 컬럼명이 `_CODE`/`_CD`로 끝나면 `FieldSource.commonCode(...)`로 승격시키고 `COMMON_CODE_GROUP_REQUIRED` WARNING을 단다 — 실제 공통코드 그룹 ID까지는 여기서 안 채우고 "확인 필요"만 표시한다.

### 2단계 — 4개 페이지 조립 (`pages()`)

archetype 하나로 list/detail/regist/updt 4개 `PageSpec`을 만든다.

| 페이지 | 필드 소스 | 비고 |
|---|---|---|
| `list` | `selectPageBindings("list", ...)` | 아래 3단계 참고 |
| `detail` | `selectPageBindings("detail", ...)` | 액션은 UPDATE/DELETE/BACK |
| `regist` | **항상 `schemaBindings` 그대로** | 물리 컬럼 전체, SAVE/CANCEL |
| `updt` | **항상 `schemaBindings` 그대로** | 물리 컬럼 전체, UPDATE/CANCEL |

즉 **등록·수정 폼은 디자인 힌트가 있어도 절대 필드를 줄이지 않는다** — 표시용 컬럼 축소는 list/detail에만 적용된다. 액션은 `ScreenActionSpec.fromLegacyCommand("SEARCH")` 식으로 문자열 커맨드를 변환해서 만든다.

### 3단계 — list/detail 컬럼 선택 우선순위 (`selectPageBindings`)

세 갈래 우선순위가 있다.

1. **`listColumns`/`detailColumns`를 명시적으로 넘겼으면** → `explicitBindings()`가 그 컬럼들로 정확히 선택 (`FieldSelectionSource.EXPLICIT`)
2. **명시 컬럼은 없지만, archetype이 그 페이지를 타겟팅**하고(`targetsPage` — archetype 문자열이 `_LIST`/`_DETAIL`로 끝나는지 체크) 힌트 바인딩이 있으면 → `mergePkBindings()`로 힌트 기반 선택 (`FieldSelectionSource.DESIGN_REFERENCE`)
3. **둘 다 없으면** → `schemaBindings` 전체 (`FieldSelectionSource.DEFAULT`)

`explicitBindings`/`mergePkBindings` 둘 다 공통 규칙이 있다:
- **PK는 선택 목록에 없어도 자동으로 앞에 끼워 넣음**(복합 PK 전부, PK가 없으면 첫 컬럼으로 폴백)
- **PK 포함 최대 `MAX_SELECTED_COLUMNS`(=6)개**를 넘으면 `IllegalArgumentException`을 던짐(조용히 자르지 않음) — `explicitBindings`는 존재하지 않는 컬럼명이 들어와도 즉시 예외

### 4단계 — 나머지 스펙 값 확정 후 조립

- `density`/`formColumnLayout`/`actionPlacement`/`searchPanelPlacement`는 각각 `LayoutDensity.from(...)` 등으로 `uiSpec.layout()`에서 변환(값이 없으면 각 enum의 기본값으로 떨어짐)
- **`componentStyles`(=`resolvedUi.components()`), `componentGeometry`(=`resolvedUi.geometryTree()`), `tokens`는 가공 없이 그대로 `ScreenSpecification`에 실어 넘김** — 이 세 필드는 assemble 안에서 아무 로직도 안 타고 단순 pass-through. (claude 경로 프롬프트에만 반영되고 auto 경로는 미참조하는 갈림은 `ScreenSpecAssembler`가 아니라 이후 소비 쪽(`CrudModelFactory` vs `ScreenSpecificationPromptFormatter`)에서 결정된다.)
- 마지막에 21개 인자짜리 `ScreenSpecification` 생성자로 `status=DRAFT`인 draft를 만들고, `validator.validate(draft)` 결과를 그대로 리턴 — **APPROVED/REVIEW_REQUIRED 판정 자체는 이 클래스 책임이 아니라 `ScreenSpecValidator`**가 한다.

### 설계상 주목할 지점

- `ScreenFieldBinding`은 필드가 12개(`semanticRole`, `mode` 포함)인데, 이 어셈블러는 **10-인자 레거시 호환 생성자만** 쓴다 — 주석에 "Figma 정규화 이전 단계는 semanticRole/mode를 알 수 없다"고 명시돼 있어, 이 클래스가 파이프라인 초기 단계(정규화 이전)에 위치한다는 걸 코드로 확인할 수 있다.
- `schemaBindings`의 `FieldSource`는 힌트가 있어도 항상 `column()`(물리 컬럼) 기반이지, 디자인 힌트의 `COMMON_CODE` 판정이 스키마 계약으로 새어 들어가지 않는다 — 공통코드 승격은 오직 `bindingsFromHints` 경로(표시 후보)에서만 일어난다.
- `ScreenSpecAssembler`는 **`ProgramMetadata`(`CrudProgramMetadata`/`BoardProgramMetadata`)를 전혀 참조하지 않는다.** 메뉴/URL 연동 정보는 이 클래스가 아니라 아래 `CrudModelFactory` 단계에서 합류한다 — "화면에 어떤 필드를 넣을지"(`ScreenSpecAssembler`)와 "이 화면이 기존 메뉴 시스템 어디에 꽂히는지"(`ProgramMetadata`)는 서로 다른 계층의 책임이다.

---

## 4. CrudModelFactory

`src/main/java/com/krdevops/springai/service/CrudModelFactory.java`

**DB 컬럼 원시 데이터(`rawColumns`, `List<Map<String,Object>>`)를 FreeMarker 렌더링용 타입 안전 모델(`CrudTemplateModel`)로 변환하는 팩토리.** `CrudSchemaQueryService.fetchColumns()`가 조회한 결과와, 앞 단계 `ScreenSpecAssembler`가 만든 `ScreenSpecification`, 그리고 `CrudProgramMetadata`(메뉴/URL 연동 정보)를 여기서 함께 받는다 — **UiDesignSpec + Schema + ProgramMetadata 3-way 결합이 실제로 일어나는 지점**이다(`ScreenSpecAssembler`가 아니라).

### 진입점 — `fromSchema()` 오버로드 4단계

```
fromSchema(table, domain, pkg, ver, rawColumns)                                    // ①
    → fromSchema(..., CrudProgramMetadata.fallback(null), JSP, LIST_ONLY, null)     // 메타데이터 없음

fromSchema(..., rawColumns, metadata)                                              // ②
    → fromSchema(..., metadata, JSP, LIST_ONLY, null)                              // ScreenSpec 없음(레거시 CRUD)

fromSchema(..., rawColumns, metadata, screenSpecification)                         // ③
    → fromSchema(..., metadata, JSP, LIST_ONLY, screenSpecification)               // Figma 참조 있는 CRUD

fromSchema(..., rawColumns, metadata, viewType, subsetMode, screenSpecification)   // ④ 실제 본체
```

앞의 3개는 하위호환용 축약 오버로드이며, 전부 결국 **④번(7-인자 버전)**으로 수렴한다.

### ④번 본체 — 단계별 흐름

#### (1) 뷰타입/서브셋 모드 확정
- `viewType`이 `null`이면 JSP로 폴백
- `subsetMode`가 `null`이면: Thymeleaf → `LIST_AND_DETAIL`, JSP → `LIST_ONLY`로 자동 결정

#### (2) 스키마 변환
`rawColumns`(Map 목록) → `toColumnMeta()` → `ColumnMeta` → `toFieldModel()` → `FieldModel`. 타입 변환(`javaType`/`jdbcType`)은 전부 `CrudMappingUtils`에 위임.

#### (3) PK 식별
`FieldModel::pk`로 필터링해서 **복합키(예: `NTT_ID`+`BBS_ID`)를 전부** 인식. PK가 하나도 없으면 첫 번째 컬럼을 PK로 간주(`CrudPromptBuilderService`와 동일 규칙이라고 주석에 명시).

#### (4) formFields(등록/수정 폼) 구성 — `buildFormFields()`
- `screenSpecification`의 `regist` 페이지가 `selectionSource() != DEFAULT`(명시 선택 또는 디자인 참조 선택)로 잡은 컬럼이 있으면 → 그 컬럼들만(단, `SYSTEM_MANAGED_FIELDS` = `frstRegistPnttm`/`frstRegisterId`/`lastUpdtPnttm`/`lastUpdusrId`는 항상 제외)
- 없으면 → PK 제외 전체 컬럼(`SYSTEM_MANAGED_FIELDS`만 뺀 것)으로 폴백
- 이후 `queryContractFactory.applyLabelOverrides(..., "regist")`로 라벨 오버라이드까지 적용

#### (5) listFields(목록) 구성 — `buildListFields()`
우선순위 3단계 + 항상 **최대 6개, PK는 항상 포함, 민감필드 제외**(`isSensitiveListField` — `password`/`ihid`/`esntl`/`cert`/`dn`/`lock`/`uniq`/`secret` 키워드 체크):

1. `screenSpecification`의 `list` 페이지가 명시/디자인참조 선택을 했으면 그 컬럼들
2. 없으면 `UiFieldRolePolicy.LIST_ROLE_PRIORITY`(역할 우선순위) 기반 자동 선택
3. 그래도 부족하면 하드코딩된 `preferred` 이름 목록(`userNm`, `emplNo`, `ofcpsNm` 등 — 직원 화면에 특화된 흔적)
4. 그래도 6개가 안 차면 남은 필드를 순서대로 채움

이후 `applyLabelOverrides`, 그리고 `queryContract.displayFields()`(검색조건/표시필드 계약, `GenerationQueryContractFactory`가 생성)를 `listFields`에 중복 없이(`addIfAbsent`) 병합한다.

#### (6) detailFields(상세) 구성 — `buildDetailFields()`
- `subsetMode != LIST_AND_DETAIL`이거나 `screenSpecification == null`이면 → `SensitiveFieldPolicy.filterDisplayFields(fields)`(전체 컬럼에서 민감필드만 뺀 것)
- `LIST_AND_DETAIL`이고 스펙의 `detail` 페이지가 명시/디자인참조 선택을 했으면 → 그 컬럼만(민감필드는 `SensitiveFieldPolicy.isSensitiveDisplayField`로 개별 체크)

> **주의**: list와 detail이 **민감필드 판단에 서로 다른 검사기**를 쓴다 — list는 `CrudModelFactory` 내부의 로컬 키워드 체크, detail은 별도 `SensitiveFieldPolicy` 클래스. 두 로직이 다르다는 점은 알아둘 필요가 있다.

#### (7) jakartaValidation 플래그
`egovVersion`이 `"5"`로 시작하거나 `"latest"`면 `true`.

#### (8) domainKr(한글 화면명) 결정
우선순위:
```
metadata.programKoreanName() 있으면 → stripScreenTypeSuffix()로 "목록/상세" 접미어 제거 후 사용
없으면 → 테이블명 기반 CrudMappingUtils.extractKoreanName()
```
`ProgramMetadata`가 실제 값에 반영되는 두 번째 지점(라우트 이외).

#### (9) urlPrefix + route 생성
`packageName`에서 `/emp/employer`식 경로 생성 후 `buildRoute()`:
```java
urlPrefix + "List.do",  metadata.registeredPath(ROLE_LIST)   // DB에 등록된 URL이 있으면 그걸, 없으면 null
urlPrefix + "Detail.do", metadata.registeredPath(ROLE_DETAIL)
... (RegistView/Regist/UpdtView/Updt/Delete 동일 패턴)
```
`CrudRouteModel`이 canonical 경로(`urlPrefix+"List.do"` 등)와 **DB 등록 경로 둘 다** 들고 있는 구조라, 실제로 어느 쪽을 쓸지는 템플릿(FreeMarker) 쪽 책임으로 넘어간다.

#### (10) 최종 조립
`layoutDensity`/`formColumnLayout`/`actionPlacement`/`searchPanelPlacement`는 `screenSpecification`이 있으면 거기서, 없으면 각각 `STANDARD`/`SINGLE_COLUMN`/`TOP_RIGHT`/`ABOVE_TABLE` 기본값으로 `CrudTemplateModel`을 만들어 반환한다.

### 부가 진입점 — `withDesignComponents()`

`fromSchema()`와 완전히 별개 경로다. 이미 만들어진 `CrudTemplateModel`을 받아 **업무 계약(필드/라우트/쿼리계약 등)은 그대로 복사**하고 `designComponents`(승인된 디자인 컴포넌트 렌더 입력)만 추가로 붙인다 — 주석에 "DB·Controller·VO 기반 업무 계약을 변경하지 않는다"고 명시된 대로, 디자인 정보가 스키마 계약을 오염시키지 않도록 분리해둔 지점이다.

### 요약 그림

```
rawColumns ──→ ColumnMeta ──→ FieldModel(전체)
                                  ├─ PK 분리 ─→ pk, effectivePkFields
                                  ├─ buildFormFields(screenSpec) ──────→ formFields
                                  ├─ buildListFields(screenSpec) ──────→ listFields  ←─ queryContract.displayFields 병합
                                  └─ buildDetailFields(subsetMode, screenSpec) ──→ detailFields

metadata(ProgramMetadata) ──→ domainKr, buildRoute()

screenSpecification ──→ layoutDensity / formColumnLayout / actionPlacement / searchPanelPlacement (없으면 기본값)

= CrudTemplateModel
```

---

## 5. ScreenSpecification에서 분기될 수 있는 화면 템플릿 모델 변환

`CrudModelFactory`는 이 파이프라인이 CRUD 화면일 때 쓰는 변환기고, `featureType`(board/master-detail)에 따라 실제로는 다른 클래스로 분기될 수 있다.

| 클래스 | 경로 | 역할 |
|---|---|---|
| `BoardModelFactory` | `service/BoardModelFactory.java` | 게시판(BBS) 스키마 → `BoardTemplateModel`. 주석에 "컬럼 변환 로직은 `CrudModelFactory`와 동일하며, 게시판 전용으로 복합 PK(`BBS_ID`,`NTT_ID`) 탐색·첨부파일 판단·목록/폼/검색 필드 선별을 추가한다"고 명시 |

**흥미로운 점**: Board는 `CrudModelFactory`의 자매 클래스(`BoardModelFactory`)를 따로 뒀는데, **MasterDetail은 별도 Factory가 없다** — `MasterDetailGenerationPlanner`와 `MasterDetailScreenSourceGenerator` 둘 다 그냥 `CrudModelFactory`를 직접 재사용한다(마스터 테이블용 CRUD 모델 1개 + 디테일 테이블용 CRUD 모델 1개를 각각 만드는 방식). CRUD/Board/MasterDetail 3종 생성기가 "각자 독립"이라던 이전 아키텍처 문서의 설명과는 별개로, **모델 변환 레이어에서는 CRUD와 MasterDetail이 실제로 같은 클래스를 공유**한다.

두 Factory 모두 `GenerationQueryContractFactory`를 공유해서 쿼리 계약(`queryContract`)과 라벨 오버라이드를 만든다는 점은 동일하다(4번 섹션 참고).

---

## 전체 파이프라인 요약

```
입력 이미지/스크린샷 준비 (ReferencePathValidator·PdfPageRasterizer·ImagePreprocessor)
        │
        ▼
  VisionAnalysisClient.analyze()  ──→  DesignAnalysisResult (uiSpec: UiDesignSpec)
        │            rawColumns(DB 스키마, CrudSchemaQueryService.fetchColumns)
        ▼                  │
  ScreenSpecificationService.create()
        ├─ ScreenSpecAssembler.assemble()   ──→ ScreenSpecification (1차 validate, DRAFT)
        ├─ ScreenDataBindingResolver.resolve()   (데이터 바인딩 재해석)
        └─ validator.validate() (2차) → repository.save()   (최종 상태 확정 + DB 영속화)
        │
        ▼
  ScreenSpecification (APPROVED / REVIEW_REQUIRED)
        │            rawColumns(재사용)   CrudProgramMetadata(메뉴/URL 연동)
        ▼                  │                              │
  CrudModelFactory.fromSchema() ◀─────────────────────────┘   (featureType=board → BoardModelFactory, master-detail → CrudModelFactory 재사용, 5번 참고)
        │
        ▼
  CrudTemplateModel / BoardTemplateModel (FreeMarker 렌더링용 최종 모델)
```

`ScreenSpecAssembler`는 "화면에 어떤 필드를 넣을지"(디자인 + 스키마)를 결정하고, `ScreenSpecificationService`가 그 결과를 데이터 바인딩 재해석·2차 검증·DB 저장까지 오케스트레이션한다. `CrudModelFactory`(또는 board면 `BoardModelFactory`)는 최종 승인된 명세에 "이 화면이 기존 메뉴 시스템 어디에 꽂히는지"(`ProgramMetadata`)까지 더해 최종 렌더링 모델을 완성한다.
