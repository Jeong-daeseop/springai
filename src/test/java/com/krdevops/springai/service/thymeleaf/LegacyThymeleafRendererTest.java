package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.BoundThymeleafView;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.SkeletonSlotKind;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafSkeleton;
import com.krdevops.springai.model.thymeleaf.BindingContractStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafRouteBinding;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R6-057: LegacyThymeleafRenderer 테스트.
 *
 * <p>BoundThymeleafView를 FreeMarker 템플릿으로 렌더링하여 HTML을 생성한다.
 */
class LegacyThymeleafRendererTest {

    private LegacyThymeleafRenderer renderer;

    @BeforeEach
    void setUp() throws Exception {
        // FreeMarker Configuration 설정
        Configuration config = new Configuration(Configuration.VERSION_2_3_33);
        String basePath = new java.io.File("").getAbsolutePath();
        config.setDirectoryForTemplateLoading(
                new java.io.File(basePath, "src/main/resources/templates")
        );
        config.setDefaultEncoding("UTF-8");
        config.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);

        renderer = new LegacyThymeleafRenderer(config);
    }

    private ThymeleafBindingContract createTestContract(String screenId, LegacyScreenRole role) {
        ThymeleafRouteBinding route = new ThymeleafRouteBinding("/test", "GET", "listMethod", "empList", "List", true, false, List.of());
        return new ThymeleafBindingContract(
                screenId,
                role,
                route,
                List.of(),  // fields
                List.of(),  // displayFieldNames
                null,       // primaryDisplayAttributeName
                List.of(),  // modelAttributesResolved
                List.of(),  // modelAttributesUnresolved
                BindingContractStatus.RESOLVED,
                List.of(),  // issues
                null,       // sourceRevision
                Instant.now()
        );
    }

    @Test
    void rendersListViewToHtml() {
        ThymeleafSkeleton skeleton = new ThymeleafSkeleton(
                "emp-list-001",
                LegacyScreenRole.LIST,
                "직원 목록",
                "layout/default",
                List.of(SkeletonSlotKind.SEARCH_FORM, SkeletonSlotKind.DATA_TABLE, SkeletonSlotKind.ACTION_BAR)
        );

        ThymeleafBindingContract contract = createTestContract("emp-list-001", LegacyScreenRole.LIST);
        BoundThymeleafView view = new BoundThymeleafView(skeleton, contract);
        String html = renderer.render(view);

        assertThat(html).isNotBlank();
        assertThat(html).contains("<html");
        assertThat(html).contains("xmlns:th=");
        assertThat(html).contains("layout:decorate");
        assertThat(html).contains("직원 목록");
    }

    @Test
    void rendersFormViewToHtml() {
        ThymeleafSkeleton skeleton = new ThymeleafSkeleton(
                "emp-form-001",
                LegacyScreenRole.FORM,
                "직원 등록",
                "layout/default",
                List.of(SkeletonSlotKind.FORM_FIELDS, SkeletonSlotKind.ACTION_BAR)
        );

        ThymeleafBindingContract contract = createTestContract("emp-form-001", LegacyScreenRole.FORM);
        BoundThymeleafView view = new BoundThymeleafView(skeleton, contract);
        String html = renderer.render(view);

        assertThat(html).isNotBlank();
        assertThat(html).contains("직원 등록");
    }

    @Test
    void rendersDetailViewToHtml() {
        ThymeleafSkeleton skeleton = new ThymeleafSkeleton(
                "emp-detail-001",
                LegacyScreenRole.DETAIL,
                "직원 상세",
                "layout/default",
                List.of(SkeletonSlotKind.DISPLAY_FIELDS, SkeletonSlotKind.ACTION_BAR)
        );

        ThymeleafBindingContract contract = createTestContract("emp-detail-001", LegacyScreenRole.DETAIL);
        BoundThymeleafView view = new BoundThymeleafView(skeleton, contract);
        String html = renderer.render(view);

        assertThat(html).isNotBlank();
        assertThat(html).contains("직원 상세");
    }

    @Test
    void htmlContainsKrdsClasses() {
        ThymeleafSkeleton skeleton = new ThymeleafSkeleton(
                "test-001",
                LegacyScreenRole.LIST,
                "테스트",
                "layout/default",
                List.of(SkeletonSlotKind.SEARCH_FORM, SkeletonSlotKind.DATA_TABLE, SkeletonSlotKind.ACTION_BAR)
        );

        ThymeleafBindingContract contract = createTestContract("test-001", LegacyScreenRole.LIST);
        BoundThymeleafView view = new BoundThymeleafView(skeleton, contract);
        String html = renderer.render(view);

        assertThat(html)
                .contains("krds-input")
                .contains("krds-btn")
                .contains("krds-table-wrap");
    }

    @Test
    void htmlContainsThymeleafNamespace() {
        ThymeleafSkeleton skeleton = new ThymeleafSkeleton(
                "test-001",
                LegacyScreenRole.LIST,
                "테스트",
                "layout/default",
                List.of(SkeletonSlotKind.SEARCH_FORM, SkeletonSlotKind.DATA_TABLE, SkeletonSlotKind.ACTION_BAR)
        );

        ThymeleafBindingContract contract = createTestContract("test-001", LegacyScreenRole.LIST);
        BoundThymeleafView view = new BoundThymeleafView(skeleton, contract);
        String html = renderer.render(view);

        assertThat(html)
                .contains("xmlns:th=\"http://www.thymeleaf.org\"")
                .contains("xmlns:layout=");
    }
}
