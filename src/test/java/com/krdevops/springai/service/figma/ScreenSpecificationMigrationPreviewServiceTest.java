package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.FieldSource;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.SearchPanelPlacement;
import com.krdevops.springai.model.design.UiFieldRole;
import com.krdevops.springai.model.design.role.ScreenPattern;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** KRV-016: v1 ScreenSpecification -> v2 Semantic 모델 Migration Preview 검증. */
class ScreenSpecificationMigrationPreviewServiceTest {

    private final ScreenSpecificationMigrationPreviewService service =
            new ScreenSpecificationMigrationPreviewService(new ScreenSemanticNormalizer());

    @Test
    void fullyConvertibleSpecificationIsReadyForMigrationWithNoReviewItems() {
        ScreenFieldBinding title = new ScreenFieldBinding("title", "제목", UiFieldRole.TITLE,
                FieldSource.column("t", "TITLE"), true, true, true, true, "TEXT", 1.0);
        PageSpec list = new PageSpec("list", "CRUD_LIST", List.of(title), List.of("SEARCH", "CREATE"));
        ScreenSpecification specification = specification(List.of(list));

        ScreenSpecificationMigrationPreviewService.MigrationPreviewResult result = service.preview(specification);

        assertThat(result.readyForMigration()).isTrue();
        assertThat(result.reviewItems()).isEmpty();
        assertThat(result.pages()).hasSize(1);
        assertThat(result.pages().get(0).pattern()).isEqualTo(ScreenPattern.CRUD_LIST);
        assertThat(result.pages().get(0).actions()).extracting(
                        ScreenSpecificationMigrationPreviewService.ActionPreview::needsReview)
                .containsOnly(false);
    }

    @Test
    void unsupportedControlIsReportedAsReviewItemNotSilentlyConverted() {
        ScreenFieldBinding richText = new ScreenFieldBinding("body", "본문", UiFieldRole.CONTENT,
                FieldSource.column("t", "BODY"), true, true, false, false, "RICH_TEXT", 1.0);
        PageSpec detail = new PageSpec("detail", "CRUD_DETAIL", List.of(richText), List.of("LIST"));
        ScreenSpecification specification = specification(List.of(detail));

        ScreenSpecificationMigrationPreviewService.MigrationPreviewResult result = service.preview(specification);

        assertThat(result.readyForMigration()).isFalse();
        assertThat(result.reviewItems()).hasSize(1);
        ScreenSpecificationMigrationPreviewService.ReviewItem item = result.reviewItems().get(0);
        assertThat(item.category()).isEqualTo(ScreenSpecificationMigrationPreviewService.ReviewCategory.FIELD_CONTROL);
        assertThat(item.rawValue()).isEqualTo("RICH_TEXT");
        assertThat(result.pages().get(0).fields().get(0).needsReview()).isTrue();
    }

    @Test
    void unsupportedActionIsReportedAsReviewItemNotSilentlyConverted() {
        PageSpec list = new PageSpec("list", "CRUD_LIST", List.of(), List.of("EXPORT_EXCEL"));
        ScreenSpecification specification = specification(List.of(list));

        ScreenSpecificationMigrationPreviewService.MigrationPreviewResult result = service.preview(specification);

        assertThat(result.readyForMigration()).isFalse();
        assertThat(result.reviewItems()).extracting(ScreenSpecificationMigrationPreviewService.ReviewItem::category)
                .containsExactly(ScreenSpecificationMigrationPreviewService.ReviewCategory.ACTION);
        assertThat(result.pages().get(0).actions().get(0).needsReview()).isTrue();
    }

    @Test
    void unresolvedPatternDoesNotThrowAndIsReportedAsReviewItem() {
        PageSpec dashboard = new PageSpec("home", "DASHBOARD", List.of(), List.of());
        ScreenSpecification specification = specification(List.of(dashboard));

        ScreenSpecificationMigrationPreviewService.MigrationPreviewResult result = service.preview(specification);

        assertThat(result.readyForMigration()).isFalse();
        assertThat(result.reviewItems()).extracting(ScreenSpecificationMigrationPreviewService.ReviewItem::category)
                .containsExactly(ScreenSpecificationMigrationPreviewService.ReviewCategory.PATTERN);
        assertThat(result.pages().get(0).pattern()).isNull();
    }

    private ScreenSpecification specification(List<PageSpec> pages) {
        return new ScreenSpecification(
                "spec-1", 1, ScreenSpecStatus.APPROVED, "테스트 화면", "crud", "CRUD_LIST",
                "ebt", "SAMPLE_TABLE", List.of(), pages, List.of(),
                LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN,
                ActionPlacement.TOP_RIGHT, SearchPanelPlacement.ABOVE_TABLE, null);
    }
}
