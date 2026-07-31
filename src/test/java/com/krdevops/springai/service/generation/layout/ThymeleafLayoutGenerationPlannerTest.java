package com.krdevops.springai.service.generation.layout;

import com.krdevops.springai.model.crud.CrudLayerDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ThymeleafLayoutGenerationPlannerTest {

    private final ThymeleafLayoutGenerationPlanner planner = new ThymeleafLayoutGenerationPlanner();

    @Test
    void planLayoutFiles_returnsFiveLayersWithFixedFileNamesUnderBasePath(@TempDir Path tempDir) {
        List<ThymeleafLayoutGenerationPlanner.PlannedFile> planned =
                planner.planLayoutFiles(tempDir, "layout/admin", true);

        assertThat(planned).hasSize(5);
        assertThat(planned).extracting(f -> f.path().getFileName().toString())
                .containsExactlyInAnyOrder("default.html", "gnb.html", "lnb.html", "breadcrumb.html", "footer.html");
        assertThat(planned).allSatisfy(f ->
                assertThat(f.path()).isEqualTo(
                        tempDir.resolve("src/main/resources/templates/layout/admin")
                                .resolve(f.path().getFileName())
                                .normalize()));
        assertThat(planned).allMatch(f -> !f.skip());
    }

    @Test
    void planLayoutFiles_overwriteFalseAndExistingFile_marksSkip(@TempDir Path tempDir) throws Exception {
        Path gnb = tempDir.resolve("src/main/resources/templates/layout/gnb.html");
        Files.createDirectories(gnb.getParent());
        Files.writeString(gnb, "custom");

        List<ThymeleafLayoutGenerationPlanner.PlannedFile> planned =
                planner.planLayoutFiles(tempDir, "layout", false);

        ThymeleafLayoutGenerationPlanner.PlannedFile gnbPlan = planned.stream()
                .filter(f -> f.layerKey().equals(CrudLayerDefinition.LAYOUT_GNB_HTML))
                .findFirst().orElseThrow();
        assertThat(gnbPlan.skip()).isTrue();

        ThymeleafLayoutGenerationPlanner.PlannedFile defaultPlan = planned.stream()
                .filter(f -> f.layerKey().equals(CrudLayerDefinition.LAYOUT_HTML))
                .findFirst().orElseThrow();
        assertThat(defaultPlan.skip()).isFalse();
    }

    @Test
    void planGnbComponents_returnsFourComponentsUnderPackagePath(@TempDir Path tempDir) {
        List<ThymeleafLayoutGenerationPlanner.PlannedFile> planned =
                planner.planGnbComponents(tempDir, "egovframework.let.emp", true);

        assertThat(planned).hasSize(4);
        assertThat(planned).extracting(ThymeleafLayoutGenerationPlanner.PlannedFile::layerKey)
                .containsExactlyInAnyOrder(
                        CrudLayerDefinition.LAYOUT_GNB_MENU_VO,
                        CrudLayerDefinition.LAYOUT_GNB_MENU_MAPPER,
                        CrudLayerDefinition.LAYOUT_GNB_MENU_MAPPER_XML,
                        CrudLayerDefinition.LAYOUT_GNB_MENU_INTERCEPTOR);
        assertThat(planned).anySatisfy(f ->
                assertThat(f.path()).isEqualTo(
                        tempDir.resolve("src/main/java/egovframework/let/emp/cmm/vo/GnbMenuVO.java").normalize()));
    }

    @Test
    void planMainHtml_computesFixedPathAndRespectsOverwriteFlag(@TempDir Path tempDir) throws Exception {
        ThymeleafLayoutGenerationPlanner.PlannedFile notOverwritten =
                planner.planMainHtml(tempDir, true);
        assertThat(notOverwritten.path()).isEqualTo(
                tempDir.resolve("src/main/resources/templates/egovframework/main/main.html").normalize());
        assertThat(notOverwritten.skip()).isFalse();

        Files.createDirectories(notOverwritten.path().getParent());
        Files.writeString(notOverwritten.path(), "existing");

        ThymeleafLayoutGenerationPlanner.PlannedFile preserved = planner.planMainHtml(tempDir, false);
        assertThat(preserved.skip()).isTrue();
    }
}
