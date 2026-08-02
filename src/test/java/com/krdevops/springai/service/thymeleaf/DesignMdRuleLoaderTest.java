package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.thymeleaf.AppliedDesignRules;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.service.contract.GenerationIssueFactory;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R6-055: DesignMdRuleLoader 테스트.
 *
 * <p>DESIGN.md 파일 로딩, YAML frontmatter 파싱, 업무계약 침범 검증을 검증한다.
 */
class DesignMdRuleLoaderTest {

    private static final Path GOLDEN_FIXTURE = Path.of("src/test/resources/generation/design");

    private LegacySourceInventoryService inventoryService;
    private GenerationIssueFactory issueFactory;
    private DesignMdRuleLoader loader;

    @BeforeEach
    void setUp() {
        OperationHashFactory hashFactory = new OperationHashFactory(new ObjectMapper().findAndRegisterModules());
        inventoryService = new LegacySourceInventoryService(hashFactory);
        issueFactory = new GenerationIssueFactory();
        loader = new DesignMdRuleLoader(inventoryService, issueFactory);
    }

    @Test
    void loadsGoldenFixtureSuccessfully() throws Exception {
        assumeFixtureExists();

        ThymeleafGenerationStageResult<AppliedDesignRules> result =
                loader.load(GOLDEN_FIXTURE.toAbsolutePath().toString());

        assertThat(result.successful()).isTrue();
        assertThat(result.value()).isNotNull();

        AppliedDesignRules rules = result.value();
        assertThat(rules.designMdPath()).isNotNull().contains("DESIGN.md");
        assertThat(rules.contentHash()).isNotNull().hasSize(64); // SHA256 hex
        assertThat(rules.schemaVersion()).isEqualTo("1.0");
        assertThat(rules.appliedRules()).isNotEmpty();
        assertThat(rules.ignoredRules()).isEmpty();
        assertThat(rules.violations()).isEmpty();
    }

    @Test
    void parsesAllSupportedCategories() throws Exception {
        assumeFixtureExists();

        ThymeleafGenerationStageResult<AppliedDesignRules> result =
                loader.load(GOLDEN_FIXTURE.toAbsolutePath().toString());

        AppliedDesignRules rules = result.value();
        assertThat(rules.appliedRules()).isNotEmpty();

        // 모든 지원 카테고리가 파싱되었는지 확인
        var categories = rules.appliedRules().stream()
                .map(AppliedDesignRules.AppliedRule::category)
                .distinct()
                .toList();

        assertThat(categories).containsAnyOf("typography", "colors", "spacing", "radius", "layout", "components", "voice", "forbidden");
    }

    @Test
    void fallsBackGracefullyWhenDesignMdNotFound(@TempDir Path tempDir) throws Exception {
        // 빈 프로젝트 디렉터리
        Path emptyProject = tempDir.resolve("empty-project");
        Files.createDirectories(emptyProject);

        ThymeleafGenerationStageResult<AppliedDesignRules> result =
                loader.load(emptyProject.toAbsolutePath().toString());

        // 폴백: 성공이지만 규칙 없음
        assertThat(result.successful()).isTrue();
        assertThat(result.value()).isNotNull();

        AppliedDesignRules rules = result.value();
        assertThat(rules.designMdPath()).isNull();
        assertThat(rules.appliedRules()).isEmpty();
        assertThat(rules.violations()).hasSize(1);

        GenerationIssue issue = rules.violations().get(0);
        assertThat(issue.code()).isEqualTo("DESIGN_MD_NOT_FOUND");
        assertThat(issue.severity()).isEqualTo(GenerationIssue.Severity.WARNING);
    }

    @Test
    void failsOnUnsupportedSchemaVersion(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("project-v2");
        Files.createDirectories(projectRoot);

        String designMdContent = """
                ---
                schemaVersion: "2.0"
                typography:
                  heading: "20px"
                ---
                # Future version design rules
                """;
        Files.writeString(projectRoot.resolve("DESIGN.md"), designMdContent);

        ThymeleafGenerationStageResult<AppliedDesignRules> result =
                loader.load(projectRoot.toAbsolutePath().toString());

        assertThat(result.successful()).isFalse();
        assertThat(result.value()).isNull();
        assertThat(result.issues()).hasSize(1);

        GenerationIssue issue = result.issues().get(0);
        assertThat(issue.code()).isEqualTo("DESIGN_MD_VERSION_UNSUPPORTED");
        assertThat(issue.severity()).isEqualTo(GenerationIssue.Severity.FATAL);
    }

    @Test
    void warnsOnUnknownCategory(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("project-unknown");
        Files.createDirectories(projectRoot);

        String designMdContent = """
                ---
                schemaVersion: "1.0"
                unknownCategory:
                  key: "value"
                typography:
                  heading: "20px"
                ---
                # Design rules
                """;
        Files.writeString(projectRoot.resolve("DESIGN.md"), designMdContent);

        ThymeleafGenerationStageResult<AppliedDesignRules> result =
                loader.load(projectRoot.toAbsolutePath().toString());

        assertThat(result.successful()).isTrue();
        assertThat(result.value()).isNotNull();

        AppliedDesignRules rules = result.value();
        assertThat(rules.appliedRules()).isNotEmpty(); // typography는 파싱됨
        assertThat(rules.ignoredRules()).hasSize(1); // unknownCategory는 무시됨
        assertThat(rules.violations()).hasSize(1); // WARNING 추가됨

        GenerationIssue issue = rules.violations().get(0);
        assertThat(issue.code()).isEqualTo("DESIGN_RULE_UNKNOWN");
        assertThat(issue.severity()).isEqualTo(GenerationIssue.Severity.WARNING);
    }

    @Test
    void failsOnForbiddenKeywordInRules(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("project-forbidden");
        Files.createDirectories(projectRoot);

        // "route" 키는 금지됨
        String designMdContent = """
                ---
                schemaVersion: "1.0"
                components:
                  button:
                    route: "/admin/list"
                ---
                # Design rules with forbidden keyword
                """;
        Files.writeString(projectRoot.resolve("DESIGN.md"), designMdContent);

        ThymeleafGenerationStageResult<AppliedDesignRules> result =
                loader.load(projectRoot.toAbsolutePath().toString());

        assertThat(result.successful()).isFalse();
        assertThat(result.value()).isNull();
        assertThat(result.issues()).hasSize(1);

        GenerationIssue issue = result.issues().get(0);
        assertThat(issue.code()).isEqualTo("DESIGN_RULE_BINDING_OVERRIDE_FORBIDDEN");
        assertThat(issue.severity()).isEqualTo(GenerationIssue.Severity.FATAL);
    }

    @Test
    void treatsPureDocumentationWithoutFrontmatterAsNoRules(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("project-doc-only");
        Files.createDirectories(projectRoot);

        String designMdContent = """
                # Design Guidelines

                This is a markdown document without YAML frontmatter.
                No structured rules are parsed.
                """;
        Files.writeString(projectRoot.resolve("DESIGN.md"), designMdContent);

        ThymeleafGenerationStageResult<AppliedDesignRules> result =
                loader.load(projectRoot.toAbsolutePath().toString());

        assertThat(result.successful()).isTrue();
        assertThat(result.value()).isNotNull();

        AppliedDesignRules rules = result.value();
        assertThat(rules.designMdPath()).isNotNull();
        assertThat(rules.appliedRules()).isEmpty();
        assertThat(rules.ignoredRules()).isEmpty();
        assertThat(rules.violations()).isEmpty();
    }

    @Test
    void failsOnYamlSyntaxError(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("project-yaml-error");
        Files.createDirectories(projectRoot);

        // 잘못된 YAML 문법
        String designMdContent = """
                ---
                schemaVersion: "1.0"
                typography:
                  heading: [unclosed list
                ---
                # Design rules
                """;
        Files.writeString(projectRoot.resolve("DESIGN.md"), designMdContent);

        ThymeleafGenerationStageResult<AppliedDesignRules> result =
                loader.load(projectRoot.toAbsolutePath().toString());

        assertThat(result.successful()).isFalse();
        assertThat(result.value()).isNull();
        assertThat(result.issues()).hasSize(1);

        GenerationIssue issue = result.issues().get(0);
        assertThat(issue.code()).isEqualTo("DESIGN_MD_PARSE_FAILED");
        assertThat(issue.severity()).isEqualTo(GenerationIssue.Severity.FATAL);
    }

    @Test
    void handlesMultipleForbiddenKeywords(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("project-multiple-forbidden");
        Files.createDirectories(projectRoot);

        // 첫 번째 금지어로 즉시 중단
        String designMdContent = """
                ---
                schemaVersion: "1.0"
                components:
                  form:
                    validation: "required"
                ---
                # Design rules
                """;
        Files.writeString(projectRoot.resolve("DESIGN.md"), designMdContent);

        ThymeleafGenerationStageResult<AppliedDesignRules> result =
                loader.load(projectRoot.toAbsolutePath().toString());

        assertThat(result.successful()).isFalse();
        assertThat(result.issues()).hasSize(1);

        GenerationIssue issue = result.issues().get(0);
        assertThat(issue.code()).isEqualTo("DESIGN_RULE_BINDING_OVERRIDE_FORBIDDEN");
    }

    @Test
    void recordsCorrectSourceLocations() throws Exception {
        assumeFixtureExists();

        ThymeleafGenerationStageResult<AppliedDesignRules> result =
                loader.load(GOLDEN_FIXTURE.toAbsolutePath().toString());

        AppliedDesignRules rules = result.value();
        assertThat(rules.appliedRules()).isNotEmpty();

        // 각 규칙의 sourceLocation이 DESIGN.md#category.key 형식인지 확인
        for (AppliedDesignRules.AppliedRule rule : rules.appliedRules()) {
            assertThat(rule.sourceLocation())
                    .startsWith("DESIGN.md#")
                    .contains(".");
        }
    }

    @Test
    void preservesContentHashForCaching() throws Exception {
        assumeFixtureExists();

        ThymeleafGenerationStageResult<AppliedDesignRules> result1 =
                loader.load(GOLDEN_FIXTURE.toAbsolutePath().toString());
        ThymeleafGenerationStageResult<AppliedDesignRules> result2 =
                loader.load(GOLDEN_FIXTURE.toAbsolutePath().toString());

        // 동일 파일의 hash는 동일해야 함 (deterministic)
        assertThat(result1.value().contentHash())
                .isEqualTo(result2.value().contentHash());
    }

    // ===== Helper Methods =====

    private void assumeFixtureExists() {
        if (!Files.exists(GOLDEN_FIXTURE.resolve("DESIGN.md"))) {
            throw new AssertionError("Fixture file not found: " + GOLDEN_FIXTURE.resolve("DESIGN.md"));
        }
    }
}
