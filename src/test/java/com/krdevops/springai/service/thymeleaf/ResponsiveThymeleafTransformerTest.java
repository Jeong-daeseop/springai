package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.ScreenHtmlSkeleton;
import com.krdevops.springai.model.thymeleaf.ViewportConstraint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.krdevops.springai.config.StubEmbeddingModelTestConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CI 러너에는 로컬 전용 ko-sroberta ONNX 모델 파일도, Redis 서버도 없어 이 테스트가 필요로 하지
 * 않는 실제 임베딩/VectorStore auto-config가 각각 파일 I/O와 연결 단계에서 실패한다.
 * spring.ai.model.embedding=none + spring.ai.vectorstore.redis.enabled=false(TestPropertySource)로
 * 끄고 StubEmbeddingModelTestConfig의 no-op Bean으로 대체한다 — 전자만으로는 부족하다.
 * TransformersEmbeddingModelAutoConfiguration의 @ConditionalOnMissingBean이 구현 클래스
 * (TransformersEmbeddingModel) 기준이라 인터페이스 스텁 Bean만으로는 비활성화되지 않는다.
 */
@SpringBootTest
@TestPropertySource(properties = {"spring.ai.model.embedding=none", "spring.ai.vectorstore.redis.enabled=false",
        "spring.ai.openai.api-key=test-key-not-used"})
@Import(StubEmbeddingModelTestConfig.class)
@DisplayName("I-5A: ResponsiveThymeleafTransformer 테스트")
class ResponsiveThymeleafTransformerTest {

    @Autowired
    private ResponsiveThymeleafTransformer transformer;

    @Test
    @DisplayName("Desktop Viewport 변환 없음")
    void testDesktopViewportNoTransform() {
        var skeleton = createListSkeleton();
        var viewport = ViewportConstraint.desktop();

        var html = transformer.transformForViewport(skeleton, viewport);

        assertNotNull(html);
        assertTrue(html.contains("table"));
    }

    @Test
    @DisplayName("Mobile Viewport: table → card list")
    void testMobileViewportTableToCard() {
        var skeleton = createListSkeleton();
        var viewport = ViewportConstraint.mobile();

        var html = transformer.transformForViewport(skeleton, viewport);

        assertNotNull(html);
        assertTrue(html.contains("card-list"));
        assertTrue(html.contains("viewport-mobile"));
    }

    @Test
    @DisplayName("Viewport별 Navigation 교체")
    void testNavigationSwap() {
        var skeleton = createListSkeleton();
        var desktop = transformer.transformForViewport(skeleton, ViewportConstraint.desktop());
        var mobile = transformer.transformForViewport(skeleton, ViewportConstraint.mobile());

        assertTrue(desktop.contains("side-navigation"));
        assertTrue(mobile.contains("bottom-navigation"));
    }

    @Test
    @DisplayName("Viewport별 CSS 클래스 추가")
    void testViewportCssClassAddition() {
        var skeleton = createListSkeleton();
        var viewport = ViewportConstraint.tablet();

        var html = transformer.transformForViewport(skeleton, viewport);

        assertTrue(html.contains("viewport-tablet"));
    }

    @Test
    @DisplayName("세 Viewport Grid와 Binding 동일성")
    void threeViewportsKeepExactGridAndBindings() {
        var skeleton = createBoundListSkeleton();
        var desktop = transformer.transformForViewport(skeleton, ViewportConstraint.desktop());
        var tablet = transformer.transformForViewport(skeleton, ViewportConstraint.tablet());
        var mobile = transformer.transformForViewport(skeleton, ViewportConstraint.mobile());

        assertTrue(desktop.contains("--grid-columns: 12"));
        assertTrue(tablet.contains("--grid-columns: 8"));
        assertTrue(mobile.contains("--grid-columns: 4"));
        assertTrue(tablet.contains("drawer-navigation"));
        assertTrue(mobile.contains("card-list"));
        assertTrue(transformer.validateBindingConsistency(desktop, tablet, mobile));
    }

    @Test
    @DisplayName("Mobile Form은 단일 열로 재배치")
    void mobileFormUsesSingleColumn() {
        var skeleton = new ScreenHtmlSkeleton(
                "test-form", "고객 등록", "FORM",
                "<div class=\"page-form\"><div class=\"form-fields\"></div></div>",
                Map.of("--spacing", "8px"), List.of("form-fields"), List.of(), LocalDateTime.now());

        assertTrue(transformer.transformForViewport(skeleton, ViewportConstraint.mobile())
                .contains("form-grid-1"));
    }

    private ScreenHtmlSkeleton createBoundListSkeleton() {
        return new ScreenHtmlSkeleton(
                "bound-list", "고객 목록", "LIST",
                """
                <div class="page-list"><table class="table"><tbody>
                  <tr th:object="${row}"><td><input th:field="*{name}" /></td></tr>
                </tbody></table></div>
                """,
                Map.of("--color-primary", "#007bff"), List.of("list-rows"), List.of(), LocalDateTime.now());
    }

    private ScreenHtmlSkeleton createListSkeleton() {
        return new ScreenHtmlSkeleton(
            "test-list",
            "고객 목록",
            "LIST",
            """
                <div class="page-list">
                  <table class="table">
                    <thead><tr><!-- slot: list-headers --></tr></thead>
                    <tbody><!-- slot: list-rows --></tbody>
                  </table>
                </div>
                """,
            Map.of("--color-primary", "#007bff"),
            List.of("search-fields", "list-headers", "list-rows", "actions"),
            List.of(),
            LocalDateTime.now()
        );
    }
}
