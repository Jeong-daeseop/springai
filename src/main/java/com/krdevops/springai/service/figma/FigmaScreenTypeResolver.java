package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.model.figma.LayoutPattern;
import org.springframework.stereotype.Component;

/**
 * R2-006: screenType은 PageSpec.template 접미사 우선/ScreenSpecification.archetype 접미사
 * fallback으로, layoutPattern은 archetype 키워드 포함 여부로 독립 판정한다(12번 §5.3).
 */
@Component
public class FigmaScreenTypeResolver {

    public FigmaScreenType resolveScreenType(PageSpec page, ScreenSpecification screenSpecification) {
        String source = page.template() != null && !page.template().isBlank()
                ? page.template() : screenSpecification.archetype();
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException(
                    "screenType을 판정할 수 없습니다: PageSpec.template과 ScreenSpecification.archetype이 모두 비어 있습니다.");
        }
        if (source.endsWith("_LIST")) {
            return FigmaScreenType.LIST;
        }
        if (source.endsWith("_FORM") || source.endsWith("_REGIST")) {
            return FigmaScreenType.FORM;
        }
        if (source.endsWith("_DETAIL")) {
            return FigmaScreenType.DETAIL;
        }
        throw new IllegalArgumentException(
                "screenType으로 매핑할 수 없는 값입니다(_LIST/_FORM/_REGIST/_DETAIL 접미사 없음): " + source);
    }

    public LayoutPattern resolveLayoutPattern(ScreenSpecification screenSpecification) {
        String archetype = screenSpecification.archetype();
        if (archetype == null) {
            return LayoutPattern.STANDARD;
        }
        if (archetype.contains("MASTER_DETAIL")) {
            return LayoutPattern.MASTER_DETAIL;
        }
        if (archetype.contains("DASHBOARD")) {
            return LayoutPattern.DASHBOARD;
        }
        return LayoutPattern.STANDARD;
    }
}
