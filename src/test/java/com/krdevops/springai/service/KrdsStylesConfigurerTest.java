package com.krdevops.springai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class KrdsStylesConfigurerTest {

    private final KrdsStylesConfigurer configurer = new KrdsStylesConfigurer();

    @Test
    void patchesWarCssOnceAndPreservesUserRules(@TempDir Path root) throws Exception {
        Path css = root.resolve("src/main/webapp/resources/css/styles.css");
        Files.createDirectories(css.getParent());
        Files.writeString(css, ".user-rule { color: rebeccapurple; }\n");

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
        assertThat(configurer.ensureBoardCrudStyles(root.toString()).status())
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

        var result = configurer.ensureBoardCrudStyles(root.toString());
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
}
