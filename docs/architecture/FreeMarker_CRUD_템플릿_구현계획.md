# FreeMarker CRUD 템플릿 전환 — 구현 계획

작성일: 2026-06-15

## 1. 현재 상태 요약

| 항목 | 현재 구현 |
|---|---|
| CRUD 템플릿 위치 | `CodeTemplateTool.java` 내 11개 Java text block |
| 치환 방식 | `CodeService.generateSource()` → `String.replace("{{KEY}}", value)` |
| 데이터 모델 | `PlaceholderValues` record → `toMap()` → `Map<String, String>` (flat) |
| 반복/조건 처리 | Java 코드에서 문자열 사전 조립 (`VO_FIELDS`, `RESULT_MAP_FIELDS` 등) |
| 템플릿 엔진 의존성 | 없음 |

### 1.1 현재 코드 구조

**`CodeTemplateTool.java`** — 11개 레이어 템플릿을 Java text block으로 보유:

| layer key | private 메서드 | 생성 파일 |
|---|---|---|
| `vo` | `voTemplate()` | `{Domain}VO.java` |
| `controller` | `controllerTemplate()` | `Egov{Domain}Controller.java` |
| `service` | `serviceTemplate()` | `{Domain}Service.java` (interface) |
| `serviceimpl` | `serviceImplTemplate()` | `Egov{Domain}ServiceImpl.java` |
| `mapper` | `mapperTemplate()` | `{Domain}Mapper.java` |
| `mapperxml` | `mapperXmlTemplate()` | `{Domain}Mapper.xml` |
| `jsplist` | `jspListTemplate()` | `Egov{Domain}List.jsp` |
| `jspdetail` | `jspDetailTemplate()` | `Egov{Domain}Detail.jsp` |
| `jspregist` | `jspRegistTemplate()` | `Egov{Domain}Regist.jsp` |
| `jspupdt` | `jspUpdtTemplate()` | `Egov{Domain}Updt.jsp` |
| `controlleradvice` | `controllerAdviceTemplate()` | `Egov{Domain}ValidationHandler.java` |

**`CodeService.generateSource()`** — `String.replace("{{" + key + "}}", value)` 반복 호출.

**`CrudPromptBuilderService.PlaceholderValues`** — 21개 키를 flat `Map<String, String>`으로 내보내는 record. `VO_FIELDS`, `RESULT_MAP_FIELDS` 등 사전 조립된 문자열 포함.

### 1.2 핵심 전환 포인트

현재 `PlaceholderValues.toMap()`이 21개 키를 **평면 문자열**로 내보낸다. `VO_FIELDS`, `JSP_LIST_TH` 같은 값은 이미 Java에서 조립된 긴 문자열이다. FreeMarker 도입 효과를 얻으려면 이 **사전 조립을 제거**하고 `List<FieldModel>`을 FreeMarker에 직접 넘겨야 한다.

---

## 2. Phase 1: 기반 구축 (의존성 + 모델 + 렌더러)

### 2.1 FreeMarker 의존성 추가

`build.gradle`:

```gradle
implementation 'org.freemarker:freemarker:2.3.33'
```

> `spring-boot-starter-freemarker`가 아닌 순수 라이브러리 사용.
> View resolver 자동 등록을 방지하기 위함.
> 이 프로젝트는 FreeMarker를 MVC View가 아닌 **코드 생성 전용**으로 사용한다.

### 2.2 도메인 모델 신규 작성

```text
src/main/java/com/krdevops/springai/model/crud/
├── ColumnMeta.java          # CrudSchemaQueryService 반환값 Map → 타입 안전 record
├── FieldModel.java          # 컬럼 1개의 메타데이터 (렌더링용)
├── CrudTemplateModel.java   # 전체 CRUD 렌더링 컨텍스트
└── PkModel.java             # PK 컬럼 전용

src/main/java/com/krdevops/springai/exception/
└── CrudTemplateRenderException.java  # FreeMarker 렌더링 실패 도메인 예외
```

**`FieldModel.java`**:

```java
public record FieldModel(
    String columnName,     // DB 컬럼명 (EMPLYR_ID)
    String javaName,       // 자바 필드명 (emplyrId)
    String javaType,       // String, int, LocalDate 등
    String comment,        // 한국어 코멘트
    boolean pk,            // PK 여부
    boolean required,      // NOT NULL 여부
    boolean stringType,    // true → @NotBlank, false → @NotNull (String 타입 여부)
    Integer maxLength,     // VARCHAR 길이 (nullable, stringType=true인 경우만 유효)
    String jdbcType        // MyBatis jdbcType (VARCHAR, INTEGER 등)
) {}
```

> `stringType` 필드는 `toFieldModel()`에서 `"String".equals(javaType)`으로 설정한다. Validation 어노테이션 선택 기준: `String` 타입 → `@NotBlank`, 그 외(`Integer`, `Long`, `BigDecimal` 등) → `@NotNull`.

**`PkModel.java`**:

```java
public record PkModel(
    String columnName,     // EMPLYR_ID
    String javaName,       // emplyrId
    String javaType        // String
) {}
```

**`CrudTemplateModel.java`**:

```java
public record CrudTemplateModel(
    String packageName,        // egovframework.let.emp
    String domain,             // Employer
    String domainLc,           // employer
    String domainKr,           // 직원
    String tableName,          // COMTNEMPLYRINFO
    String urlPrefix,          // /emp/employer
    String date,               // 생성일
    String egovVersion,        // "5.0" | "4.3"
    boolean jakartaValidation, // true → jakarta.validation, false → javax
    PkModel pk,                // PK 정보
    List<FieldModel> fields,   // 전체 필드 목록 (PK 포함)
    List<FieldModel> nonPkFields // PK 제외 필드 (UPDATE SET 용)
) {}
```

### 2.3 FreeMarker Configuration 빈

```text
src/main/java/com/krdevops/springai/config/FreemarkerConfig.java
```

```java
@Configuration
public class FreemarkerConfig {

    @Bean
    public freemarker.template.Configuration freemarkerConfiguration() {
        var cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setClassLoaderForTemplateLoading(
            getClass().getClassLoader(), "templates/crud");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(
            TemplateExceptionHandler.RETHROW_HANDLER);
        return cfg;
    }
}
```

### 2.4 CrudTemplateRenderer 서비스

```text
src/main/java/com/krdevops/springai/service/CrudTemplateRenderer.java
```

```java
@Service
@RequiredArgsConstructor
public class CrudTemplateRenderer {

    private final Configuration freemarkerConfig;

    /**
     * 지정된 FreeMarker 템플릿으로 CRUD 소스를 렌더링한다.
     *
     * @param templateName 템플릿 파일명 (확장자 제외, 예: "vo.java")
     * @param model        렌더링에 필요한 전체 컨텍스트
     * @return 렌더링된 소스 코드 문자열
     * @throws CrudTemplateRenderException 템플릿 로딩 또는 렌더링 실패 시
     */
    public String render(String templateName, CrudTemplateModel model) {
        try {
            Template template = freemarkerConfig.getTemplate(templateName + ".ftl");
            StringWriter writer = new StringWriter();
            // FreeMarker 2.3.33의 Java record 접근 방식 주의사항:
            // Java record는 getDomain()이 아닌 domain() accessor를 가진다.
            // FreeMarker 2.3.33부터 record accessor를 지원하지만, 버전/환경에 따라
            // ${domain} 접근이 실패할 수 있다. CrudTemplateRendererTest에서 반드시 검증 필요.
            // 불안정 시 아래 Map 모델 방식으로 전환한다 (2.4.1절 참조).
            template.process(model, writer);
            return writer.toString();
        } catch (IOException | TemplateException e) {
            throw new CrudTemplateRenderException(
                "템플릿 렌더링 실패: " + templateName, e);
        }
    }
}
```

#### 2.4.1 FreeMarker record 접근 방식 — 불확실성 대비

Java record는 `getDomain()` 형태의 getter가 없고 `domain()` accessor만 있다. FreeMarker 2.3.33은 record accessor를 지원하지만, 실제 동작은 `CrudTemplateRendererTest`로 먼저 검증해야 한다.

**접근 방식 A — record 직접 전달 (기본 시도):**

```java
template.process(model, writer);  // CrudTemplateModel record 직접 전달
```

템플릿에서 `${domain}`, `${packageName}` 등으로 접근. 테스트에서 검증 후 사용.

**접근 방식 B — 명시적 Map 모델 (A가 불안정할 경우 대안):**

```java
Map<String, Object> dataModel = new HashMap<>();
dataModel.put("domain", model.domain());
dataModel.put("packageName", model.packageName());
dataModel.put("fields", model.fields());
// ... 나머지 필드
template.process(dataModel, writer);
```

accessor 방식 의존 없이 명시적으로 키를 지정하므로 버전에 무관하게 안전하다. 단, 필드 추가 시 양쪽(`CrudTemplateModel` + `dataModel` 구성 코드)을 동기화해야 한다.

> **결정 기준:** `CrudTemplateRendererTest`에서 A 방식 검증 → 실패 시 B 방식으로 전환.

**예외 처리 정책:**

- `Configuration#getTemplate()` — `IOException` 발생 가능 (클래스패스 리소스 미존재 등)
- `Template#process()` — `TemplateException` (변수 누락 등) + `IOException` 발생 가능
- 두 예외 모두 `CrudTemplateRenderException` (unchecked) 으로 래핑하여 호출 스택에 전파
- `CrudTemplateRenderException`은 `RuntimeException`을 상속하는 신규 도메인 예외 클래스로 작성

```text
src/main/java/com/krdevops/springai/exception/CrudTemplateRenderException.java
```

---

## 3. Phase 2: `.ftl` 템플릿 파일 작성 (11개)

### 3.1 파일 목록

```text
src/main/resources/templates/crud/
├── vo.java.ftl
├── controller.java.ftl
├── service.java.ftl
├── service-impl.java.ftl
├── mapper.java.ftl
├── mapper.xml.ftl
├── jsp-list.jsp.ftl
├── jsp-detail.jsp.ftl
├── jsp-regist.jsp.ftl
├── jsp-updt.jsp.ftl
└── controller-advice.java.ftl
```

### 3.2 기존 placeholder → FreeMarker 전환 매핑

| 기존 placeholder | FreeMarker 대체 |
|---|---|
| `{{VO_FIELDS}}` | `<#list fields as f>` ... `</#list>` |
| `{{RESULT_MAP_FIELDS}}` | `<#list fields as f><#if f.pk>...<#else>...</#if></#list>` |
| `{{INSERT_COLUMNS}}` | `<#list fields as f>${f.columnName}<#sep>, </#list>` |
| `{{INSERT_VALUES}}` | `<#list fields as f>#{${f.javaName}}<#sep>, </#list>` |
| `{{UPDATE_SET}}` | `<#list nonPkFields as f>${f.columnName} = #{${f.javaName}}<#sep>, </#list>` |
| `{{MAPPER_COLUMNS}}` | `<#list fields as f>${f.columnName}<#sep>, </#list>` |
| `{{JSP_LIST_TH}}` | `<#list fields as f><th>${f.comment}</th></#list>` |
| `{{JSP_LIST_TD}}` | `<#list fields as f><td><c:out .../></td></#list>` |
| `{{JSP_DETAIL_ROWS}}` | `<#list fields as f>` detail row `</#list>` |
| `{{JSP_FORM_INPUTS}}` | `<#list fields as f>` + `<#if f.pk>hidden/readonly<#else>input</#if>` `</#list>` |
| `{{VALIDATION_IMPORT}}` | `<#if jakartaValidation>jakarta...<#else>javax...</#if>` |
| `{{PACKAGE}}` | `${packageName}` |
| `{{DOMAIN}}` | `${domain}` |
| `{{DOMAIN_LC}}` | `${domainLc}` |
| `{{DOMAIN_KR}}` | `${domainKr}` |
| `{{TABLE_NAME}}` | `${tableName}` |
| `{{PK_FIELD}}` | `${pk.javaName}` |
| `{{PK_COLUMN}}` | `${pk.columnName}` |
| `{{PK_TYPE}}` | `${pk.javaType}` |
| `{{URL_PREFIX}}` | `${urlPrefix}` |
| `{{DATE}}` | `${date}` |

### 3.3 템플릿 예시 — `vo.java.ftl`

> **주의:** PaginationInfo import는 `org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo` 를 사용한다.
> (`egovframework.rte...` 경로는 구버전이며 현재 `CodeTemplateTool`과 불일치한다.)

```ftl
package ${packageName}.service;

<#if jakartaValidation>
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
<#else>
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
</#if>
import lombok.Getter;
import lombok.Setter;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;

/**
 * ${domainKr} VO
 * @since ${date}
 */
@Getter
@Setter
public class ${domain}VO {

<#list fields as f>
    // ${f.comment}
    <#if !f.pk && f.required>
    <#if f.stringType>@NotBlank<#else>@NotNull</#if>
    </#if>
    <#if !f.pk && f.maxLength??>@Size(max = ${f.maxLength})</#if>
    private ${f.javaType} ${f.javaName};

</#list>
    // 페이징 — CodeValidatorService 검증 기준 필드 일치
    private PaginationInfo paginationInfo;
    private int pageIndex = 1;
    private int pageUnit = 10;
    private int pageSize = 10;
    private int firstIndex = 0;
    private int lastIndex = 0;
    private int recordCountPerPage = 10;
    private String searchCondition = "";
    private String searchKeyword = "";
}
```

### 3.4 템플릿 예시 — `mapper.xml.ftl`

```ftl
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="${packageName}.service.impl.${domain}Mapper">

    <resultMap id="${domainLc}ResultMap" type="${packageName}.service.${domain}VO">
<#list fields as f>
    <#if f.pk>
        <id property="${f.javaName}" column="${f.columnName}"/>
    <#else>
        <result property="${f.javaName}" column="${f.columnName}"/>
    </#if>
</#list>
    </resultMap>

    <sql id="searchCondition">
        <if test="searchKeyword != null and searchKeyword != ''">
            <choose>
                <when test="searchCondition == '0'">
                    AND ${pk.columnName} LIKE CONCAT('%', #{searchKeyword}, '%')
                </when>
            </choose>
        </if>
    </sql>

    <select id="select${domain}List" parameterType="${packageName}.service.${domain}VO"
            resultMap="${domainLc}ResultMap">
        SELECT <#list fields as f>${f.columnName}<#sep>, </#list>
        FROM ${tableName}
        WHERE 1=1
        <include refid="searchCondition"/>
        ORDER BY ${pk.columnName} DESC
        LIMIT #{paginationInfo.firstRecordIndex}, #{paginationInfo.recordCountPerPage}
    </select>

    <select id="select${domain}ListTotCnt" parameterType="${packageName}.service.${domain}VO"
            resultType="int">
        SELECT COUNT(*) FROM ${tableName}
        WHERE 1=1
        <include refid="searchCondition"/>
    </select>

    <select id="select${domain}" parameterType="${packageName}.service.${domain}VO"
            resultMap="${domainLc}ResultMap">
        SELECT <#list fields as f>${f.columnName}<#sep>, </#list>
        FROM ${tableName}
        WHERE ${pk.columnName} = #{${pk.javaName}}
    </select>

    <insert id="insert${domain}" parameterType="${packageName}.service.${domain}VO">
        INSERT INTO ${tableName} (
            <#list fields as f>${f.columnName}<#sep>, </#list>
        ) VALUES (
            <#list fields as f>#{${f.javaName}<#if f.jdbcType??>, jdbcType=${f.jdbcType}</#if>}<#sep>, </#list>
        )
    </insert>

    <update id="update${domain}" parameterType="${packageName}.service.${domain}VO">
        UPDATE ${tableName}
        SET <#list nonPkFields as f>${f.columnName} = #{${f.javaName}<#if f.jdbcType??>, jdbcType=${f.jdbcType}</#if>}<#sep>,
            </#list>
        WHERE ${pk.columnName} = #{${pk.javaName}}
    </update>

    <delete id="delete${domain}" parameterType="${packageName}.service.${domain}VO">
        DELETE FROM ${tableName}
        WHERE ${pk.columnName} = #{${pk.javaName}}
    </delete>

</mapper>
```

### 3.5 JSP EL 충돌 처리 규칙

JSP 파일은 `${...}`를 EL로 사용한다. FreeMarker도 `${...}`를 사용하므로 충돌이 발생한다.

**규칙 1 — 정적 JSP EL 구간:**

```ftl
<#noparse>
<c:out value="${result.emplyrId}" />
</#noparse>
```

**규칙 2 — 필드 반복 내 JSP EL:**

```ftl
<#list fields as f>
<td><c:out value="${'$'}{result.${f.javaName}}" /></td>
</#list>
```

**우선순위:** Java, Mapper XML, Service, Controller, VO를 먼저 `.ftl`로 전환하고, JSP는 충돌 처리 규칙을 검증한 뒤 전환한다.

---

## 4. Phase 3: PlaceholderValues → CrudTemplateModel 변환 계층

### 4.1 ColumnMeta 모델 도입 여부

현재 컬럼 메타데이터는 `CrudPromptBuilderService.fetchColumns()`(private)가 `JdbcTemplate`으로 직접 조회하여 `List<Map<String, Object>>`로 반환한다.
`SchemaReaderTool.getTableSchema()`는 **문자열**을 반환하므로 이 경로와는 별개다.
Phase 3에서 신규 작성하는 `CrudSchemaQueryService`가 이 조회 책임을 담당한다.

**옵션 A — ColumnMeta record 신규 도입 (권장):**

```text
src/main/java/com/krdevops/springai/model/crud/ColumnMeta.java
```

```java
public record ColumnMeta(
    String columnName,   // DB 컬럼명
    String dataType,     // varchar, int 등 (MySQL DATA_TYPE — 소문자)
    int columnSize,      // CHARACTER_MAXIMUM_LENGTH
    boolean nullable,    // IS_NULLABLE != "NO"
    boolean pk,          // COLUMN_KEY = "PRI"
    String remarks       // COLUMN_COMMENT
) {}
```

`CrudSchemaQueryService.fetchColumns()` 반환값(`List<Map<String,Object>>`)을 `CrudModelFactory` 진입 시점에 `ColumnMeta`로 변환하는 어댑터 메서드를 추가한다. 이후 내부 로직은 모두 `ColumnMeta` 기반으로 처리한다.

**옵션 B — Map 기반 유지:**

`CrudModelFactory`가 `List<Map<String, Object>>`를 직접 받아 처리. 타입 안전성이 낮고 키 오타 위험이 있어 비권장.

> **채택: 옵션 A.** `ColumnMeta` record를 Phase 1에서 모델 파일과 함께 신규 작성한다.

### 4.2 CrudModelFactory 서비스

```text
src/main/java/com/krdevops/springai/service/CrudModelFactory.java
```

**책임:** `CrudSchemaQueryService.fetchColumns()`가 반환하는 `List<Map<String, Object>>`를 `CrudTemplateModel`로 변환.

```java
@Service
public class CrudModelFactory {

    public CrudTemplateModel fromSchema(
            String tableName,
            String domain, String packageName,
            String egovVersion, List<Map<String, Object>> rawColumns) {

        // Map → ColumnMeta 변환
        List<ColumnMeta> columns = rawColumns.stream()
            .map(this::toColumnMeta)
            .toList();

        List<FieldModel> fields = columns.stream()
            .map(this::toFieldModel)
            .toList();

        FieldModel pkField = fields.stream()
            .filter(FieldModel::pk).findFirst().orElseThrow();

        PkModel pk = new PkModel(
            pkField.columnName(), pkField.javaName(), pkField.javaType());

        List<FieldModel> nonPkFields = fields.stream()
            .filter(f -> !f.pk()).toList();

        // 기존 CrudPromptBuilderService:118-122 로직과 동일하게 유지
        boolean jakarta = egovVersion != null
            && (egovVersion.startsWith("5") || "latest".equalsIgnoreCase(egovVersion));

        String domainLc = domain.substring(0, 1).toLowerCase()
                        + domain.substring(1);

        // domainKr: CrudMappingUtils로 이관된 helper 사용 (4.4절 참조)
        // import com.krdevops.springai.util.CrudMappingUtils; 또는 static import 적용
        String domainKr = CrudMappingUtils.extractKoreanName(tableName);

        // urlPrefix: 기존 로직 — packageName에서 "egovframework.let." 제거 후 "/" 변환
        // 예: "egovframework.let.emp" → "/emp/" + domainLc
        String urlPrefix = "/" + packageName.replace("egovframework.let.", "")
                                            .replace(".", "/")
                           + "/" + domainLc;

        return new CrudTemplateModel(
            packageName, domain, domainLc, domainKr,
            tableName,
            urlPrefix,
            LocalDate.now().toString(),
            egovVersion, jakarta,
            pk, fields, nonPkFields
        );
    }

    private ColumnMeta toColumnMeta(Map<String, Object> row) {
        // 키는 CrudPromptBuilderService.fetchColumns() SQL 결과 기준
        // CHARACTER_MAXIMUM_LENGTH, COLUMN_COMMENT, COLUMN_KEY 사용
        Object len = row.get("CHARACTER_MAXIMUM_LENGTH");
        return new ColumnMeta(
            (String) row.get("COLUMN_NAME"),
            (String) row.get("DATA_TYPE"),
            len != null ? ((Number) len).intValue() : 0,
            !"NO".equals(row.get("IS_NULLABLE")),
            "PRI".equals(row.get("COLUMN_KEY")),          // IS_PK 아님
            (String) row.getOrDefault("COLUMN_COMMENT", "") // REMARKS 아님
        );
    }

    private FieldModel toFieldModel(ColumnMeta col) {
        // helper 메서드는 모두 CrudMappingUtils 위임 (4.4절 참조)
        String javaType = CrudMappingUtils.mapJavaType(col.dataType(), col.columnSize());
        return new FieldModel(
            col.columnName(),
            CrudMappingUtils.toCamelCase(col.columnName()),
            javaType,
            col.remarks(),
            col.pk(),
            !col.nullable(),
            "String".equals(javaType),   // stringType: true → @NotBlank, false → @NotNull
            "varchar".equalsIgnoreCase(col.dataType()) ? col.columnSize() : null,
            CrudMappingUtils.mapJdbcType(col.dataType())
        );
    }
}
```

> **`jakartaValidation` 판단 로직:** `"5.0".equals(egovVersion)` 대신 기존 `CrudPromptBuilderService:118-122`와 동일하게 `startsWith("5") || "latest".equalsIgnoreCase(...)` 를 사용한다. `"5.1"` 등 마이너 버전 및 `"latest"` 키워드를 모두 수용한다.

### 4.3 컬럼 조회 책임 명시

`CrudModelFactory.fromSchema()`에 넘길 `rawColumns`를 어디서 가져오는지 명확히 해야 한다. 현재 컬럼 조회 로직(`fetchColumns()`)은 `CrudPromptBuilderService`의 **private 메서드**로 외부에서 접근할 수 없다.

세 가지 옵션:

| 옵션 | 방식 | 비고 |
|---|---|---|
| **A — 신규 서비스 분리 (권장)** | `CrudSchemaQueryService` 신규 작성, `JdbcTemplate` 직접 보유 | 책임 분리 명확, `CrudModelFactory`와 `CrudPromptBuilderService` 양쪽에서 재사용 가능 |
| B — 기존 메서드 public 승격 | `CrudPromptBuilderService.fetchColumns()` → `public` | 변경 최소, 단 서비스 응집도 낮아짐 |
| C — CrudModelFactory 직접 조회 | `CrudModelFactory`가 `JdbcTemplate` 주입 | 팩토리가 DB 접근하면 단일 책임 위반 |

> **채택: 옵션 A.** `CrudSchemaQueryService`를 Phase 3에서 `CrudModelFactory`와 함께 신규 작성한다.

```text
src/main/java/com/krdevops/springai/service/CrudSchemaQueryService.java
  └── List<Map<String, Object>> fetchColumns(String database, String tableName)
```

`orchestrateAuto()` 변경 흐름:

```text
CrudSchemaQueryService.fetchColumns() → rawColumns
CrudModelFactory.fromSchema(rawColumns, ...) → CrudTemplateModel
CrudTemplateRenderer.render(templateName, model) → 소스 문자열
CodeService.saveGeneratedCode(path, source) → 파일 저장
```

**layerKey → templateName 매핑 테이블:**

`CrudPromptBuilderTool`의 `LAYERS` 배열 key와 `.ftl` 파일명이 다르므로 매핑이 필요하다.

| layerKey (LAYERS 배열 실제 값) | templateName (`.ftl` 파일명) |
|---|---|
| `vo` | `vo.java` |
| `mapper` | `mapper.java` |
| `mapperXml` | `mapper.xml` |
| `service` | `service.java` |
| `serviceImpl` | `service-impl.java` |
| `controller` | `controller.java` |
| `controlleradvice` | `controller-advice.java` |
| `jspList` | `jsp-list.jsp` |
| `jspDetail` | `jsp-detail.jsp` |
| `jspRegist` | `jsp-regist.jsp` |
| `jspUpdt` | `jsp-updt.jsp` |

> **주의:** `mapperXml`, `serviceImpl`, `jspList` 등 camelCase 키가 혼재한다. `Map.of(...)` 구성 시 대소문자를 그대로 사용해야 한다.

`CrudTemplateRenderer` 또는 별도 상수 클래스에 이 매핑을 `Map<String, String>`으로 보유한다.

### 4.4 Helper 메서드 이관 책임

`CrudModelFactory`에서 사용하는 아래 helper 메서드들은 현재 `CrudPromptBuilderService` private 메서드로 구현되어 있다. Phase 3에서 `CrudMappingUtils`(static 유틸 클래스)로 이관한다.

| 메서드 | 현재 위치 | 이관 위치 |
|---|---|---|
| `toCamelCase(columnName)` | `CrudPromptBuilderService` private | `CrudMappingUtils` |
| `toJavaType(dataType)` → `mapJavaType(dataType, columnSize)` | `CrudPromptBuilderService` private (columnSize 인자 없음) | `CrudMappingUtils` (columnSize 파라미터 추가하여 확장 이관) |
| `mapJdbcType(dataType)` | 현재 없음 — **신규 추가** | `CrudMappingUtils` |
| `extractKoreanName(tableName)` | `CrudPromptBuilderService` private | `CrudMappingUtils` |

이관 후 `CrudPromptBuilderService`의 기존 private 메서드는 `CrudMappingUtils`를 호출하도록 위임 처리하여 하위 호환을 유지한다.

### 4.5 기존 PlaceholderValues와의 관계

`CrudModelFactory`가 기존 `CrudPromptBuilderService.PlaceholderValues`의 **문자열 사전 조립 로직을 대체**한다.

```text
기존: fetchColumns() [private] → 문자열 조립 → PlaceholderValues (flat Map) → String.replace()
전환: CrudSchemaQueryService.fetchColumns() → ColumnMeta → CrudModelFactory → CrudTemplateModel → FreeMarker 렌더링
```

---

## 5. Phase 4: 기존 Tool API 유지 + 내부 렌더러 교체

### 5.1 변경 대상

`CrudPromptBuilderTool.java`의 `orchestrateAuto()` 메서드 내부만 교체한다.

```text
기존 흐름:
  PlaceholderValues.toMap() → CodeService.generateSource() → String.replace()

전환 흐름:
  CrudModelFactory.fromSchema() → CrudTemplateRenderer.render() → 결과 문자열
```

### 5.2 외부 MCP Tool 계약 유지

| Tool 메서드 | 변경 여부 | 비고 |
|---|---|---|
| `CodeTemplateTool.getCodeTemplate(layer)` | **역할 변경** | 아래 5.4 참조 |
| `CodeSaverTool.generateSource(layer, valuesJson)` | **유지 (제약 있음)** | 아래 5.3 참조 |
| `CrudPromptBuilderTool.buildFullCrudPrompt(auto)` | 내부 교체 | `orchestrateAuto()`가 FreeMarker 렌더러 사용 |
| `CodeValidatorTool.validateGeneratedCode*()` | Tool 계약 유지, 내부 규칙 수정 가능 | `CodeValidatorService` 내부 검증 규칙(SQL ID 패턴 등)은 FreeMarker 생성 결과와 맞게 수정 대상 (6.2절 참조) |

### 5.3 CodeSaverTool.generateSource() 호환 전략

`CodeSaverTool.generateSource(layer, valuesJson)`는 `Map<String, String>` 기반 flat placeholder를 받는다. FreeMarker 렌더링에는 `List<FieldModel>`이 필요하므로 **이 API는 FreeMarker로 직접 연결할 수 없다**.

두 가지 옵션:

**옵션 A — 기존 API 유지, FreeMarker 범위 제한 (권장):**

`generateSource(layer, valuesJson)`는 기존 `String.replace()` 방식 유지. FreeMarker는 `orchestrateAuto()` 경로에서만 사용한다. 두 경로가 병행하며, `generateSource()`는 Claude 프롬프트 방식에서 계속 사용된다.

```text
Claude 프롬프트 경로: generateSource(layer, valuesJson) → String.replace() [유지]
auto 오케스트레이션: CrudModelFactory → CrudTemplateRenderer → FreeMarker [신규]
```

**옵션 B — generateSource() 내부를 FreeMarker로 교체:**

`valuesJson`의 flat Map으로는 컬럼 리스트를 표현할 수 없다. JSON 스키마를 확장하거나 별도 파라미터를 추가해야 하므로 API 파괴적 변경이 발생한다. 비권장.

> **채택: 옵션 A.** `generateSource()` API와 `String.replace()` 방식은 Phase 6까지 유지하고, FreeMarker는 `auto` 경로에서만 사용한다.

### 5.4 렌더러 선택 플래그 분리

`llmProvider: "auto"` 파라미터는 "내부 오케스트레이션 vs 프롬프트 반환"을 구분하는 용도다. 이것을 렌더러 선택 플래그로 겸용하면 의미가 섞인다.

롤백 및 전환 제어는 별도 애플리케이션 설정으로 분리한다:

`application.yaml`:
```yaml
app:
  codegen:
    template-engine: freemarker   # freemarker | text-block
```

> **`AppProperties` 확장 필요:** 현재 `AppProperties`에는 `codegen` 필드가 없다.
> `AppProperties.Codegen` inner class를 추가하거나 별도 `CodegenProperties` (`@ConfigurationProperties("app.codegen")`)를 신규 작성해야 한다.
> Phase 1 작업 범위에 포함된다 (8절 참조).

`CrudPromptBuilderTool.orchestrateAuto()` 내부에서 이 설정을 읽어 렌더러를 선택한다:

```text
template-engine: freemarker → CrudTemplateRenderer.render()
template-engine: text-block → 기존 CodeService.generateSource() + String.replace()
```

문제 발생 시 `application.yaml` 설정 하나만 바꾸면 즉시 롤백된다. `llmProvider` 파라미터 의미는 변경하지 않는다.

### 5.5 CodeTemplateTool의 최종 역할

`CodeTemplateTool.getCodeTemplate(layer)`는 모델 없이 템플릿 문자열을 반환하는 Tool이다. FreeMarker 렌더링에는 `CrudTemplateModel`이 필요하므로 이 메서드는 FreeMarker로 교체할 수 없다.

Phase 6 이후 최종 상태:

| 옵션 | 처리 방식 |
|---|---|
| **폐기 (권장)** | `auto` 경로가 안정화되면 `getCodeTemplate()`을 `@Deprecated` 처리 후 제거. Claude 프롬프트 방식 자체를 `auto`로 대체 |
| **`.ftl` 원문 반환으로 변경** | 렌더링 전 템플릿 원문을 디버깅/확인 목적으로 반환하는 Tool로 역할 재정의 |
| **유지** | Claude 프롬프트 경로(`llmProvider: "claude"`)가 계속 필요하면 기존 text block 유지 |

> **Phase 6 착수 전 별도 결정 필요.** 기본 방향은 `auto` 경로 안정화 후 `getCodeTemplate()` 폐기.

---

## 6. Phase 5: 테스트

### 6.1 테스트 파일

```text
src/test/java/com/krdevops/springai/service/
├── CrudTemplateRendererTest.java
├── CrudModelFactoryTest.java
└── CrudTemplateIntegrationTest.java

src/test/java/com/krdevops/springai/util/
└── CrudMappingUtilsTest.java
```

### 6.2 테스트 항목

| 테스트 | 검증 내용 |
|---|---|
| VO 렌더링 | `@Getter`, `@Setter`, `PaginationInfo` 포함, 필드 반복 정상 |
| Controller 렌더링 | `@Controller`, `@RequestMapping`, `PaginationInfo`, `ModelMap`, `EgovPropertyService` (CodeValidatorService.checkController() 요구 항목) |
| ServiceImpl 렌더링 | `EgovAbstractServiceImpl` 상속, `@Transactional` |
| Mapper XML 렌더링 | `<resultMap>`, 6개 SQL ID (`select{Domain}List`, `select{Domain}ListTotCnt`, `select{Domain}`, `insert{Domain}`, `update{Domain}`, `delete{Domain}`), `paginationInfo.firstRecordIndex` |
| CodeValidatorService 규칙 정합 | 생성된 SQL ID 패턴이 `CodeValidatorService.checkMapperXml()` 검증 규칙과 일치하는지 확인. 불일치 시 `CodeValidatorService` 규칙도 함께 수정 |
| JSP 렌더링 | `<c:url>`, EL 표현식이 `${...}`로 정상 출력 |
| Validation 분기 | `egovVersion: "5.0"` → `jakarta.validation`, `"4.3"` → `javax.validation` |
| 미치환 검증 | 렌더링 결과에 FreeMarker 변수 잔존 없음 |
| CodeValidator 통과 | 기존 `CodeValidatorService` 규칙 전수 통과 |
| Maven compile smoke | 생성된 소스가 컴파일 가능한지 확인 |
| CrudMappingUtils 단위 테스트 | `toCamelCase`, `mapJavaType`, `mapJdbcType`, `extractKoreanName` 각 메서드의 주요 입력값 검증 (생성 결과 전체에 영향) |
| CodegenProperties 바인딩 | `app.codegen.template-engine: freemarker` / `text-block` 설정이 정상 바인딩되고 렌더러 분기가 동작하는지 확인 |

---

## 7. Phase 6: 기존 text block 정리 (최종)

Phase 5 테스트가 안정화된 후 진행한다. **`CodeTemplateTool` 처리 방향은 5.5절에서 별도 결정** 후 착수한다.

1. `CodeTemplateTool` → 5.5절의 세 옵션(폐기 / `.ftl` 원문 반환으로 역할 변경 / 유지) 중 결정된 방향으로 처리
2. `CrudPromptBuilderService.PlaceholderValues` record 제거 (`auto` 경로 안정화 후)
3. `CrudPromptBuilderService.fetchColumns()` → `CrudSchemaQueryService`로 완전 이관 후 private 메서드 제거
4. `CodeService.generateSource()`의 `String.replace()` 로직 → Claude 프롬프트 경로도 `auto`로 통합 결정 시 제거

> 이 단계는 **별도 승인 후 진행**.

---

## 8. 작업 파일 영향 범위

```text
Phase 1  신규 8개 파일, 기존 2개 수정
         ├── build.gradle                                   (freemarker 의존성 1줄 추가)
         ├── application.yaml                               (app.codegen.template-engine 설정 추가)
         ├── model/crud/ColumnMeta.java                     (신규 — CrudSchemaQueryService 반환값 record)
         ├── model/crud/FieldModel.java                     (신규 — stringType 필드 포함)
         ├── model/crud/CrudTemplateModel.java              (신규)
         ├── model/crud/PkModel.java                        (신규)
         ├── exception/CrudTemplateRenderException.java     (신규 — 도메인 예외)
         ├── config/FreemarkerConfig.java                   (신규)
         ├── config/CodegenProperties.java                  (신규 — @ConfigurationProperties("app.codegen"))
         └── service/CrudTemplateRenderer.java              (신규 — layerKey→templateName 매핑 포함)

Phase 2  신규 11개 파일
         └── src/main/resources/templates/crud/*.ftl        (신규 ×11, 버전 폴더 없이 단일 디렉터리)

Phase 3  신규 3개 파일
         ├── service/CrudSchemaQueryService.java            (신규 — fetchColumns() JdbcTemplate 조회)
         ├── service/CrudModelFactory.java                  (신규)
         └── util/CrudMappingUtils.java                     (신규 — toCamelCase, mapJavaType, mapJdbcType, extractKoreanName 이관)

Phase 4  기존 1~2개 파일 수정
         ├── tools/CrudPromptBuilderTool.java               (orchestrateAuto 내부 교체, CodegenProperties 주입)
         └── service/CodeValidatorService.java              (SQL ID 패턴 등 검증 규칙이 FreeMarker 생성 결과와 불일치 시 수정 — 테스트 후 결정)

Phase 5  신규 4개 파일
         ├── test/.../CrudTemplateRendererTest.java         (신규)
         ├── test/.../CrudModelFactoryTest.java             (신규)
         ├── test/.../CrudTemplateIntegrationTest.java      (신규)
         └── test/.../CrudMappingUtilsTest.java             (신규)

Phase 6  별도 승인 후 진행 — CodeTemplateTool 역할 재결정 필요 (5.5절 참조)
         ├── tools/CodeTemplateTool.java                    (폐기 or 역할 변경)
         ├── service/CodeService.java                       (generateSource replace 로직 — auto 경로 제거 시)
         └── service/CrudPromptBuilderService.java          (PlaceholderValues 제거 — auto 경로 안정화 후)
```

---

## 9. 위험 요소 및 완화 방안

| 위험 | 대응 |
|---|---|
| JSP EL `${...}` 충돌 | JSP `.ftl`은 Phase 2 후반에 작성. `<#noparse>` 사용 규칙 확정 후 진행 |
| FreeMarker View resolver 자동 등록 | `spring-boot-starter-freemarker` 사용하지 않고 순수 `freemarker` 라이브러리만 추가 |
| Java record의 getter 접근 | FreeMarker 2.3.33은 Java record 지원. `model.domain()` → `${domain}` 자동 매핑 |
| 롤백 | `application.yaml`의 `app.codegen.template-engine: text-block`으로 즉시 기존 `String.replace()` 경로로 복귀. `llmProvider` 파라미터와 무관 |
| `.tpl` 파일과 혼동 | 확장자(`.tpl` vs `.ftl`)와 디렉터리(`egov/`, `security/` vs `crud/`)로 완전 분리 |
| FreeMarker와 기존 `.tpl`의 `${key}` 문법 충돌 | 확장자로 엔진 라우팅 명확 분리. `.tpl`은 기존 `CodeTemplateTool` + `CodeService.generateSource()` (String.replace), `.ftl`은 FreeMarker `Configuration`이 각각 담당 |
| 모델 설계 부실 | 문자열 덩어리 주입 금지. `List<FieldModel>`을 FreeMarker에 직접 전달하는 구조 강제 |
| 템플릿 복잡도 증가 | 비즈니스 판단은 Java 모델 생성 단계에서 처리. 템플릿은 표현과 단순 조건만 담당 |
| 검증 기준 불일치 | FreeMarker 전환 시 `CodeValidatorService` 검증 규칙과 생성 결과를 함께 정렬 |

---

## 10. 버전 폴더 분리 검토

### 10.1 검토 배경

Security 템플릿은 `egov43/`, `egov50/` 버전별 폴더로 분리되어 있다. CRUD `.ftl` 템플릿도 동일한 방식으로 버전 폴더를 만들어야 하는지 검토한다.

### 10.2 현재 Security 템플릿의 버전 폴더 구조 (참고)

```text
src/main/resources/templates/security/
├── common/          ← 양쪽 공용 (8개)
├── egov43/          ← 4.3 전용 (7개)
└── egov50/          ← 5.0 전용 (5개)
```

Security에서 버전 폴더 분리가 **필요한 이유**:

| 파일 | 4.3 vs 5.0 차이 수준 |
|---|---|
| `java-config.java.tpl` | **완전히 다른 코드** (158줄 vs 33줄, `WebSecurityConfigurerAdapter` vs `@Import` stub) |
| `context-security.xml.tpl` | **완전히 다른 스키마** (`egov-security` DSL vs plain `<beans>` POJO) |
| `user-details-service` | **파일 종류 자체가 다름** (Java 소스 vs 마크다운 안내문) |
| `role-hierarchy.java.tpl` | API 1줄 차이 (`setHierarchy()` vs `fromHierarchy()`) |

`java-config`과 `context-security`는 **구조적으로 완전히 다른 파일**이라 조건 분기로 합치면 가독성이 크게 떨어진다. Security의 버전 폴더 분리는 올바른 설계다.

### 10.3 CRUD 템플릿의 버전별 차이 분석

CRUD 생성에서 eGovFrame 버전에 따라 달라지는 부분을 전수 조사한 결과:

| 레이어 | 버전 차이 |
|---|---|
| **VO** | `javax.validation.constraints.*` vs `jakarta.validation.constraints.*` (import 3줄) |
| **Controller** | `javax.validation.Valid` vs `jakarta.validation.Valid` (현재 하드코딩 버그, FreeMarker 전환 시 수정 — 10.5절) |
| Service | 없음 |
| ServiceImpl | 없음 |
| Mapper | 없음 |
| MapperXml | 없음 |
| JSP (List/Detail/Regist/Updt) | 없음 |
| ControllerAdvice | 없음 |

**11개 템플릿 중 VO의 validation import와 Controller의 `@Valid` import만 다르다.** 두 차이 모두 `jakartaValidation` boolean 플래그 하나로 FreeMarker 조건 분기 처리가 가능하다.

### 10.4 결론: CRUD는 버전 폴더 불필요

버전 폴더를 분리하면:

```text
templates/crud/egov43/   ← 11개 파일
templates/crud/egov50/   ← 11개 파일 (9개는 동일, VO·Controller만 다름)
```

**22개 파일 중 18개가 중복**된다. 유지보수 비용만 늘어난다.

FreeMarker 조건 분기로 단일 파일에서 처리하는 것이 적합하다:

```ftl
<#-- vo.java.ftl — VO validation import 버전 분기 예시 -->
<#if jakartaValidation>
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
<#else>
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
</#if>
```

> Controller의 `@Valid` import 분기 예시는 **10.5절** 참조.

`CrudTemplateModel`의 `jakartaValidation` boolean 플래그 하나로 충분하다.

### 10.5 Controller `@Valid` import 버그 수정 포함

현재 `CodeTemplateTool.controllerTemplate()`은 `import jakarta.validation.Valid`를 하드코딩한다. eGovFrame 4.3에서는 `javax.validation.Valid`여야 한다.

FreeMarker 전환 시 `controller.java.ftl`에서 함께 수정:

```ftl
<#if jakartaValidation>
import jakarta.validation.Valid;
<#else>
import javax.validation.Valid;
</#if>
```

### 10.6 판단 기준 정리

| 기준 | 폴더 분리 권장 | 단일 파일 + 조건 분기 권장 |
|---|---|---|
| 파일 구조 자체가 다름 | Security (`java-config`, `context-security`) | — |
| import 1~2줄만 다름 | — | **CRUD** (validation import) |
| 파일 존재 여부가 다름 | Security (`user-details-service`) | — |
| 차이 없음 | — | `egov/*.tpl`, CRUD 나머지 10개 |

### 10.7 최종 폴더 전략

```text
src/main/resources/templates/
├── egov/*.tpl                    ← 유지 (버전 무관, 단순 치환)
├── security/
│   ├── common/*.tpl              ← 유지 (양쪽 공용)
│   ├── egov43/*.tpl              ← 유지 (구조적 차이 큼)
│   └── egov50/*.tpl              ← 유지 (구조적 차이 큼)
└── crud/*.ftl                    ← 신규 (버전 폴더 불필요, 조건 분기로 처리)
```

---

## 11. 기존 `.tpl` / `.md` 유지 범위

FreeMarker 전환 대상이 **아닌** 파일:

```text
templates/*.md                              → MCP Prompt 전용 (유지)
src/main/resources/templates/egov/*.tpl     → 프로젝트 초기화 파일 생성 (유지)
src/main/resources/templates/security/**/*.tpl → 보안 파일 생성 (유지)
```

FreeMarker 전환 대상:

```text
CodeTemplateTool.java 내 11개 text block
  → src/main/resources/templates/crud/*.ftl 로 분리 (버전 폴더 없이 단일 디렉터리)
```

---

## 12. 참고 문서

- [Mustache/FreeMarker 템플릿 도입 비교분석](Mustache_템플릿_도입_비교분석.md)
