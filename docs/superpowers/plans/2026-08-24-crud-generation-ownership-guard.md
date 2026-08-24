# CRUD 생성 Scope·Ownership·Revision 체인 연결 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** CRUD 생성 Apply 경로(`CodeServiceGenerationExecutor`)에 Region 단위 Ownership 보호(사람이
고친 부분 자동 보존, 진짜 충돌만 사람 검토)와 파일 단위 Revision drift 감지(기존 `ATOMIC_APPROVED`
재사용)를 연결하고, 켜기 전 이미 생성된 화면을 위한 부트스트랩 Tool을 추가한다.

**Architecture:** `RegionMarkerParser`가 Base(스냅샷)·Current(디스크)·New(렌더 결과) 세 버전을
Region 단위로 쪼개고, 이미 존재하는 8개 판정 클래스(`OwnershipConflictDetector` 등)로 3-way 비교
결과를 평가한 뒤, `ApprovedWriteConflictGuard`가 Apply 전 충돌을 막는다. Base는 신규
`CrudGenerationSnapshotStore`(MySQL, `ThymeleafProjectOperationRepository`와 동일한 패턴)에
저장한다. 이 전체는 `PipelineEvolutionProperties.usesV2Preview()`(모드 `V2_PREVIEW` 이상) 뒤에서만
동작하며, 현재 운영 기본값 `V2_APPLY`에서는 신규 Apply 경로가 활성화된다. 장애·롤백 시
`APP_PIPELINE_EVOLUTION_MODE=V2_PREVIEW` 또는 `DUAL_READ`를 명시하면 단계적으로 낮출 수 있다.

**Tech Stack:** Spring Boot 4.1 / Spring AI 2.0 MCP 서버, MySQL(JdbcTemplate, Flyway migration),
Jackson(record JSON 직렬화), JUnit 5 + AssertJ + Mockito, FreeMarker 템플릿.

## Global Constraints

- 패키지 루트는 `com.krdevops.springai`. 신규 클래스는 기존 서브패키지 관례를 따른다
  (`service.generation`, `mapper`, `tools.generation`).
- 이 기능 전체는 `PipelineEvolutionProperties.usesV2Preview()`가 `true`일 때만 동작해야 한다 —
  `false`(명시적으로 `DISABLED`/`OBSERVE`/`DUAL_READ`로 낮춘 경우)일 때는 기존 `BEST_EFFORT_COMPATIBILITY` 경로가
  1바이트도 안 바뀐 채로 그대로 동작해야 한다.
- 기존 호출자·테스트 호환을 위해, DI 대상 클래스에 새 협력자를 추가할 때는 `CrudGenerationPlanner`가
  이미 쓰는 패턴(전체 인자 `@Autowired` 생성자 + 이전 인자 개수의 호환 생성자가 안전한 기본값으로
  위임)을 그대로 따른다.
- 신규 MySQL 테이블은 `@PostConstruct` DDL이 아니라 Flyway migration(`src/main/resources/db/migration/`)
  으로만 만든다 — 이 프로젝트의 신규 테이블 목표 패턴(`ThymeleafProjectOperationRepository`의
  Javadoc 참고).
- `*IntegrationTest`로 끝나는 테스트 클래스는 실제 MySQL이 필요하며 `-Pci` 빌드에서 자동 제외된다
  (`build.gradle`의 `mapper/**/*IntegrationTest`/`service/**/*IntegrationTest` 규칙). 로컬에서
  `docker start egov-mysql` 실행 후 검증한다.
- MCP Tool을 추가/변경하면 `src/test/resources/mcp/tool-definitions-baseline.json`을 삭제하고
  `McpToolDefinitionSnapshotTest`의 `EXPECTED_TOOL_METHOD_COUNT`/`EXPECTED_TOOL_OBJECT_COUNT`
  상수를 갱신해야 한다(README의 MCP Tool baseline 절차).

---

## 파일 구조 개요

| 파일 | 역할 |
|---|---|
| `service/generation/RegionMarkerParser.java` (신규) | 4개 마커 문법을 공용 정규식으로 파싱, fail-safe UNKNOWN 강등 |
| `model/generation/GenerationOwnershipManifest.java` (수정) | `regionsFor(artifactPath)` 헬퍼 추가 |
| `service/generation/CrudGenerationOperationIdFactory.java` (신규) | 화면 식별용 결정적 operationId 계산 |
| `db/migration/V15__ai_crud_generation_snapshot.sql` (신규) | Base 스냅샷 테이블 |
| `service/generation/CrudGenerationSnapshotStore.java` (신규, interface) | Base 조회/저장 계약 |
| `mapper/CrudGenerationSnapshotRepository.java` (신규) | MySQL Adapter |
| `service/generation/pipeline/processor/CodeServiceGenerationExecutor.java` (수정) | Ownership-aware Apply 경로 연결 |
| `templates/crud/*.ftl` (수정, 10개 파일) | Region 마커 삽입 |
| `tools/generation/CrudGenerationSnapshotTool.java` (신규) | `adoptCurrentAsBaseline` MCP Tool |
| `config/McpConfig.java` (수정) | 신규 Tool 등록 |

---

### Task 1: RegionMarkerParser

**Files:**
- Create: `src/main/java/com/krdevops/springai/service/generation/RegionMarkerParser.java`
- Test: `src/test/java/com/krdevops/springai/service/generation/RegionMarkerParserTest.java`

**Interfaces:**
- Produces: `RegionMarkerParser.parse(String content) -> List<RegionMarkerParser.ParsedRegion>`,
  `RegionMarkerParser.ParsedRegion(String regionId, GenerationOwnershipManifest.RegionType regionType, String content, int startIndex, int endIndex)`,
  `RegionMarkerParser.hashOf(String regionContent) -> String`(sha256 hex).

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegionMarkerParserTest {

    @Test
    void 내용이_null이거나_비어있으면_빈_리스트를_반환한다() {
        assertThat(RegionMarkerParser.parse(null)).isEmpty();
        assertThat(RegionMarkerParser.parse("")).isEmpty();
    }

    @Test
    void 마커가_없으면_파일_전체를_generated_단일_Region으로_취급한다() {
        List<RegionMarkerParser.ParsedRegion> regions = RegionMarkerParser.parse("class Foo {}");

        assertThat(regions).hasSize(1);
        assertThat(regions.get(0).regionId()).isEqualTo("generated.file");
        assertThat(regions.get(0).regionType()).isEqualTo(GenerationOwnershipManifest.RegionType.GENERATED);
        assertThat(regions.get(0).content()).isEqualTo("class Foo {}");
    }

    @Test
    void Java_주석_마커로_감싼_구간을_Region으로_분리한다() {
        String content = """
                class ServiceImpl {
                    public void run() {
                        // @region:generated:body start
                        doStandardCrud();
                        // @region:generated:body end
                        // @region:protected:custom start
                        doCustomLogic();
                        // @region:protected:custom end
                    }
                }
                """;

        List<RegionMarkerParser.ParsedRegion> regions = RegionMarkerParser.parse(content);

        assertThat(regions).hasSize(2);
        assertThat(regions.get(0).regionId()).isEqualTo("body");
        assertThat(regions.get(0).regionType()).isEqualTo(GenerationOwnershipManifest.RegionType.GENERATED);
        assertThat(regions.get(0).content()).contains("doStandardCrud();");
        assertThat(regions.get(1).regionId()).isEqualTo("custom");
        assertThat(regions.get(1).regionType()).isEqualTo(GenerationOwnershipManifest.RegionType.PROTECTED);
        assertThat(regions.get(1).content()).contains("doCustomLogic();");
    }

    @Test
    void HTML과_JSP_주석_문법도_동일하게_인식한다() {
        String html = "<!-- @region:binding:table start -->x<!-- @region:binding:table end -->";
        String jsp = "<%-- @region:binding:table start --%>x<%-- @region:binding:table end --%>";

        assertThat(RegionMarkerParser.parse(html)).hasSize(1);
        assertThat(RegionMarkerParser.parse(jsp)).hasSize(1);
        assertThat(RegionMarkerParser.parse(html).get(0).regionType())
                .isEqualTo(GenerationOwnershipManifest.RegionType.BINDING);
    }

    @Test
    void 마커_사이의_비마커_구간은_어떤_Region으로도_파싱되지_않는다() {
        String content = "IMPORTS\n// @region:generated:a start\nA\n// @region:generated:a end\nGAP";

        List<RegionMarkerParser.ParsedRegion> regions = RegionMarkerParser.parse(content);

        assertThat(regions).hasSize(1);
        assertThat(regions.get(0).content()).isEqualTo("\nA\n");
    }

    @Test
    void end_마커가_없으면_파일_전체를_UNKNOWN으로_강등한다() {
        String content = "// @region:protected:custom start\nA";

        List<RegionMarkerParser.ParsedRegion> regions = RegionMarkerParser.parse(content);

        assertThat(regions).hasSize(1);
        assertThat(regions.get(0).regionId()).isEqualTo("unknown.file");
        assertThat(regions.get(0).regionType()).isEqualTo(GenerationOwnershipManifest.RegionType.UNKNOWN);
    }

    @Test
    void 같은_id가_중복되면_파일_전체를_UNKNOWN으로_강등한다() {
        String content = """
                // @region:generated:dup start
                A
                // @region:generated:dup end
                // @region:generated:dup start
                B
                // @region:generated:dup end
                """;

        List<RegionMarkerParser.ParsedRegion> regions = RegionMarkerParser.parse(content);

        assertThat(regions).hasSize(1);
        assertThat(regions.get(0).regionType()).isEqualTo(GenerationOwnershipManifest.RegionType.UNKNOWN);
    }

    @Test
    void 해시는_같은_내용에_대해_결정적이다() {
        assertThat(RegionMarkerParser.hashOf("same")).isEqualTo(RegionMarkerParser.hashOf("same"));
        assertThat(RegionMarkerParser.hashOf("a")).isNotEqualTo(RegionMarkerParser.hashOf("b"));
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "com.krdevops.springai.service.generation.RegionMarkerParserTest"`
Expected: FAIL — `RegionMarkerParser` 클래스가 없어 컴파일 실패

- [ ] **Step 3: 구현 작성**

```java
package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.generation.GenerationOwnershipManifest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 파일 내용에서 {@code @region:{type}:{id} start/end} 마커로 구분된 Region을 파싱한다. 감싸는
 * 주석 기호(// , &lt;!-- --&gt;, &lt;%-- --%&gt;)와 무관하게 마커 텍스트 자체만 인식하므로
 * Java/Thymeleaf HTML/JSP/MyBatis XML에 모두 그대로 쓸 수 있다.
 *
 * <p>마커 사이에 있지 않은 구간(예: import문, 마커가 아예 없는 파일의 일부)은 어떤 Region에도
 * 속하지 않는다 — New의 값을 그대로 유지한다는 점에서 암묵적으로 {@code GENERATED}와 동일하게
 * 취급된다.
 */
public final class RegionMarkerParser {

    private static final Pattern MARKER = Pattern.compile(
            "@region:([a-z]+):([A-Za-z0-9_.-]+)\\s+(start|end)");

    private RegionMarkerParser() {
    }

    public static List<ParsedRegion> parse(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        List<MarkerToken> tokens = findTokens(content);
        if (tokens.isEmpty()) {
            return List.of(new ParsedRegion("generated.file",
                    GenerationOwnershipManifest.RegionType.GENERATED, content, 0, content.length()));
        }
        List<ParsedRegion> regions = pairTokens(content, tokens);
        if (regions == null) {
            return List.of(new ParsedRegion("unknown.file",
                    GenerationOwnershipManifest.RegionType.UNKNOWN, content, 0, content.length()));
        }
        return regions;
    }

    public static String hashOf(String regionContent) {
        return ContentHashes.sha256Hex(regionContent.getBytes(StandardCharsets.UTF_8));
    }

    private static List<MarkerToken> findTokens(String content) {
        List<MarkerToken> tokens = new ArrayList<>();
        Matcher matcher = MARKER.matcher(content);
        while (matcher.find()) {
            tokens.add(new MarkerToken(matcher.group(1), matcher.group(2), matcher.group(3),
                    matcher.start(), matcher.end()));
        }
        return tokens;
    }

    /** 짝이 안 맞거나 id가 중복되면 {@code null}을 반환해 호출자가 파일 전체를 UNKNOWN으로 강등하게 한다. */
    private static List<ParsedRegion> pairTokens(String content, List<MarkerToken> tokens) {
        List<ParsedRegion> regions = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        MarkerToken pendingStart = null;
        for (MarkerToken token : tokens) {
            if ("start".equals(token.kind())) {
                if (pendingStart != null) {
                    return null;
                }
                pendingStart = token;
            } else {
                if (pendingStart == null || !pendingStart.id().equals(token.id())
                        || !pendingStart.type().equals(token.type())) {
                    return null;
                }
                if (!seenIds.add(pendingStart.id())) {
                    return null;
                }
                int interiorStart = pendingStart.markerEnd();
                int interiorEnd = findInteriorEnd(content, token.markerStart());
                regions.add(new ParsedRegion(pendingStart.id(), toRegionType(pendingStart.type()),
                        content.substring(interiorStart, interiorEnd), interiorStart, interiorEnd));
                pendingStart = null;
            }
        }
        return pendingStart != null ? null : regions;
    }

    /**
     * 실제 구현 중 발견(Task 1 리뷰): 끝 마커 앞의 주석 여는 기호(<code>// </code>,
     * <code>&lt;!-- </code>, <code>&lt;%-- </code>)를 그대로 두면 마커 사이 구간에 다음 Region의
     * 주석 기호까지 섞여 들어간다({@code "\nA\n// "}처럼) — {@code token.markerStart()}를 그대로
     * interiorEnd로 쓰면 안 된다. 끝 마커가 자기 줄에 혼자 있으면(줄 앞부분이 공백/주석기호뿐이면)
     * 그 줄 시작(직전 개행 바로 다음)까지 되감고, 마커 앞에 실제 코드가 있으면(인라인 마커) 되감지
     * 않는다.
     */
    private static int findInteriorEnd(String content, int markerStart) {
        int i = markerStart - 1;
        while (i >= 0 && content.charAt(i) != '\n') {
            char c = content.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                return markerStart; // 마커 앞에 실제 코드가 있음 — 인라인, 되감지 않는다.
            }
            i--;
        }
        return i >= 0 ? i + 1 : markerStart;
    }

    private static GenerationOwnershipManifest.RegionType toRegionType(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "generated" -> GenerationOwnershipManifest.RegionType.GENERATED;
            case "binding" -> GenerationOwnershipManifest.RegionType.BINDING;
            case "protected" -> GenerationOwnershipManifest.RegionType.PROTECTED;
            default -> GenerationOwnershipManifest.RegionType.UNKNOWN;
        };
    }

    private record MarkerToken(String type, String id, String kind, int markerStart, int markerEnd) {
    }

    /** 파싱된 Region 1개. {@code startIndex}/{@code endIndex}는 원본 문자열에서 마커를 제외한
     * 내부 콘텐츠의 오프셋이라 스플라이스(내용 치환)에 그대로 쓸 수 있다. */
    public record ParsedRegion(String regionId, GenerationOwnershipManifest.RegionType regionType,
                                String content, int startIndex, int endIndex) {
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.krdevops.springai.service.generation.RegionMarkerParserTest"`
Expected: PASS (8 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/krdevops/springai/service/generation/RegionMarkerParser.java \
        src/test/java/com/krdevops/springai/service/generation/RegionMarkerParserTest.java
git commit -m "feat: add RegionMarkerParser for CRUD generation ownership regions"
```

---

### Task 2: GenerationOwnershipManifest.regionsFor() 헬퍼

**Files:**
- Modify: `src/main/java/com/krdevops/springai/model/generation/GenerationOwnershipManifest.java`
- Modify: `src/test/java/com/krdevops/springai/model/generation/GenerationOwnershipManifestTest.java`

**Interfaces:**
- Produces: `GenerationOwnershipManifest.regionsFor(String artifactPath) -> List<Region>` (없으면 빈 리스트)

- [ ] **Step 1: 실패하는 테스트 추가**

`GenerationOwnershipManifestTest.java`에 추가:

```java
    @Test
    void regionsFor는_해당_artifactPath의_Region_목록을_반환하고_없으면_빈_리스트다() {
        var region = new GenerationOwnershipManifest.Region("controller.generated",
                GenerationOwnershipManifest.RegionType.GENERATED, "a".repeat(64));
        var artifact = new GenerationOwnershipManifest.ArtifactOwnership("src/Controller.java",
                List.of(region), GenerationOwnershipManifest.MergePolicy.REGENERATE, "springai");
        var manifest = GenerationOwnershipManifest.builder("ownership-2").artifacts(List.of(artifact)).build();

        assertThat(manifest.regionsFor("src/Controller.java")).containsExactly(region);
        assertThat(manifest.regionsFor("없는파일.java")).isEmpty();
    }
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "com.krdevops.springai.model.generation.GenerationOwnershipManifestTest"`
Expected: FAIL — `regionsFor` 메서드가 없어 컴파일 실패

- [ ] **Step 3: 구현 추가**

`GenerationOwnershipManifest.java`의 `hasValidContentHash()` 메서드 바로 아래에 추가:

```java
    /** {@code artifactPath}에 해당하는 Region 목록. 없으면 빈 리스트(3-way 비교에서 Base 없음으로 취급). */
    public List<Region> regionsFor(String artifactPath) {
        return artifacts.stream()
                .filter(artifact -> artifact.artifactPath().equals(artifactPath))
                .findFirst()
                .map(ArtifactOwnership::regions)
                .orElse(List.of());
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.krdevops.springai.model.generation.GenerationOwnershipManifestTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/krdevops/springai/model/generation/GenerationOwnershipManifest.java \
        src/test/java/com/krdevops/springai/model/generation/GenerationOwnershipManifestTest.java
git commit -m "feat: add GenerationOwnershipManifest.regionsFor lookup"
```

---

### Task 3: CrudGenerationOperationIdFactory

**Files:**
- Create: `src/main/java/com/krdevops/springai/service/generation/CrudGenerationOperationIdFactory.java`
- Test: `src/test/java/com/krdevops/springai/service/generation/CrudGenerationOperationIdFactoryTest.java`

**Interfaces:**
- Produces: `CrudGenerationOperationIdFactory.forScreen(String outputPath, String tableName, String viewType) -> String`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.krdevops.springai.service.generation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrudGenerationOperationIdFactoryTest {

    @Test
    void 같은_입력이면_항상_같은_operationId를_반환한다() {
        String first = CrudGenerationOperationIdFactory.forScreen("/tmp/proj", "LETTNEMPLYRINFO", "thymeleaf");
        String second = CrudGenerationOperationIdFactory.forScreen("/tmp/proj", "LETTNEMPLYRINFO", "thymeleaf");

        assertThat(first).isEqualTo(second);
        assertThat(first).matches("[a-f0-9]{64}");
    }

    @Test
    void tableName_대소문자는_같은_operationId를_만든다() {
        String lower = CrudGenerationOperationIdFactory.forScreen("/tmp/proj", "lettnemplyrinfo", "thymeleaf");
        String upper = CrudGenerationOperationIdFactory.forScreen("/tmp/proj", "LETTNEMPLYRINFO", "thymeleaf");

        assertThat(lower).isEqualTo(upper);
    }

    @Test
    void viewType이_다르면_다른_operationId다() {
        String jsp = CrudGenerationOperationIdFactory.forScreen("/tmp/proj", "EMP", "jsp");
        String thymeleaf = CrudGenerationOperationIdFactory.forScreen("/tmp/proj", "EMP", "thymeleaf");

        assertThat(jsp).isNotEqualTo(thymeleaf);
    }

    @Test
    void 필수값이_없으면_거부한다() {
        assertThatThrownBy(() -> CrudGenerationOperationIdFactory.forScreen(null, "EMP", "jsp"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.krdevops.springai.service.generation.CrudGenerationOperationIdFactoryTest"`
Expected: FAIL — 클래스 없음

- [ ] **Step 3: 구현 작성**

```java
package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.artifact.ContentHashes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

/** CRUD 생성 화면 하나(같은 outputPath·tableName·viewType 조합)를 식별하는 결정적 operationId. */
public final class CrudGenerationOperationIdFactory {

    private CrudGenerationOperationIdFactory() {
    }

    public static String forScreen(String outputPath, String tableName, String viewType) {
        if (outputPath == null || tableName == null || viewType == null) {
            throw new IllegalArgumentException("outputPath·tableName·viewType은 모두 필수입니다.");
        }
        String canonicalOutputPath = Path.of(outputPath).toAbsolutePath().normalize().toString();
        String canonical = canonicalOutputPath + "|" + tableName.trim().toUpperCase(Locale.ROOT)
                + "|" + viewType.trim().toLowerCase(Locale.ROOT);
        return ContentHashes.sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.krdevops.springai.service.generation.CrudGenerationOperationIdFactoryTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/krdevops/springai/service/generation/CrudGenerationOperationIdFactory.java \
        src/test/java/com/krdevops/springai/service/generation/CrudGenerationOperationIdFactoryTest.java
git commit -m "feat: add CrudGenerationOperationIdFactory"
```

---

### Task 4: Base 스냅샷 저장소 (Flyway + Store + Repository)

**Files:**
- Create: `src/main/resources/db/migration/V15__ai_crud_generation_snapshot.sql`
- Create: `src/main/java/com/krdevops/springai/service/generation/CrudGenerationSnapshotStore.java`
- Create: `src/main/java/com/krdevops/springai/mapper/CrudGenerationSnapshotRepository.java`
- Test: `src/test/java/com/krdevops/springai/mapper/CrudGenerationSnapshotRepositoryIntegrationTest.java`

**Interfaces:**
- Consumes: `GenerationOwnershipManifest`(Task 2) — Jackson으로 직렬화 가능해야 하므로 record 기본
  생성자 그대로 사용(추가 애노테이션 불필요, `ThymeleafOperationSnapshot`과 동일한 전례).
- Produces: `CrudGenerationSnapshotStore.findLatest(String operationId) -> Optional<GenerationOwnershipManifest>`,
  `CrudGenerationSnapshotStore.save(String operationId, GenerationOwnershipManifest manifest) -> void`.

- [ ] **Step 1: Flyway migration 작성**

```sql
-- Scope·Ownership·Revision 체인의 Base 스냅샷 저장소. GenerationOwnershipManifest를 그대로 JSON으로
-- 저장하고 PRIMARY KEY(OPERATION_ID, REVISION)로 compare-and-set을 얻는다
-- (AI_THYMELEAF_PROJECT_OPERATION과 동일한 패턴, V2__ai_thymeleaf_project_operation.sql 참고).

CREATE TABLE AI_CRUD_GENERATION_SNAPSHOT (
    OPERATION_ID   VARCHAR(64) NOT NULL,
    REVISION       INT         NOT NULL,
    SNAPSHOT_JSON  LONGTEXT    NOT NULL,
    CREATED_AT     DATETIME    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (OPERATION_ID, REVISION)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

파일 경로: `src/main/resources/db/migration/V15__ai_crud_generation_snapshot.sql`
(기존 최고 버전이 `V14__design_code_component_mapping.sql`이므로 V15가 다음 번호다.)

- [ ] **Step 2: interface 작성**

```java
package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.GenerationOwnershipManifest;

import java.util.Optional;

/** CRUD 생성 Region Ownership의 Base(직전 Apply 성공 시점) 스냅샷 저장소. */
public interface CrudGenerationSnapshotStore {

    Optional<GenerationOwnershipManifest> findLatest(String operationId);

    /** {@code writePort.apply()}가 APPLIED를 반환한 직후에만 호출한다 — revision은 자동 +1. */
    void save(String operationId, GenerationOwnershipManifest manifest);
}
```

- [ ] **Step 3: 실패하는 통합 테스트 작성** (`ThymeleafProjectOperationRepositoryIntegrationTest` 패턴 재사용)

```java
package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CrudGenerationSnapshotRepository}가 revision 기반 compare-and-set과 재시작 후 복구를
 * 실제로 제공하는지 실 MySQL로 검증한다. docker start egov-mysql 필요 — `-Pci`에서는 제외된다.
 */
class CrudGenerationSnapshotRepositoryIntegrationTest {

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private CrudGenerationSnapshotRepository newRepository() {
        return new CrudGenerationSnapshotRepository(jdbcTemplate, objectMapper);
    }

    private GenerationOwnershipManifest manifest(String suffix) {
        var region = new GenerationOwnershipManifest.Region("generated.file",
                GenerationOwnershipManifest.RegionType.GENERATED, "a".repeat(64));
        var artifact = new GenerationOwnershipManifest.ArtifactOwnership("Employer" + suffix + ".java",
                List.of(region), GenerationOwnershipManifest.MergePolicy.REGENERATE, "springai");
        return GenerationOwnershipManifest.builder("snap-" + suffix).artifacts(List.of(artifact)).build();
    }

    @Test
    void findLatest는_없으면_빈값을_반환한다() {
        String operationId = "crudop-missing-" + UUID.randomUUID();

        assertThat(newRepository().findLatest(operationId)).isEmpty();
    }

    @Test
    void save를_두번_하면_findLatest는_가장_최근_revision을_반환한다() {
        String operationId = "crudop-" + UUID.randomUUID();
        CrudGenerationSnapshotRepository repository = newRepository();

        repository.save(operationId, manifest("V1"));
        repository.save(operationId, manifest("V2"));

        GenerationOwnershipManifest latest = repository.findLatest(operationId).orElseThrow();
        assertThat(latest.artifacts().get(0).artifactPath()).isEqualTo("EmployerV2.java");
    }

    @Test
    void 재시작_시뮬레이션_새_Repository_인스턴스도_이전에_저장된_스냅샷을_본다() {
        String operationId = "crudop-restart-" + UUID.randomUUID();
        newRepository().save(operationId, manifest("Restart"));

        GenerationOwnershipManifest recovered = newRepository().findLatest(operationId).orElseThrow();

        assertThat(recovered.artifacts().get(0).artifactPath()).isEqualTo("EmployerRestart.java");
    }
}
```

- [ ] **Step 4: 테스트가 실패하는지 확인**

Run: `docker start egov-mysql && ./gradlew test --tests "com.krdevops.springai.mapper.CrudGenerationSnapshotRepositoryIntegrationTest"`
Expected: FAIL — `CrudGenerationSnapshotRepository` 클래스가 없어 컴파일 실패

- [ ] **Step 5: Repository 구현**

```java
package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import com.krdevops.springai.service.generation.CrudGenerationSnapshotStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link CrudGenerationSnapshotStore}의 MySQL Adapter.
 * {@code ThymeleafProjectOperationRepository}와 동일한 {@code PRIMARY KEY(OPERATION_ID, REVISION)}
 * compare-and-set 패턴을 그대로 따른다 — operationId 자체가 (outputPath, tableName, viewType)을
 * 결정적으로 인코딩하므로 별도 screen-index 테이블은 필요 없다.
 */
@Repository
public class CrudGenerationSnapshotRepository implements CrudGenerationSnapshotStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CrudGenerationSnapshotRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy()
                .findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public Optional<GenerationOwnershipManifest> findLatest(String operationId) {
        List<String> json = jdbcTemplate.queryForList("""
            SELECT SNAPSHOT_JSON FROM AI_CRUD_GENERATION_SNAPSHOT
             WHERE OPERATION_ID = ? ORDER BY REVISION DESC LIMIT 1
            """, String.class, operationId);
        return json.isEmpty() ? Optional.empty() : Optional.of(fromJson(json.get(0)));
    }

    @Override
    public void save(String operationId, GenerationOwnershipManifest manifest) {
        Integer maxRevision = jdbcTemplate.queryForObject("""
            SELECT COALESCE(MAX(REVISION), 0) FROM AI_CRUD_GENERATION_SNAPSHOT WHERE OPERATION_ID = ?
            """, Integer.class, operationId);
        int nextRevision = (maxRevision == null ? 0 : maxRevision) + 1;
        try {
            jdbcTemplate.update("""
                INSERT INTO AI_CRUD_GENERATION_SNAPSHOT (OPERATION_ID, REVISION, SNAPSHOT_JSON)
                VALUES (?, ?, ?)
                """, operationId, nextRevision, toJson(manifest));
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException(
                    "CRUD_GENERATION_SNAPSHOT_REVISION_CONFLICT: 동시 갱신으로 revision이 이미 존재합니다: "
                            + operationId + "/" + nextRevision, exception);
        }
    }

    private String toJson(GenerationOwnershipManifest manifest) {
        try {
            return objectMapper.writeValueAsString(manifest);
        } catch (Exception exception) {
            throw new IllegalStateException("GenerationOwnershipManifest JSON 직렬화 실패", exception);
        }
    }

    private GenerationOwnershipManifest fromJson(String json) {
        try {
            return objectMapper.readValue(json, GenerationOwnershipManifest.class);
        } catch (Exception exception) {
            throw new IllegalStateException("GenerationOwnershipManifest JSON 역직렬화 실패", exception);
        }
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew test --tests "com.krdevops.springai.mapper.CrudGenerationSnapshotRepositoryIntegrationTest"`
Expected: PASS (3 tests) — MySQL이 안 떠 있으면 연결 오류가 나므로 `docker start egov-mysql` 먼저 확인

- [ ] **Step 7: 커밋**

```bash
git add src/main/resources/db/migration/V15__ai_crud_generation_snapshot.sql \
        src/main/java/com/krdevops/springai/service/generation/CrudGenerationSnapshotStore.java \
        src/main/java/com/krdevops/springai/mapper/CrudGenerationSnapshotRepository.java \
        src/test/java/com/krdevops/springai/mapper/CrudGenerationSnapshotRepositoryIntegrationTest.java
git commit -m "feat: add CrudGenerationSnapshotStore MySQL-backed Base snapshot repository"
```

---

### Task 5: CodeServiceGenerationExecutor — 안전한 리팩터링(게이트만 추가, 동작 무변경)

**Files:**
- Modify: `src/main/java/com/krdevops/springai/service/generation/pipeline/processor/CodeServiceGenerationExecutor.java`

**Interfaces:**
- Consumes: `PipelineEvolutionProperties.usesV2Preview()`(기존)
- Produces: 기존 `execute(RenderedGenerationPlan)` 시그니처 유지. 내부적으로 `legacyExecute()`(기존
  로직 그대로)와 `ownershipAwareExecute()`(Task 6에서 실제 구현, 이번 Task에서는 `legacyExecute()`를
  그대로 호출하는 임시 패스스루)로 분리.

이 Task의 목적은 "동작을 절대 바꾸지 않으면서" 새 협력자를 주입할 수 있는 생성자 구조를 먼저
만드는 것이다 — 기존 `CodeServiceGenerationExecutorTest`(2-arg 생성자 사용)가 한 글자도 안 바뀐
채로 계속 통과해야 한다.

- [ ] **Step 1: 기존 테스트가 현재 통과하는지 먼저 확인(리팩터링 전 기준선)**

Run: `./gradlew test --tests "com.krdevops.springai.service.generation.pipeline.processor.CodeServiceGenerationExecutorTest"`
Expected: PASS (3 tests) — 지금부터 이 결과가 절대 바뀌면 안 된다.

- [ ] **Step 2: 클래스를 아래처럼 재구성**

`CodeServiceGenerationExecutor.java` 전체를 다음으로 교체한다(기존 import에 아래 5개를 추가하고,
클래스 본문을 교체):

```java
package com.krdevops.springai.service.generation.pipeline.processor;

import com.krdevops.springai.config.PipelineEvolutionProperties;
import com.krdevops.springai.model.write.ProjectChangeSet;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.generation.CrudGenerationSnapshotStore;
import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.RenderedFilePlan;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;
import com.krdevops.springai.service.generation.pipeline.GenerationExecutor;
import com.krdevops.springai.service.write.ApplyOutcome;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WP7 2차 pass/ARCH-0716: CRUD Pipeline 내 유일한 WRITE 어댑터. (Board/Master-Detail Orchestration
 * Service, Thymeleaf Layout 생성 등 이 Pipeline 밖의 다른 경로는 여전히
 * {@code codeService.saveGeneratedCode}를 직접 호출한다 — ARCH-0717/0718 별도 항목.)
 *
 * <p>{@code pipelineEvolutionProperties.usesV2Preview()}가 false면(명시적으로
 * {@code DISABLED}/{@code OBSERVE}/{@code DUAL_READ}로 낮춘 경우) 기존 {@link ProjectWritePolicy#BEST_EFFORT_COMPATIBILITY} 경로를 그대로
 * 쓴다. true(모드 {@code V2_PREVIEW} 이상, 현재 운영 기본값 {@code V2_APPLY})면 Region Ownership 3-way 비교 + Revision drift 감지가
 * 추가된 경로를 탄다 — 상세는 {@code docs/superpowers/specs/2026-08-24-crud-generation-ownership-guard-design.md}.
 */
@Slf4j
@Component
public class CodeServiceGenerationExecutor implements GenerationExecutor {

    private final CodeService codeService;
    private final ApprovedProjectWritePort writePort;
    private final PipelineEvolutionProperties pipelineEvolutionProperties;
    private final CrudGenerationSnapshotStore snapshotStore;

    @Autowired
    public CodeServiceGenerationExecutor(
            CodeService codeService,
            ApprovedProjectWritePort writePort,
            PipelineEvolutionProperties pipelineEvolutionProperties,
            CrudGenerationSnapshotStore snapshotStore) {
        this.codeService = codeService;
        this.writePort = writePort;
        this.pipelineEvolutionProperties = pipelineEvolutionProperties;
        this.snapshotStore = snapshotStore;
    }

    /** Ownership Guard 도입 전 2-arg 호출자·테스트 호환 — usesV2Preview()가 항상 false이므로 legacy 경로만 탄다. */
    public CodeServiceGenerationExecutor(CodeService codeService, ApprovedProjectWritePort writePort) {
        this(codeService, writePort, new PipelineEvolutionProperties(), null);
    }

    @Override
    public GenerationExecution execute(RenderedGenerationPlan plan) {
        if (!pipelineEvolutionProperties.usesV2Preview()) {
            return legacyExecute(plan);
        }
        return ownershipAwareExecute(plan);
    }

    /** 지금까지의 BEST_EFFORT_COMPATIBILITY 경로 — 원문 그대로 옮겨왔다. */
    private GenerationExecution legacyExecute(RenderedGenerationPlan plan) {
        List<RenderedFilePlan> toApply = plan.files().stream().filter(RenderedFilePlan::rendered).toList();

        Path outputRoot = null;
        Map<String, String> failureMessagesByRelative = Map.of();
        if (!toApply.isEmpty()) {
            String outputPath = plan.context().outputPath();
            codeService.validateOutputRoot(outputPath);
            outputRoot = Path.of(outputPath);

            List<ProjectChangeSet.FileChange> changes = new ArrayList<>();
            for (RenderedFilePlan file : toApply) {
                String relative = outputRoot.relativize(file.targetPath()).toString();
                changes.add(new ProjectChangeSet.FileChange(relative, null, file.source(), null));
            }

            ProjectChangeSet changeSet = new ProjectChangeSet(
                    outputPath, null, changes, List.of(), ProjectWritePolicy.BEST_EFFORT_COMPATIBILITY);
            failureMessagesByRelative = writePort.apply(changeSet).failureMessages();
        }

        List<RenderedFilePlan> succeeded = new ArrayList<>();
        List<GenerationFailure> failed = new ArrayList<>();
        for (RenderedFilePlan file : plan.files()) {
            if (!file.rendered()) {
                failed.add(file.renderFailure());
                continue;
            }
            String relative = outputRoot.relativize(file.targetPath()).toString();
            String failureMessage = failureMessagesByRelative.get(relative);
            if (failureMessage != null) {
                failed.add(new GenerationFailure(
                        file.layerKey(), file.displayName() + " — 파일 저장 실패: " + failureMessage));
                log.error("[pipeline] 저장 실패: {}", file.targetPath());
            } else {
                succeeded.add(file);
                log.info("[pipeline] 저장 완료: {}", file.targetPath());
            }
        }

        return new GenerationExecution(plan, succeeded, failed);
    }

    /** Task 6에서 실제 Ownership-aware 로직으로 교체한다. 지금은 legacy와 동일하게 동작한다. */
    private GenerationExecution ownershipAwareExecute(RenderedGenerationPlan plan) {
        return legacyExecute(plan);
    }
}
```

- [ ] **Step 3: 기존 테스트가 여전히 통과하는지 확인(회귀 없음 증명)**

Run: `./gradlew test --tests "com.krdevops.springai.service.generation.pipeline.processor.CodeServiceGenerationExecutorTest"`
Expected: PASS (3 tests) — Step 1과 완전히 동일한 결과여야 한다.

- [ ] **Step 4: CrudPipelineFixture도 새 협력자 없이 컴파일되는지 확인**

`CrudPipelineFixture.java`는 2-arg 생성자를 쓰므로 수정이 필요 없다. 컴파일만 확인한다.

Run: `./gradlew compileTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/krdevops/springai/service/generation/pipeline/processor/CodeServiceGenerationExecutor.java
git commit -m "refactor: split CodeServiceGenerationExecutor into legacy/ownership-aware paths (no behavior change)"
```

---

### Task 6: CodeServiceGenerationExecutor — Ownership-aware Apply 경로 실제 구현

**Files:**
- Modify: `src/main/java/com/krdevops/springai/service/generation/pipeline/processor/CodeServiceGenerationExecutor.java`
- Create: `src/test/java/com/krdevops/springai/service/generation/InMemoryCrudGenerationSnapshotStore.java` (테스트 지원)
- Create: `src/test/java/com/krdevops/springai/service/generation/pipeline/processor/CodeServiceGenerationExecutorOwnershipTest.java`

**Interfaces:**
- Consumes: `RegionMarkerParser`(Task 1), `GenerationOwnershipManifest.regionsFor()`(Task 2),
  `CrudGenerationOperationIdFactory`(Task 3), `CrudGenerationSnapshotStore`(Task 4),
  기존 `OwnershipConflictDetector`/`GeneratedRegionPreservationService`/`SemanticMergePlanService`/
  `ApprovedWriteConflictGuard`/`ThreeWayRegionComparison`.
- Produces: `ownershipAwareExecute()`의 실제 동작(이번 Task에서 `legacyExecute()` 위임을 대체).

**중요한 설계 결정(스펙 문서 참고)**: Base에는 있는데 New에서 사라진 Region이 `PROTECTED`/`BINDING`
이면 `ThreeWayRegionComparison`의 기본 판정과 무관하게 강제로 `BOTH_CHANGED`로 승격한다. Scope
불변식(실제 쓰기 대상 == 계획된 대상)은 이 아키텍처에서 항상 참인 자기 일관성 assertion이므로
`IllegalStateException`으로 표현하지 사람에게 보고할 실패로 만들지 않는다.

- [ ] **Step 1: 테스트 지원용 인메모리 스냅샷 저장소 작성**

```java
package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.GenerationOwnershipManifest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 실 MySQL 없이 Ownership 시나리오를 테스트하기 위한 인메모리 {@link CrudGenerationSnapshotStore}. */
public class InMemoryCrudGenerationSnapshotStore implements CrudGenerationSnapshotStore {

    private final Map<String, GenerationOwnershipManifest> latestByOperationId = new LinkedHashMap<>();

    @Override
    public Optional<GenerationOwnershipManifest> findLatest(String operationId) {
        return Optional.ofNullable(latestByOperationId.get(operationId));
    }

    @Override
    public void save(String operationId, GenerationOwnershipManifest manifest) {
        latestByOperationId.put(operationId, manifest);
    }
}
```

- [ ] **Step 2: 첫 번째 실패하는 시나리오 테스트 작성 — 최초 생성(Base·Current 없음)**

```java
package com.krdevops.springai.service.generation.pipeline.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.config.PipelineEvolutionProperties;
import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.generation.CrudGenerationOperationIdFactory;
import com.krdevops.springai.service.generation.InMemoryCrudGenerationSnapshotStore;
import com.krdevops.springai.service.generation.model.FileBlueprint;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.RenderedFilePlan;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * usesV2Preview()==true(모드 V2_PREVIEW 이상)일 때의 Ownership-aware Apply 경로를 검증한다.
 * writePort는 Mock이 아니라 실제 {@link FileSystemApprovedProjectWritePort}를 임시 디렉터리에
 * 대고 써서, 스플라이스·ATOMIC_APPROVED drift 감지까지 실제로 동작하는지 확인한다.
 */
class CodeServiceGenerationExecutorOwnershipTest {

    @TempDir
    Path outputRoot;

    private InMemoryCrudGenerationSnapshotStore snapshotStore;

    private CodeServiceGenerationExecutor executor() {
        CodeService codeService = new CodeService(egovProperties(outputRoot));
        FileSystemApprovedProjectWritePort writePort = new FileSystemApprovedProjectWritePort(
                new SafePathResolver(), new OperationHashFactory(new ObjectMapper()));
        PipelineEvolutionProperties properties = new PipelineEvolutionProperties();
        properties.setMode(PipelineEvolutionProperties.Mode.V2_PREVIEW);
        snapshotStore = new InMemoryCrudGenerationSnapshotStore();
        return new CodeServiceGenerationExecutor(codeService, writePort, properties, snapshotStore);
    }

    @Test
    void 최초_생성은_충돌없이_저장되고_스냅샷이_생긴다() {
        CodeServiceGenerationExecutor executor = executor();
        RenderedFilePlan file = RenderedFilePlan.rendered(
                new FileBlueprint("vo", "EmployerVO.java", outputRoot.resolve("EmployerVO.java"), null),
                "class EmployerVO {}");
        RenderedGenerationPlan plan = new RenderedGenerationPlan(
                context(outputRoot), List.of(file), List.of(), List.of());

        GenerationExecution execution = executor.execute(plan);

        assertThat(execution.failedFiles()).isEmpty();
        assertThat(execution.succeededFiles()).containsExactly(file);
        assertThat(outputRoot.resolve("EmployerVO.java")).hasContent("class EmployerVO {}");
        String operationId = CrudGenerationOperationIdFactory.forScreen(
                outputRoot.toString(), "EMP", "thymeleaf");
        assertThat(snapshotStore.findLatest(operationId)).isPresent();
    }

    private GenerationContext context(Path outputPath) {
        return new GenerationContext(
                "crud", "ebt", "EMP", "emp", "egovframework.let.emp",
                outputPath.toString(), "5.0", "thymeleaf", Map.of());
    }

    private EgovProperties egovProperties(Path basePath) {
        EgovProperties properties = new EgovProperties();
        EgovProperties.Output output = new EgovProperties.Output();
        output.setBasePath(basePath.toString());
        properties.setOutput(output);
        return properties;
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "com.krdevops.springai.service.generation.pipeline.processor.CodeServiceGenerationExecutorOwnershipTest"`
Expected: FAIL — 4-arg 생성자는 이미 있지만 `ownershipAwareExecute`가 아직 `legacyExecute`로
위임하므로 스냅샷이 저장되지 않아 `snapshotStore.findLatest(...)`가 비어 있음(assertion 실패)

- [ ] **Step 4: `ownershipAwareExecute` 실제 구현으로 교체**

`CodeServiceGenerationExecutor.java`의 import 블록에 추가:

```java
import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import com.krdevops.springai.model.generation.ThreeWayRegionComparison;
import com.krdevops.springai.service.generation.CrudGenerationOperationIdFactory;
import com.krdevops.springai.service.generation.GeneratedRegionPreservationService;
import com.krdevops.springai.service.generation.OwnershipConflictDetector;
import com.krdevops.springai.service.generation.RegionMarkerParser;
import com.krdevops.springai.service.generation.ApprovedWriteConflictGuard;
import com.krdevops.springai.service.generation.SemanticMergePlanService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
```

생성자에 2개 협력자를 더 추가한다. `OwnershipConflictDetector`/`GeneratedRegionPreservationService`는
Executor가 직접 쓰지 않는다 — `SemanticMergePlanService.preview()`가 내부에서 이미 이 둘을 호출해
`conflictRegionIds`/`preservedRegionIds`까지 결과에 담아 돌려주므로, Executor가 따로 다시 호출하면
같은 계산을 중복하는 것이다(코드 검토 중 발견해 뺐다):

```java
    private final CodeService codeService;
    private final ApprovedProjectWritePort writePort;
    private final PipelineEvolutionProperties pipelineEvolutionProperties;
    private final CrudGenerationSnapshotStore snapshotStore;
    private final SemanticMergePlanService semanticMergePlanService;
    private final ApprovedWriteConflictGuard approvedWriteConflictGuard;

    @Autowired
    public CodeServiceGenerationExecutor(
            CodeService codeService,
            ApprovedProjectWritePort writePort,
            PipelineEvolutionProperties pipelineEvolutionProperties,
            CrudGenerationSnapshotStore snapshotStore,
            SemanticMergePlanService semanticMergePlanService,
            ApprovedWriteConflictGuard approvedWriteConflictGuard) {
        this.codeService = codeService;
        this.writePort = writePort;
        this.pipelineEvolutionProperties = pipelineEvolutionProperties;
        this.snapshotStore = snapshotStore;
        this.semanticMergePlanService = semanticMergePlanService;
        this.approvedWriteConflictGuard = approvedWriteConflictGuard;
    }

    /** Ownership Guard 도입 전 4-arg 호출자·테스트 호환. */
    public CodeServiceGenerationExecutor(
            CodeService codeService, ApprovedProjectWritePort writePort,
            PipelineEvolutionProperties pipelineEvolutionProperties, CrudGenerationSnapshotStore snapshotStore) {
        this(codeService, writePort, pipelineEvolutionProperties, snapshotStore,
                new SemanticMergePlanService(new OwnershipConflictDetector(), new GeneratedRegionPreservationService()),
                new ApprovedWriteConflictGuard());
    }

    /** Ownership Guard 도입 전 2-arg 호출자·테스트 호환 — usesV2Preview()가 항상 false이므로 legacy 경로만 탄다. */
    public CodeServiceGenerationExecutor(CodeService codeService, ApprovedProjectWritePort writePort) {
        this(codeService, writePort, new PipelineEvolutionProperties(), null);
    }
```

`ownershipAwareExecute()`를 아래로 교체:

```java
    private GenerationExecution ownershipAwareExecute(RenderedGenerationPlan plan) {
        List<RenderedFilePlan> toApply = plan.files().stream().filter(RenderedFilePlan::rendered).toList();
        List<GenerationFailure> renderFailures = plan.files().stream()
                .filter(file -> !file.rendered()).map(RenderedFilePlan::renderFailure).toList();
        if (toApply.isEmpty()) {
            return new GenerationExecution(plan, List.of(), renderFailures);
        }

        String outputPath = plan.context().outputPath();
        codeService.validateOutputRoot(outputPath);
        Path outputRoot = Path.of(outputPath);

        String operationId = CrudGenerationOperationIdFactory.forScreen(
                outputPath, plan.context().tableName(), plan.context().viewType());
        GenerationOwnershipManifest base = snapshotStore.findLatest(operationId).orElse(null);

        Map<String, String> currentContentByPath = new LinkedHashMap<>();
        Map<String, String> currentHashByPath = new LinkedHashMap<>();
        Map<String, List<RegionMarkerParser.ParsedRegion>> newRegionsByPath = new LinkedHashMap<>();
        List<ThreeWayRegionComparison> allComparisons = new ArrayList<>();
        Map<String, GenerationOwnershipManifest.RegionType> regionTypes = new LinkedHashMap<>();

        for (RenderedFilePlan file : toApply) {
            String relative = outputRoot.relativize(file.targetPath()).toString();
            String current = readIfExists(file.targetPath());
            currentContentByPath.put(relative, current);
            currentHashByPath.put(relative, current == null ? "MISSING" : sha256(current));

            List<RegionMarkerParser.ParsedRegion> newRegions = RegionMarkerParser.parse(file.source());
            List<RegionMarkerParser.ParsedRegion> currentRegions = RegionMarkerParser.parse(current);
            newRegionsByPath.put(relative, newRegions);
            List<GenerationOwnershipManifest.Region> baseRegions =
                    base == null ? List.of() : base.regionsFor(relative);

            Set<String> regionIds = new LinkedHashSet<>();
            newRegions.forEach(region -> regionIds.add(region.regionId()));
            currentRegions.forEach(region -> regionIds.add(region.regionId()));
            baseRegions.forEach(region -> regionIds.add(region.regionId()));

            for (String regionId : regionIds) {
                var baseRegion = baseRegions.stream()
                        .filter(region -> region.regionId().equals(regionId)).findFirst().orElse(null);
                var newRegion = newRegions.stream()
                        .filter(region -> region.regionId().equals(regionId)).findFirst().orElse(null);
                var currentRegion = currentRegions.stream()
                        .filter(region -> region.regionId().equals(regionId)).findFirst().orElse(null);

                String baseHash = baseRegion == null ? null : baseRegion.contentHash();
                String newHash = newRegion == null ? null : RegionMarkerParser.hashOf(newRegion.content());
                String currentHash = currentRegion == null ? null : RegionMarkerParser.hashOf(currentRegion.content());
                GenerationOwnershipManifest.RegionType type = newRegion != null ? newRegion.regionType()
                        : (baseRegion != null ? baseRegion.regionType() : GenerationOwnershipManifest.RegionType.UNKNOWN);

                String comparisonId = relative + "::" + regionId;
                ThreeWayRegionComparison comparison =
                        ThreeWayRegionComparison.compare(comparisonId, baseHash, currentHash, newHash);
                boolean protectedRegionVanished = newRegion == null && baseRegion != null
                        && (type == GenerationOwnershipManifest.RegionType.PROTECTED
                            || type == GenerationOwnershipManifest.RegionType.BINDING)
                        && comparison.status() != ThreeWayRegionComparison.ChangeStatus.BOTH_CHANGED;
                if (protectedRegionVanished) {
                    comparison = new ThreeWayRegionComparison(comparisonId, baseHash, currentHash, newHash,
                            ThreeWayRegionComparison.ChangeStatus.BOTH_CHANGED);
                }
                regionTypes.put(comparisonId, type);
                allComparisons.add(comparison);
            }
        }

        var mergePlan = semanticMergePlanService.preview(allComparisons, regionTypes);
        try {
            approvedWriteConflictGuard.requireApplyAllowed(mergePlan);
        } catch (ApprovedWriteConflictGuard.ApplyConflictBlockedException conflict) {
            List<GenerationFailure> failures = new ArrayList<>(renderFailures);
            failures.add(new GenerationFailure("ownership-guard",
                    "Region 소유권 충돌로 Apply 중단: " + conflict.plan().conflictRegionIds()));
            return new GenerationExecution(plan, List.of(), failures);
        }

        // SemanticMergePlan.preservedRegionIds()는 이미 GeneratedRegionPreservationService.plan()이
        // 계산한 결과이므로, Executor가 그 서비스를 따로 다시 호출할 필요가 없다.
        Set<String> preservedComparisonIds = new LinkedHashSet<>(mergePlan.preservedRegionIds());

        List<ProjectChangeSet.FileChange> changes = new ArrayList<>();
        Map<String, List<RegionMarkerParser.ParsedRegion>> finalRegionsByPath = new LinkedHashMap<>();
        // 실제 쓰기 대상은 여기서 정해지는 toApply뿐이다 — Planner→Renderer→Executor 사이에 다른
        // 파일이 끼어드는 단계가 없으므로, scopeManifest 없이도 이 목록 자체가 이번 호출의 Scope다.
        for (RenderedFilePlan file : toApply) {
            String relative = outputRoot.relativize(file.targetPath()).toString();
            String spliced = spliceRegions(file.source(), newRegionsByPath.get(relative),
                    relative, currentContentByPath.get(relative), preservedComparisonIds);
            finalRegionsByPath.put(relative, RegionMarkerParser.parse(spliced));
            changes.add(new ProjectChangeSet.FileChange(relative, currentHashByPath.get(relative),
                    spliced, sha256(spliced)));
        }

        createDirectoriesIfMissing(outputRoot);
        ApplyOutcome outcome = writePort.apply(new ProjectChangeSet(
                outputPath, null, changes, List.of(), ProjectWritePolicy.ATOMIC_APPROVED));

        if (outcome.status() == ApplyOutcome.Status.CONFLICT) {
            List<GenerationFailure> failures = new ArrayList<>(renderFailures);
            failures.add(new GenerationFailure("write-guard",
                    "동시 수정으로 파일 Revision이 어긋나 Apply 중단: " + outcome.conflictingPaths()));
            return new GenerationExecution(plan, List.of(), failures);
        }
        if (outcome.status() != ApplyOutcome.Status.APPLIED) {
            List<GenerationFailure> failures = new ArrayList<>(renderFailures);
            failures.add(new GenerationFailure("write-guard",
                    "Apply 실패(" + outcome.status() + "): " + outcome.failureDetail()));
            return new GenerationExecution(plan, List.of(), failures);
        }

        snapshotStore.save(operationId, buildOwnershipManifest(operationId, finalRegionsByPath));
        return new GenerationExecution(plan, toApply, renderFailures);
    }

    /** PRESERVE 대상 Region만 New 콘텐츠에서 Current 내용으로 치환한다. */
    private String spliceRegions(String newContent, List<RegionMarkerParser.ParsedRegion> newRegions,
            String relative, String currentContent, Set<String> preservedComparisonIds) {
        if (currentContent == null) {
            return newContent;
        }
        List<RegionMarkerParser.ParsedRegion> currentRegions = RegionMarkerParser.parse(currentContent);
        StringBuilder result = new StringBuilder(newContent);
        // 뒤에서부터 치환해야 앞쪽 오프셋이 밀리지 않는다.
        for (int i = newRegions.size() - 1; i >= 0; i--) {
            RegionMarkerParser.ParsedRegion region = newRegions.get(i);
            String comparisonId = relative + "::" + region.regionId();
            if (!preservedComparisonIds.contains(comparisonId)) {
                continue;
            }
            currentRegions.stream()
                    .filter(current -> current.regionId().equals(region.regionId()))
                    .findFirst()
                    .ifPresent(currentRegion -> result.replace(
                            region.startIndex(), region.endIndex(), currentRegion.content()));
        }
        return result.toString();
    }

    private GenerationOwnershipManifest buildOwnershipManifest(
            String operationId, Map<String, List<RegionMarkerParser.ParsedRegion>> regionsByPath) {
        List<GenerationOwnershipManifest.ArtifactOwnership> artifacts = new ArrayList<>();
        for (var entry : regionsByPath.entrySet()) {
            List<GenerationOwnershipManifest.Region> regions = entry.getValue().stream()
                    .map(region -> new GenerationOwnershipManifest.Region(
                            region.regionId(), region.regionType(), RegionMarkerParser.hashOf(region.content())))
                    .toList();
            artifacts.add(new GenerationOwnershipManifest.ArtifactOwnership(
                    entry.getKey(), regions, GenerationOwnershipManifest.MergePolicy.REGENERATE, "springai"));
        }
        return GenerationOwnershipManifest.builder(operationId).artifacts(artifacts).build();
    }

    private String readIfExists(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("기존 파일 읽기 실패: " + path, exception);
        }
    }

    private void createDirectoriesIfMissing(Path root) {
        if (Files.exists(root)) {
            return;
        }
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("출력 루트 생성 실패: " + root, exception);
        }
    }

    private String sha256(String text) {
        return ContentHashes.sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }
```

필요한 `import java.nio.file.Files;`도 추가한다(기존 `java.nio.file.Path`와 같은 블록).

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "com.krdevops.springai.service.generation.pipeline.processor.CodeServiceGenerationExecutorOwnershipTest"`
Expected: PASS (1 test)

- [ ] **Step 6: 나머지 4개 시나리오 테스트 추가** — 같은 테스트 클래스에 순서대로 추가하고, 매번
  `./gradlew test --tests "...CodeServiceGenerationExecutorOwnershipTest"`로 통과를 확인한다.

```java
    @Test
    void 재생성인데_아무것도_안_바뀌면_충돌없이_저장된다() {
        CodeServiceGenerationExecutor executor = executor();
        String content = "// @region:generated:body start\nA\n// @region:generated:body end\n";
        RenderedFilePlan first = RenderedFilePlan.rendered(
                new FileBlueprint("vo", "EmployerVO.java", outputRoot.resolve("EmployerVO.java"), null), content);
        executor.execute(new RenderedGenerationPlan(context(outputRoot), List.of(first), List.of(), List.of()));

        RenderedFilePlan second = RenderedFilePlan.rendered(
                new FileBlueprint("vo", "EmployerVO.java", outputRoot.resolve("EmployerVO.java"), null), content);
        GenerationExecution execution = executor.execute(
                new RenderedGenerationPlan(context(outputRoot), List.of(second), List.of(), List.of()));

        assertThat(execution.failedFiles()).isEmpty();
        assertThat(outputRoot.resolve("EmployerVO.java")).hasContent(content);
    }

    @Test
    void 생성기만_바뀐_generated_Region은_자동_반영된다() {
        CodeServiceGenerationExecutor executor = executor();
        String v1 = "// @region:generated:body start\nOLD\n// @region:generated:body end\n";
        executor.execute(new RenderedGenerationPlan(context(outputRoot), List.of(RenderedFilePlan.rendered(
                new FileBlueprint("vo", "EmployerVO.java", outputRoot.resolve("EmployerVO.java"), null), v1)),
                List.of(), List.of()));

        String v2 = "// @region:generated:body start\nNEW\n// @region:generated:body end\n";
        GenerationExecution execution = executor.execute(new RenderedGenerationPlan(context(outputRoot),
                List.of(RenderedFilePlan.rendered(new FileBlueprint("vo", "EmployerVO.java",
                        outputRoot.resolve("EmployerVO.java"), null), v2)), List.of(), List.of()));

        assertThat(execution.failedFiles()).isEmpty();
        assertThat(outputRoot.resolve("EmployerVO.java")).hasContent(v2);
    }

    @Test
    void 사람만_고친_protected_Region은_자동_보존되고_New에_스플라이스된다() {
        CodeServiceGenerationExecutor executor = executor();
        String v1 = "HEADER\n// @region:protected:custom start\nORIGINAL\n// @region:protected:custom end\nFOOTER";
        Path target = outputRoot.resolve("EmployerServiceImpl.java");
        executor.execute(new RenderedGenerationPlan(context(outputRoot),
                List.of(RenderedFilePlan.rendered(new FileBlueprint("serviceImpl", "EmployerServiceImpl.java",
                        target, null), v1)), List.of(), List.of()));

        // 사람이 protected 구간만 손으로 고쳤다고 가정 — 디스크 파일을 직접 편집한다.
        Files.writeString(target,
                "HEADER\n// @region:protected:custom start\nHAND_EDITED\n// @region:protected:custom end\nFOOTER");

        // 생성기는 HEADER/FOOTER는 그대로 두고 protected 구간만 다시 ORIGINAL로 만들려 한다(재생성 재현).
        String v2 = "HEADER\n// @region:protected:custom start\nORIGINAL\n// @region:protected:custom end\nFOOTER";
        GenerationExecution execution = executor.execute(new RenderedGenerationPlan(context(outputRoot),
                List.of(RenderedFilePlan.rendered(new FileBlueprint("serviceImpl", "EmployerServiceImpl.java",
                        target, null), v2)), List.of(), List.of()));

        assertThat(execution.failedFiles()).isEmpty();
        assertThat(target).content().contains("HAND_EDITED").doesNotContain("ORIGINAL");
    }

    @Test
    void 둘_다_바뀐_protected_Region은_Apply_전체를_중단시키고_파일을_안_쓴다() {
        CodeServiceGenerationExecutor executor = executor();
        String v1 = "// @region:protected:custom start\nORIGINAL\n// @region:protected:custom end\n";
        Path target = outputRoot.resolve("EmployerServiceImpl.java");
        executor.execute(new RenderedGenerationPlan(context(outputRoot),
                List.of(RenderedFilePlan.rendered(new FileBlueprint("serviceImpl", "EmployerServiceImpl.java",
                        target, null), v1)), List.of(), List.of()));

        Files.writeString(target, "// @region:protected:custom start\nHAND_EDITED\n// @region:protected:custom end\n");
        String v2 = "// @region:protected:custom start\nGENERATOR_CHANGED\n// @region:protected:custom end\n";
        RenderedFilePlan otherFile = RenderedFilePlan.rendered(
                new FileBlueprint("vo", "EmployerVO.java", outputRoot.resolve("EmployerVO.java"), null), "class X{}");
        GenerationExecution execution = executor.execute(new RenderedGenerationPlan(context(outputRoot),
                List.of(RenderedFilePlan.rendered(new FileBlueprint("serviceImpl", "EmployerServiceImpl.java",
                        target, null), v2), otherFile), List.of(), List.of()));

        assertThat(execution.succeededFiles()).isEmpty(); // 전부 아니면 전무 — otherFile도 안 써짐
        assertThat(execution.failedFiles()).hasSize(1);
        assertThat(execution.failedFiles().get(0).source()).isEqualTo("ownership-guard");
        assertThat(outputRoot.resolve("EmployerVO.java")).doesNotExist();
    }

    @Test
    void ATOMIC_APPROVED_CONFLICT_상태는_write_guard_실패로_변환되고_스냅샷을_갱신하지_않는다() {
        // 진짜 동시성 경합은 결정론적으로 재현하기 어렵다 — writePort를 Mock으로 대체해 CONFLICT를
        // 직접 유도한다. Current를 다시 읽어 drift를 감지하는 것 자체는 이미
        // FileSystemApprovedProjectWritePortTest가 실제 파일로 검증하므로 여기서 중복하지 않는다.
        // 이 테스트는 오직 "execute()가 CONFLICT를 write-guard 실패로 정확히 옮기는지"만 본다.
        ApprovedProjectWritePort writePort = org.mockito.Mockito.mock(ApprovedProjectWritePort.class);
        org.mockito.BDDMockito.given(writePort.apply(org.mockito.ArgumentMatchers.any()))
                .willReturn(ApplyOutcome.conflict(List.of("EmployerVO.java")));
        CodeService codeService = new CodeService(egovProperties(outputRoot));
        PipelineEvolutionProperties properties = new PipelineEvolutionProperties();
        properties.setMode(PipelineEvolutionProperties.Mode.V2_PREVIEW);
        InMemoryCrudGenerationSnapshotStore snapshotStore = new InMemoryCrudGenerationSnapshotStore();
        CodeServiceGenerationExecutor executor = new CodeServiceGenerationExecutor(
                codeService, writePort, properties, snapshotStore,
                new SemanticMergePlanService(new OwnershipConflictDetector(), new GeneratedRegionPreservationService()),
                new ApprovedWriteConflictGuard());

        RenderedFilePlan file = RenderedFilePlan.rendered(
                new FileBlueprint("vo", "EmployerVO.java", outputRoot.resolve("EmployerVO.java"), null), "class X{}");
        GenerationExecution execution = executor.execute(
                new RenderedGenerationPlan(context(outputRoot), List.of(file), List.of(), List.of()));

        assertThat(execution.succeededFiles()).isEmpty();
        assertThat(execution.failedFiles()).hasSize(1);
        assertThat(execution.failedFiles().get(0).source()).isEqualTo("write-guard");
        String operationId = CrudGenerationOperationIdFactory.forScreen(
                outputRoot.toString(), "EMP", "thymeleaf");
        assertThat(snapshotStore.findLatest(operationId)).isEmpty();
    }
```

이 테스트를 추가하려면 파일 상단 import 블록에 다음을 더한다:

```java
import com.krdevops.springai.service.generation.CrudGenerationOperationIdFactory;
import com.krdevops.springai.service.generation.GeneratedRegionPreservationService;
import com.krdevops.springai.service.generation.OwnershipConflictDetector;
import com.krdevops.springai.service.generation.SemanticMergePlanService;
import com.krdevops.springai.service.generation.ApprovedWriteConflictGuard;
import com.krdevops.springai.service.write.ApplyOutcome;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
```

- [ ] **Step 8: 전체 테스트 통과 확인 (Task 5 기준선도 함께)**

Run: `./gradlew test --tests "com.krdevops.springai.service.generation.pipeline.processor.*"`
Expected: PASS — `CodeServiceGenerationExecutorTest`(기존 3개) + `CodeServiceGenerationExecutorOwnershipTest`(신규 6개) 전부

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/krdevops/springai/service/generation/pipeline/processor/CodeServiceGenerationExecutor.java \
        src/test/java/com/krdevops/springai/service/generation/InMemoryCrudGenerationSnapshotStore.java \
        src/test/java/com/krdevops/springai/service/generation/pipeline/processor/CodeServiceGenerationExecutorOwnershipTest.java
git commit -m "feat: wire Region ownership 3-way comparison and ATOMIC_APPROVED revision guard into CodeServiceGenerationExecutor"
```

---

### Task 7: FreeMarker 템플릿에 Region 마커 삽입

**Files:**
- Modify: `src/main/resources/templates/crud/vo.java.ftl`
- Modify: `src/main/resources/templates/crud/mapper.xml.ftl`
- Modify: `src/main/resources/templates/crud/controller.java.ftl`
- Modify: `src/main/resources/templates/crud/service.java.ftl`
- Modify: `src/main/resources/templates/crud/service-impl.java.ftl`
- Modify: `src/main/resources/templates/crud/jsp-list.jsp.ftl`
- Modify: `src/main/resources/templates/crud/jsp-detail.jsp.ftl`
- Modify: `src/main/resources/templates/crud/jsp-regist.jsp.ftl`
- Modify: `src/main/resources/templates/crud/jsp-updt.jsp.ftl`
- Modify: `src/main/resources/templates/crud/thymeleaf-list.html.ftl`
- Modify: `src/main/resources/templates/crud/thymeleaf-detail.html.ftl`
- Modify: `src/main/resources/templates/crud/thymeleaf-regist.html.ftl`
- Modify: `src/main/resources/templates/crud/thymeleaf-updt.html.ftl`
- Test: `src/test/java/com/krdevops/springai/service/CrudTemplateRendererTest.java`(기존 파일에 마커
  존재 검증 케이스 추가)

이 Task는 각 템플릿의 **끝부분**에 스펙의 "6개 레이어 초기 마커 배치" 표를 그대로 적용한다 — 표준
CRUD 출력은 전부 `generated`(마커로 감싸지 않아도 파서가 파일 전체를 `generated.file` 단일
Region으로 취급하므로 실제로는 "커스텀 영역"에만 마커가 필요하다).

- [ ] **Step 1: VO/Service(interface) — 마커 불필요 확인만**

`vo.java.ftl`과 `service.java.ftl`은 전체가 `generated` 취급되면 되므로(스펙의 표) **마커를 추가하지
않는다** — `RegionMarkerParser.parse()`가 마커 없는 파일을 자동으로 `generated.file` Region 1개로
처리하기 때문이다. 이 두 파일은 이번 Task에서 변경하지 않는다(변경 없음 자체가 이 Step의 산출물).

- [ ] **Step 2: Mapper XML — resultMap/기본 SQL을 binding으로 감싸기**

`mapper.xml.ftl`에서 `<resultMap ...>...</resultMap>` 블록 전체를 다음과 같이 감싼다(파일 앞부분,
정확한 줄 번호는 현재 템플릿을 열어 `<resultMap` 태그를 찾아 그 위/아래에 삽입):

```xml
    <!-- @region:binding:resultMap start -->
    <resultMap id="${r"${domain}"}ResultMap" type="...">
        ...
    </resultMap>
    <!-- @region:binding:resultMap end -->
```

(각 `<select>`/`<insert>`/`<update>`/`<delete>` 표준 CRUD SQL 블록은 마커로 감싸지 않는다 — 파일
전체가 마커 밖에서는 암묵적으로 `generated`로 취급되므로 표준 SQL은 그대로 둬도 된다. resultMap만
`binding`으로 명시하는 이유는 "스키마 변경 시에만 갱신, 그 외엔 보존"이라는 스펙의 정책 차이를
실제로 구분하기 위해서다.)

- [ ] **Step 3: Controller — 파일 하단에 protected 커스텀 액션 자리 추가**

`controller.java.ftl`의 마지막 메서드 뒤, 클래스 닫는 `}` 바로 앞에 추가:

```java
    // @region:protected:customActions start
    // 이 위치에 추가한 커스텀 @RequestMapping 메서드는 재생성 시 보존됩니다.
    // @region:protected:customActions end
```

- [ ] **Step 4: ServiceImpl — 표준 CRUD 메서드 본문은 generated, 비즈니스 로직 자리는 protected**

`service-impl.java.ftl`의 각 표준 CRUD 메서드(`insert`/`update`/`delete` 등) 본문 안, 실제 DAO 호출
직전에 추가:

```java
    public void insert${r"${domain}"}(${r"${domain}"}VO vo) {
        // @region:protected:beforeInsert start
        // 저장 전 커스텀 검증/가공 로직을 이 안에 작성하면 재생성 시 보존됩니다.
        // @region:protected:beforeInsert end
        ${r"${domain}"}Mapper.insert${r"${domain}"}(vo);
    }
```

(다른 CRUD 메서드에도 동일 패턴으로 `beforeUpdate`/`beforeDelete` 등 메서드별 고유 id를 부여한다 —
같은 파일 안에서 id가 중복되면 Task 1의 fail-safe가 파일 전체를 `UNKNOWN`으로 강등시킨다는 점을
반드시 지킨다.)

- [ ] **Step 5: JSP 4종 — 화면 하단에 protected 커스텀 영역 추가**

`jsp-list.jsp.ftl`/`jsp-detail.jsp.ftl`/`jsp-regist.jsp.ftl`/`jsp-updt.jsp.ftl` 각각의 `</body>`
직전에 추가:

```jsp
<%-- @region:protected:customSection start --%>
<%-- 이 위치의 커스텀 마크업/스크립트는 재생성 시 보존됩니다. --%>
<%-- @region:protected:customSection end --%>
```

- [ ] **Step 6: Thymeleaf HTML 4종 — 화면 하단에 protected 커스텀 영역 추가**

`thymeleaf-list.html.ftl`/`thymeleaf-detail.html.ftl`/`thymeleaf-regist.html.ftl`/
`thymeleaf-updt.html.ftl` 각각의 메인 콘텐츠 영역 마지막 `</div>` 직전에 추가:

```html
<!-- @region:protected:customSection start -->
<!-- 이 위치의 커스텀 마크업은 재생성 시 보존됩니다. -->
<!-- @region:protected:customSection end -->
```

(`-body.html.ftl`/`-standalone.html.ftl` 변형은 이번 Task 범위 밖이다 — 필요해지면 후속 작업으로
동일 패턴을 반복 적용한다.)

- [ ] **Step 7: 기존 렌더러 테스트가 여전히 통과하는지 확인**

Run: `./gradlew test --tests "com.krdevops.springai.service.CrudTemplateRendererTest"`
Expected: PASS — 마커는 각 언어의 주석 문법 안에 있으므로 컴파일·렌더링 결과에 실질적 영향이 없다.

- [ ] **Step 8: 마커가 실제로 파싱되는지 확인하는 회귀 테스트 추가**

`CrudTemplateRendererTest.java`에 추가(기존 렌더 결과를 얻는 방식은 파일 상단의 다른 테스트를
참고해 동일하게 렌더링한 뒤):

```java
    @Test
    void ServiceImpl_렌더_결과에는_protected_Region_마커가_있다() {
        String rendered = renderer.renderByLayerKey("serviceImpl", model()); // 기존 헬퍼 재사용
        var regions = com.krdevops.springai.service.generation.RegionMarkerParser.parse(rendered);
        assertThat(regions).anyMatch(region ->
                region.regionType() == com.krdevops.springai.model.generation.GenerationOwnershipManifest.RegionType.PROTECTED);
    }
```

(`renderer`/`model()` 헬퍼 이름은 기존 `CrudTemplateRendererTest`에 이미 있는 것을 그대로 쓴다 —
파일을 열어 정확한 헬퍼 메서드명을 확인하고 맞춘다.)

- [ ] **Step 9: 커밋**

```bash
git add src/main/resources/templates/crud/mapper.xml.ftl \
        src/main/resources/templates/crud/controller.java.ftl \
        src/main/resources/templates/crud/service-impl.java.ftl \
        src/main/resources/templates/crud/jsp-list.jsp.ftl \
        src/main/resources/templates/crud/jsp-detail.jsp.ftl \
        src/main/resources/templates/crud/jsp-regist.jsp.ftl \
        src/main/resources/templates/crud/jsp-updt.jsp.ftl \
        src/main/resources/templates/crud/thymeleaf-list.html.ftl \
        src/main/resources/templates/crud/thymeleaf-detail.html.ftl \
        src/main/resources/templates/crud/thymeleaf-regist.html.ftl \
        src/main/resources/templates/crud/thymeleaf-updt.html.ftl \
        src/test/java/com/krdevops/springai/service/CrudTemplateRendererTest.java
git commit -m "feat: insert Region ownership markers into CRUD FreeMarker templates"
```

---

### Task 8: adoptCurrentAsBaseline MCP Tool

**Files:**
- Create: `src/main/java/com/krdevops/springai/tools/generation/CrudGenerationSnapshotTool.java`
- Modify: `src/main/java/com/krdevops/springai/config/McpConfig.java`
- Modify: `src/test/resources/mcp/tool-definitions-baseline.json` (삭제 후 재생성)
- Modify: `src/test/java/com/krdevops/springai/config/McpToolDefinitionSnapshotTest.java`
- Test: `src/test/java/com/krdevops/springai/tools/generation/CrudGenerationSnapshotToolTest.java`

**Interfaces:**
- Consumes: `CrudGenerationPlanner.plan(CrudGenerationCommand)`(기존, 파일 저장 없이 Blueprint만
  돌려줌), `RegionMarkerParser`(Task 1), `CrudGenerationOperationIdFactory`(Task 3),
  `CrudGenerationSnapshotStore`(Task 4).
- Produces: `@Tool adoptCurrentAsBaseline(database, tableName, domain, packageName, outputPath, viewType) -> String`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.krdevops.springai.tools.generation;

import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import com.krdevops.springai.service.generation.CrudGenerationOperationIdFactory;
import com.krdevops.springai.service.generation.InMemoryCrudGenerationSnapshotStore;
import com.krdevops.springai.service.generation.crud.CrudGenerationPlan;
import com.krdevops.springai.service.generation.crud.CrudGenerationPlanner;
import com.krdevops.springai.service.generation.model.FileBlueprint;
import com.krdevops.springai.service.generation.model.GenerationBlueprint;
import com.krdevops.springai.service.generation.model.GenerationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

class CrudGenerationSnapshotToolTest {

    @TempDir
    Path outputRoot;

    @Test
    void 디스크에_있는_현재_내용을_그대로_스냅샷으로_등록하고_파일은_건드리지_않는다() throws Exception {
        Path voFile = outputRoot.resolve("EmployerVO.java");
        Files.writeString(voFile, "class EmployerVO { /* hand edited long ago */ }");

        CrudGenerationPlanner planner = Mockito.mock(CrudGenerationPlanner.class);
        GenerationBlueprint blueprint = new GenerationBlueprint(
                new GenerationContext("crud", "ebt", "EMP", "emp", "egovframework.let.emp",
                        outputRoot.toString(), "5.0", "thymeleaf", Map.of()),
                List.of(new FileBlueprint("vo", "EmployerVO.java", voFile, null)),
                List.of(), List.of());
        given(planner.plan(org.mockito.ArgumentMatchers.any())).willReturn(
                new CrudGenerationPlan(blueprint, null, CrudProgramMetadata.fallback(null), null, List.of()));

        InMemoryCrudGenerationSnapshotStore snapshotStore = new InMemoryCrudGenerationSnapshotStore();
        CrudGenerationSnapshotTool tool = new CrudGenerationSnapshotTool(planner, snapshotStore);

        String result = tool.adoptCurrentAsBaseline(
                "ebt", "EMP", "emp", "egovframework.let.emp", outputRoot.toString(), "thymeleaf");

        assertThat(result).contains("채택");
        assertThat(voFile).hasContent("class EmployerVO { /* hand edited long ago */ }"); // 파일 미변경
        String operationId = CrudGenerationOperationIdFactory.forScreen(
                outputRoot.toString(), "EMP", "thymeleaf");
        GenerationOwnershipManifest adopted = snapshotStore.findLatest(operationId).orElseThrow();
        assertThat(adopted.artifacts()).hasSize(1);
        assertThat(adopted.artifacts().get(0).artifactPath()).isEqualTo("EmployerVO.java");
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "com.krdevops.springai.tools.generation.CrudGenerationSnapshotToolTest"`
Expected: FAIL — `CrudGenerationSnapshotTool` 클래스가 없어 컴파일 실패

- [ ] **Step 3: Tool 구현**

```java
package com.krdevops.springai.tools.generation;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;
import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import com.krdevops.springai.service.generation.CrudGenerationOperationIdFactory;
import com.krdevops.springai.service.generation.CrudGenerationSnapshotStore;
import com.krdevops.springai.service.generation.RegionMarkerParser;
import com.krdevops.springai.service.generation.crud.CrudGenerationCommand;
import com.krdevops.springai.service.generation.crud.CrudGenerationPlan;
import com.krdevops.springai.service.generation.crud.CrudGenerationPlanner;
import com.krdevops.springai.service.generation.model.DesignContextReference;
import com.krdevops.springai.service.generation.model.FileBlueprint;
import com.krdevops.springai.service.generation.model.LayoutOptions;
import com.krdevops.springai.service.generation.model.ProgramMetadataOverrides;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 5축 Ownership 체인을 켜기 전 이미 생성돼 있던 화면을 위한 부트스트랩 Tool. 파일을 전혀 쓰지
 * 않고, 지금 디스크에 있는 내용을 그대로 신뢰해 다음 재생성부터 쓸 Base 스냅샷만 등록한다.
 */
@Component
@RequiredArgsConstructor
public class CrudGenerationSnapshotTool {

    private final CrudGenerationPlanner crudGenerationPlanner;
    private final CrudGenerationSnapshotStore snapshotStore;

    @McpToolRisk(McpToolRiskLevel.DB_WRITE)
    @Tool(description = """
            5축 파이프라인 Ownership 보호(app.pipeline-evolution.mode=V2_PREVIEW 이상)를 켜기 전
            이미 생성돼 있던 CRUD 화면을 위한 부트스트랩 Tool입니다.
            지금 디스크에 있는 파일 내용을 그대로 신뢰해 다음 재생성부터 비교 기준(Base)으로 쓸
            스냅샷만 등록하며, 파일은 전혀 건드리지 않습니다.
            이 Tool을 호출하지 않고 기존 화면을 재생성하면, Base가 없어 Current와 New가 조금이라도
            다르면 충돌(BOTH_CHANGED)로 판정되어 사람 검토가 필요합니다.
            database/tableName/domain/packageName/outputPath/viewType은 원래 이 화면을 생성할 때
            썼던 값과 동일해야 합니다.
            """)
    public String adoptCurrentAsBaseline(String database, String tableName, String domain,
            String packageName, String outputPath, String viewType) {
        CrudGenerationCommand command = new CrudGenerationCommand(
                database, tableName, domain, packageName, Path.of(outputPath),
                "auto", "5.0", viewType, LayoutOptions.empty(), ProgramMetadataOverrides.empty(),
                DesignContextReference.empty());
        CrudGenerationPlan plan = crudGenerationPlanner.plan(command);
        if (plan.failed()) {
            return "채택 실패 — 화면 계획을 만들 수 없습니다: " + plan.failure().validationSummary();
        }

        List<GenerationOwnershipManifest.ArtifactOwnership> artifacts = new ArrayList<>();
        int adoptedFileCount = 0;
        for (FileBlueprint file : plan.blueprint().files()) {
            String current = readIfExists(file.targetPath());
            if (current == null) {
                continue; // 아직 생성된 적 없는 파일 — 채택할 대상이 없다.
            }
            List<RegionMarkerParser.ParsedRegion> regions = RegionMarkerParser.parse(current);
            List<GenerationOwnershipManifest.Region> manifestRegions = regions.stream()
                    .map(region -> new GenerationOwnershipManifest.Region(
                            region.regionId(), region.regionType(), RegionMarkerParser.hashOf(region.content())))
                    .toList();
            String relative = Path.of(outputPath).relativize(file.targetPath()).toString();
            artifacts.add(new GenerationOwnershipManifest.ArtifactOwnership(
                    relative, manifestRegions, GenerationOwnershipManifest.MergePolicy.REGENERATE, "springai"));
            adoptedFileCount++;
        }

        String operationId = CrudGenerationOperationIdFactory.forScreen(outputPath, tableName, viewType);
        GenerationOwnershipManifest manifest = GenerationOwnershipManifest.builder(operationId)
                .artifacts(artifacts).build();
        snapshotStore.save(operationId, manifest);

        return "채택 완료 — " + adoptedFileCount + "개 파일의 현재 내용을 다음 재생성의 Base로 등록했습니다. "
                + "파일은 변경되지 않았습니다.";
    }

    private String readIfExists(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("기존 파일 읽기 실패: " + path, exception);
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.krdevops.springai.tools.generation.CrudGenerationSnapshotToolTest"`
Expected: PASS (1 test)

- [ ] **Step 5: McpConfig에 등록**

`McpConfig.java` import 블록에 추가:

```java
import com.krdevops.springai.tools.generation.CrudGenerationSnapshotTool;
```

`allToolCallbacks(...)` 메서드 파라미터 목록 마지막(`OperationalTelemetry telemetry` 바로 앞)에 추가:

```java
            CrudGenerationSnapshotTool crudGenerationSnapshotTool,
```

`.toolObjects(...)` 호출 목록 마지막(`thymeleafBaselineApprovalTool` 바로 뒤)에 추가:

```java
                        thymeleafBaselineApprovalTool, crudGenerationSnapshotTool)
```

- [ ] **Step 6: MCP Tool baseline 갱신**

```bash
rm src/test/resources/mcp/tool-definitions-baseline.json
```

`McpToolDefinitionSnapshotTest.java`에서 상수를 갱신:

```java
    private static final int EXPECTED_TOOL_METHOD_COUNT = 102; // 101 → 102: adoptCurrentAsBaseline 추가
    ...
    private static final int EXPECTED_TOOL_OBJECT_COUNT = 37; // 36 → 37: CrudGenerationSnapshotTool 추가
```

- [ ] **Step 7: baseline 재생성 + 전체 회귀 확인**

Run: `./gradlew test --tests "com.krdevops.springai.config.McpToolDefinitionSnapshotTest"`
Expected: 최초 실행은 PASS(baseline 파일을 새로 생성). 바로 다시 실행해 두 번째도 PASS하는지 확인한다.

```bash
./gradlew test --tests "com.krdevops.springai.config.McpToolDefinitionSnapshotTest"
```

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/krdevops/springai/tools/generation/CrudGenerationSnapshotTool.java \
        src/main/java/com/krdevops/springai/config/McpConfig.java \
        src/test/java/com/krdevops/springai/tools/generation/CrudGenerationSnapshotToolTest.java \
        src/test/java/com/krdevops/springai/config/McpToolDefinitionSnapshotTest.java \
        src/test/resources/mcp/tool-definitions-baseline.json
git commit -m "feat: add adoptCurrentAsBaseline MCP tool for pre-existing screen bootstrap"
```

---

## 전체 완료 확인

- [x] `./gradlew test` 전체 통과 및 `./gradlew bootJar` 성공을 확인했다.
- [x] `app.pipeline-evolution.mode` 기본값을 `V2_APPLY`로 전환했다. 장애·롤백 시
      `APP_PIPELINE_EVOLUTION_MODE=V2_PREVIEW` 또는 `DUAL_READ`를 명시할 수 있다.
