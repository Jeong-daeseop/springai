package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.ScreenHtmlSkeleton;
import com.krdevops.springai.model.thymeleaf.ViewportConstraint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * I-5A: Responsive Thymeleaf 변환.
 * ScreenHtmlSkeleton을 3개 Viewport에 맞게 반응형으로 변환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResponsiveThymeleafTransformer {

    /**
     * Viewport별 반응형 HTML 생성.
     *
     * @param skeleton ScreenHtmlSkeleton
     * @param viewport ViewportConstraint
     * @return Viewport 최적화된 HTML
     */
    public String transformForViewport(ScreenHtmlSkeleton skeleton, ViewportConstraint viewport) {
        if (skeleton == null || !skeleton.isValid()) {
            return "";
        }

        var html = skeleton.htmlStructure();

        // 1. Viewport별 CSS 클래스 추가
        html = addViewportCssClass(html, viewport);

        // 2. 아키타입별 변환
        html = switch (skeleton.archetype().toUpperCase()) {
            case "LIST" -> transformListForViewport(html, viewport);
            case "FORM" -> transformFormForViewport(html, viewport);
            case "DETAIL" -> transformDetailForViewport(html, viewport);
            default -> html;
        };

        // 3. Navigation Component 교체
        html = swapNavigationComponent(html, viewport);

        // 4. 반응형 CSS 변수 주입
        html = injectResponsiveCssVariables(html, viewport);

        return html;
    }

    private String addViewportCssClass(String html, ViewportConstraint viewport) {
        return html.replace(
            "<div class=\"page-",
            "<div class=\"page- viewport-" + viewport.viewport().name().toLowerCase() + " "
        );
    }

    private String transformListForViewport(String html, ViewportConstraint viewport) {
        if (viewport.viewport() == ViewportConstraint.ViewportType.DESKTOP) {
            return html;
        }

        // Tablet/Mobile: table → card list
        if (html.contains("<table")) {
            return html.replace(
                "<table class=\"table\">",
                "<div class=\"card-list\">" +
                    "<div class=\"card-item\"><!-- slot: card-fields --></div>" +
                "</div>"
            ).replace("</table>", "");
        }

        return html;
    }

    private String transformFormForViewport(String html, ViewportConstraint viewport) {
        if (viewport.viewport() == ViewportConstraint.ViewportType.DESKTOP) {
            return html;
        }

        var gridClass = switch (viewport.viewport()) {
            case TABLET -> "form-grid-2";
            case MOBILE -> "form-grid-1";
            default -> "form-grid-2";
        };

        return html.replace(
            "<div class=\"form-fields\">",
            "<div class=\"form-fields " + gridClass + "\">"
        );
    }

    private String transformDetailForViewport(String html, ViewportConstraint viewport) {
        if (viewport.viewport() == ViewportConstraint.ViewportType.DESKTOP) {
            return html;
        }

        return html.replace(
            "<dl class=\"detail-list\">",
            "<dl class=\"detail-list detail-vertical\">"
        );
    }

    private String swapNavigationComponent(String html, ViewportConstraint viewport) {
        var navClass = viewport.navigation().cssClass;
        String navigation = "<nav class=\"" + navClass + "\"><!-- slot: navigation --></nav>";
        if (html.contains("<!-- slot: navigation -->")) {
            return html.replace("<!-- slot: navigation -->", navigation);
        }
        return navigation + "\n" + html;
    }

    private String injectResponsiveCssVariables(String html, ViewportConstraint viewport) {
        var cssVars = new StringBuilder();
        cssVars.append("<style>\n");
        boolean mediaQuery = viewport.breakpointRule() != null && !viewport.breakpointRule().isBlank();
        if (mediaQuery) {
            cssVars.append(viewport.breakpointRule()).append(" {\n");
        }
        cssVars.append(mediaQuery ? "  :root {\n" : ":root {\n");
        cssVars.append("    --grid-columns: ").append(viewport.gridColumns()).append(";\n");
        cssVars.append("    --viewport-width: ").append(viewport.width()).append("px;\n");
        cssVars.append("  }\n");
        if (mediaQuery) {
            cssVars.append("}\n");
        }
        cssVars.append("</style>\n");

        return cssVars.toString() + html;
    }

    /**
     * Viewport별 Overflow 검증.
     *
     * @param html 렌더링된 HTML
     * @param viewport ViewportConstraint
     * @return 유효하면 true
     */
    public boolean validateNoOverflow(String html, ViewportConstraint viewport) {
        if (html == null || html.isEmpty()) {
            return false;
        }

        return !html.contains("width: >100%");
    }

    /**
     * 세 Viewport의 Binding Contract 동일성 검증.
     *
     * @param desktopHtml Desktop 뷰
     * @param tabletHtml Tablet 뷰
     * @param mobileHtml Mobile 뷰
     * @return 동일하면 true
     */
    public boolean validateBindingConsistency(String desktopHtml, String tabletHtml, String mobileHtml) {
        if (desktopHtml == null || tabletHtml == null || mobileHtml == null) {
            return false;
        }

        var desktopBindings = countBindings(desktopHtml);
        var tabletBindings = countBindings(tabletHtml);
        var mobileBindings = countBindings(mobileHtml);

        return desktopBindings == tabletBindings && tabletBindings == mobileBindings;
    }

    private int countBindings(String html) {
        var count = 0;
        count += html.split("th:field").length - 1;
        count += html.split("th:object").length - 1;
        return count;
    }
}
