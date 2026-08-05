package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WP7 5차 pass: {@code Files.writeString} 원시 호출을 공용
 * {@code ApprovedProjectWritePort}(ATOMIC_APPROVED)로 전환한다.
 */
class KrdsStylesConfigurerTest {

    private KrdsStylesConfigurer configurer(Path outputRoot) {
        EgovProperties properties = new EgovProperties();
        EgovProperties.Output output = new EgovProperties.Output();
        output.setBasePath(outputRoot.toString());
        properties.setOutput(output);
        CodeService codeService = new CodeService(properties);
        FileSystemApprovedProjectWritePort writePort = new FileSystemApprovedProjectWritePort(
                new SafePathResolver(), new OperationHashFactory(new ObjectMapper()));
        return new KrdsStylesConfigurer(codeService, writePort, new OperationHashFactory(new ObjectMapper()));
    }

    @Test
    void patchesWarCssOnceAndPreservesUserRules(@TempDir Path root) throws Exception {
        Path css = root.resolve("src/main/webapp/resources/css/styles.css");
        Files.createDirectories(css.getParent());
        Files.writeString(css, ".user-rule { color: rebeccapurple; }\n");
        KrdsStylesConfigurer configurer = configurer(root);

        var first = configurer.ensureBoardCrudStyles(root.toString());
        String once = Files.readString(css);
        var second = configurer.ensureBoardCrudStyles(root.toString());

        assertThat(first.status()).isEqualTo(KrdsStylesConfigurer.Status.PATCHED);
        assertThat(second.status()).isEqualTo(KrdsStylesConfigurer.Status.PRESERVED);
        assertThat(Files.readString(css)).isEqualTo(once).startsWith(".user-rule");
        assertThat(once).containsOnlyOnce(KrdsStylesConfigurer.START_MARKER);
    }

    @Test
    void reportsNotFoundWhenStaticResourceStructureIsMissing(@TempDir Path root) {
        assertThat(configurer(root).ensureBoardCrudStyles(root.toString()).status())
                .isEqualTo(KrdsStylesConfigurer.Status.NOT_FOUND);
    }

    @Test
    void updatesOutdatedMarkerBlockAndPreservesRulesOutsideIt(@TempDir Path root) throws Exception {
        Path css = root.resolve("src/main/webapp/resources/css/styles.css");
        Files.createDirectories(css.getParent());
        Files.writeString(css, """
.before { color: red; }
/* === egov-board-crud:start === */
.legacy { height: 99rem; }
/* === egov-board-crud:end === */
.after { color: blue; }
""");

        var result = configurer(root).ensureBoardCrudStyles(root.toString());
        String updated = Files.readString(css);

        assertThat(result.status()).isEqualTo(KrdsStylesConfigurer.Status.PATCHED);
        assertThat(result.message()).contains("계약 갱신");
        assertThat(updated)
                .contains(".before { color: red; }")
                .contains(".after { color: blue; }")
                .contains("--krds-input--textarea-size-height")
                .doesNotContain(".legacy");
    }

    @Test
    void generatedBaseCssMarkerMatchesRuntimePatchContract() throws Exception {
        String template = Files.readString(Path.of(
                "src/main/resources/templates/egov/styles.css.tpl"));
        int start = template.indexOf(KrdsStylesConfigurer.START_MARKER);
        int end = template.indexOf(KrdsStylesConfigurer.END_MARKER, start)
                + KrdsStylesConfigurer.END_MARKER.length();

        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        assertThat(template.substring(start, end).trim())
                .isEqualTo(KrdsStylesConfigurer.CRUD_CSS.trim());
    }

    @Test
    void generatedBaseDensityMarkerMatchesRuntimePatchContract() throws Exception {
        String template = Files.readString(Path.of(
                "src/main/resources/templates/egov/styles.css.tpl"));
        int start = template.indexOf(TableDensityCssContract.START_MARKER);
        int end = template.indexOf(TableDensityCssContract.END_MARKER, start)
                + TableDensityCssContract.END_MARKER.length();

        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        assertThat(template.substring(start, end).trim())
                .isEqualTo(TableDensityCssContract.CSS.trim());
    }

    @Test
    void tableDensityStylesArePatchedIdempotently(@TempDir Path root) throws Exception {
        Path css = root.resolve("src/main/webapp/resources/css/styles.css");
        Files.createDirectories(css.getParent());
        Files.writeString(css, ".user { color: black; }\n");
        KrdsStylesConfigurer configurer = configurer(root);

        var first = configurer.ensureTableDensityStyles(root.toString());
        var second = configurer.ensureTableDensityStyles(root.toString());
        String content = Files.readString(css);

        assertThat(first.status()).isEqualTo(KrdsStylesConfigurer.Status.PATCHED);
        assertThat(second.status()).isEqualTo(KrdsStylesConfigurer.Status.PRESERVED);
        assertThat(content).containsOnlyOnce(KrdsStylesConfigurer.DENSITY_START_MARKER)
                .contains(".egov-density-compact")
                .contains(".egov-density-comfortable")
                .startsWith(".user");
    }

    @Test
    void generatedBaseFormColumnLayoutMarkerMatchesRuntimePatchContract() throws Exception {
        String template = Files.readString(Path.of(
                "src/main/resources/templates/egov/styles.css.tpl"));
        int start = template.indexOf(FormColumnLayoutCssContract.START_MARKER);
        int end = template.indexOf(FormColumnLayoutCssContract.END_MARKER, start)
                + FormColumnLayoutCssContract.END_MARKER.length();

        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        assertThat(template.substring(start, end).trim())
                .isEqualTo(FormColumnLayoutCssContract.CSS.trim());
    }

    @Test
    void formColumnLayoutStylesArePatchedIdempotently(@TempDir Path root) throws Exception {
        Path css = root.resolve("src/main/webapp/resources/css/styles.css");
        Files.createDirectories(css.getParent());
        Files.writeString(css, ".user { color: black; }\n");
        KrdsStylesConfigurer configurer = configurer(root);

        var first = configurer.ensureFormColumnLayoutStyles(root.toString());
        var second = configurer.ensureFormColumnLayoutStyles(root.toString());
        String content = Files.readString(css);

        assertThat(first.status()).isEqualTo(KrdsStylesConfigurer.Status.PATCHED);
        assertThat(second.status()).isEqualTo(KrdsStylesConfigurer.Status.PRESERVED);
        assertThat(content).containsOnlyOnce(KrdsStylesConfigurer.FORM_COLUMN_LAYOUT_START_MARKER)
                .contains(".egov-layout-two-col")
                .contains(".form-row-two-col")
                .startsWith(".user");
    }

    @Test
    void newStylesCssIsCreatedThroughPortWhenDirectoryExistsButFileDoesNot(@TempDir Path root) throws Exception {
        Path cssDir = root.resolve("src/main/webapp/resources/css");
        Files.createDirectories(cssDir);

        var result = configurer(root).ensureBoardCrudStyles(root.toString());

        assertThat(result.status()).isEqualTo(KrdsStylesConfigurer.Status.PATCHED);
        assertThat(result.message()).contains("신규 생성");
        assertThat(Files.readString(cssDir.resolve("styles.css"))).contains(KrdsStylesConfigurer.START_MARKER);
    }

    /** ATOMIC_APPROVED 전환 확인: 디스크 쓰기가 실패하면 원본 파일이 그대로 보존돼야 한다. */
    @Test
    void diskWriteFailure_leavesOriginalFileUntouched(@TempDir Path root) throws Exception {
        Path css = root.resolve("src/main/webapp/resources/css/styles.css");
        Files.createDirectories(css.getParent());
        Files.writeString(css, ".user-rule { color: rebeccapurple; }\n");
        String original = Files.readString(css);
        Path cssDir = css.getParent();
        boolean readOnlySet = cssDir.toFile().setWritable(false);
        Assumptions.assumeTrue(readOnlySet, "이 실행 환경(예: root)에서는 디렉터리 쓰기 금지가 걸리지 않아 이 테스트를 건너뛴다.");
        try {
            var result = configurer(root).ensureBoardCrudStyles(root.toString());

            assertThat(result.status()).isEqualTo(KrdsStylesConfigurer.Status.FAILED);
        } finally {
            cssDir.toFile().setWritable(true);
        }
        assertThat(Files.readString(css)).isEqualTo(original);
    }
}
