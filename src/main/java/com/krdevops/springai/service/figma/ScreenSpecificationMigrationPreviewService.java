package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenActionSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.role.FieldMode;
import com.krdevops.springai.model.design.role.ScreenPattern;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * KRV-016: 기존(v1) {@link ScreenSpecification}이 새 Semantic 모델(ScreenPattern/SemanticRole/
 * ScreenActionSpec)로 변환됐을 때 어떤 결과가 나올지 미리 계산한다. {@link ScreenSemanticNormalizer}가
 * 변환할 수 없는 Control/Action은 임의로 기본값을 채우지 않고 {@link ReviewItem}으로 모아 반환하며,
 * 실제 v2 데이터를 저장하거나 원본 ScreenSpecification을 변경하지 않는다(Preview 전용).
 */
@Service
public class ScreenSpecificationMigrationPreviewService {

    private final ScreenSemanticNormalizer normalizer;

    public ScreenSpecificationMigrationPreviewService(ScreenSemanticNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public MigrationPreviewResult preview(ScreenSpecification specification) {
        List<ReviewItem> reviewItems = new ArrayList<>();
        List<PagePreview> pages = new ArrayList<>();
        for (PageSpec page : specification.pages()) {
            pages.add(previewPage(page, reviewItems));
        }
        return new MigrationPreviewResult(
                specification.id(), specification.version(), List.copyOf(pages), List.copyOf(reviewItems));
    }

    /** 저장 전 v1 원본 문자열 Action을 검토하는 명시적 Migration Preview 경계. */
    public LegacyActionMigrationPreview previewLegacyActions(String pageId, List<String> legacyActions) {
        List<ReviewItem> reviewItems = new ArrayList<>();
        List<ActionPreview> actions = new ArrayList<>();
        for (String action : legacyActions == null ? List.<String>of() : legacyActions) {
            actions.add(previewAction(pageId, action, reviewItems));
        }
        return new LegacyActionMigrationPreview(List.copyOf(actions), List.copyOf(reviewItems));
    }

    private PagePreview previewPage(PageSpec page, List<ReviewItem> reviewItems) {
        ScreenPattern pattern = null;
        try {
            pattern = normalizer.pattern(page);
        } catch (IllegalArgumentException e) {
            reviewItems.add(new ReviewItem(page.id(), ReviewCategory.PATTERN, page.template(), e.getMessage()));
        }
        FieldMode defaultFieldMode = pattern == null ? null : normalizer.fieldMode(pattern);

        List<FieldPreview> fields = new ArrayList<>();
        for (ScreenFieldBinding field : page.fields()) {
            fields.add(previewField(page.id(), field, defaultFieldMode, reviewItems));
        }

        List<ActionPreview> actions = new ArrayList<>();
        for (ScreenActionSpec action : page.actions()) {
            actions.add(new ActionPreview(action.command(), action));
        }

        return new PagePreview(page.id(), page.template(), pattern, List.copyOf(fields), List.copyOf(actions));
    }

    private FieldPreview previewField(
            String pageId, ScreenFieldBinding field, FieldMode defaultFieldMode, List<ReviewItem> reviewItems) {
        try {
            return new FieldPreview(field.id(), field.control(), normalizer.fieldRole(field), defaultFieldMode);
        } catch (IllegalArgumentException e) {
            reviewItems.add(new ReviewItem(pageId, ReviewCategory.FIELD_CONTROL, field.control(), e.getMessage()));
            return new FieldPreview(field.id(), field.control(), null, defaultFieldMode);
        }
    }

    private ActionPreview previewAction(String pageId, String action, List<ReviewItem> reviewItems) {
        try {
            return new ActionPreview(action, normalizer.action(action));
        } catch (IllegalArgumentException e) {
            reviewItems.add(new ReviewItem(pageId, ReviewCategory.ACTION, action, e.getMessage()));
            return new ActionPreview(action, null);
        }
    }

    public enum ReviewCategory { PATTERN, FIELD_CONTROL, ACTION }

    /** 변환에 실패해 사람의 검토가 필요한 원본 값. 임의로 기본값을 채우지 않았다는 증거로 남긴다. */
    public record ReviewItem(String pageId, ReviewCategory category, String rawValue, String reason) {}

    public record FieldPreview(String fieldId, String control, com.krdevops.springai.model.design.role.SemanticRole semanticRole, FieldMode mode) {
        public boolean needsReview() {
            return semanticRole == null;
        }
    }

    public record ActionPreview(String command, ScreenActionSpec resolved) {
        public boolean needsReview() {
            return resolved == null;
        }
    }

    public record PagePreview(
            String pageId, String template, ScreenPattern pattern,
            List<FieldPreview> fields, List<ActionPreview> actions) {
    }

    public record MigrationPreviewResult(
            String specificationId, int specificationVersion,
            List<PagePreview> pages, List<ReviewItem> reviewItems) {
        public boolean readyForMigration() {
            return reviewItems.isEmpty();
        }
    }

    public record LegacyActionMigrationPreview(List<ActionPreview> actions, List<ReviewItem> reviewItems) {
        public boolean readyForMigration() {
            return reviewItems.isEmpty();
        }
    }
}
