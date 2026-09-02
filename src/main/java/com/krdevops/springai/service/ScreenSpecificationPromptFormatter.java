package com.krdevops.springai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.DataSourceSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiDesignSpec;
import org.springframework.stereotype.Service;

@Service
public class ScreenSpecificationPromptFormatter {

    private final ObjectMapper objectMapper;

    public ScreenSpecificationPromptFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String format(ScreenSpecification specification) {
        if (specification == null) return "";
        StringBuilder result = new StringBuilder();
        result.append("[승인 화면명세 — 임의 변경 금지]\n")
                .append("  specificationId: ").append(specification.id()).append('\n')
                .append("  status: ").append(specification.status()).append('\n')
                .append("  archetype: ").append(specification.archetype()).append('\n')
                .append("  layoutDensity: ").append(specification.layoutDensity()).append('\n')
                .append("  formColumnLayout: ").append(specification.formColumnLayout()).append('\n')
                .append("  actionPlacement: ").append(specification.actionPlacement()).append('\n')
                .append("  searchPanelPlacement: ").append(specification.searchPanelPlacement()).append('\n')
                .append("  primaryDataSource: ").append(specification.database()).append('.')
                .append(specification.primaryTable()).append('\n');
        result.append("  dataSources:\n");
        for (DataSourceSpec dataSource : specification.dataSources()) {
            result.append("    - ").append(dataSource.alias()).append(" = ")
                    .append(dataSource.schema()).append('.').append(dataSource.table());
            if (dataSource.primary()) {
                result.append(" (PRIMARY)");
            } else {
                result.append(" ").append(dataSource.joinType()).append(" JOIN ON ")
                        .append(dataSource.joinExpression());
            }
            result.append('\n');
        }
        if (!specification.componentStyles().isEmpty()) {
            result.append("  componentStyles:\n");
            for (UiDesignSpec.ComponentSpec component : specification.componentStyles()) {
                result.append("    - ").append(component.type());
                if (component.backgroundColor() != null) {
                    result.append(" backgroundColor=").append(component.backgroundColor());
                }
                if (component.borderColor() != null) {
                    result.append(" borderColor=").append(component.borderColor());
                }
                result.append('\n');
            }
        }
        if (!specification.tokens().isEmpty()) {
            result.append("  tokens(화면 전체 배경색·폰트 참고값):\n");
            specification.tokens().forEach((key, value) ->
                    result.append("    - ").append(key).append(" = ").append(value).append('\n'));
        }
        if (!specification.componentGeometry().isEmpty()) {
            result.append("  [opacity 적용 규칙]:\n")
                    .append("    - componentStyles/geometry의 rgba alpha에는 color.a × paint.opacity가 이미 반영되어 있습니다.\n")
                    .append("    - geometry.opacity는 해당 노드의 로컬 opacity이며 null은 1.0입니다.\n")
                    .append("    - cumulativeNodeOpacity = ancestor opacity × ... × current node opacity\n")
                    .append("    - effectivePaintAlpha = rgba alpha × cumulativeNodeOpacity\n")
                    .append("    - geometry.fills/strokes 배열은 Figma paint 순서를 보존하며 첫 유효 SOLID 색상보다 우선합니다.\n")
                    .append("    - PaintSpec.color의 alpha와 PaintSpec.opacity는 별도 값이며, GRADIENT/IMAGE는 메타데이터로만 보존될 수 있습니다.\n")
                    .append("    - paint/node opacity를 rgba와 CSS opacity 양쪽에 중복 적용하지 마세요.\n")
                    .append("    - 실제 노드는 componentGeometry 값을 우선하고 componentStyles는 geometry 스타일이 없을 때만 fallback으로 사용하세요.\n");
            result.append("  componentGeometry(JSON, 참고용 — 정확한 좌표/간격/색상/폰트는 이 값을 ")
                    .append("따르되 krds-*/egov-* 클래스 구조와 컴포넌트 트리는 기존 템플릿 규칙을 유지하세요):\n");
            try {
                result.append(objectMapper.writeValueAsString(specification.componentGeometry())).append('\n');
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("componentGeometry 직렬화 실패", e);
            }
            appendGradientHints(result, specification.componentGeometry());
        }
        for (PageSpec page : specification.pages()) {
            result.append("  page ").append(page.id()).append(" template=").append(page.template())
                    .append(" selectionSource=").append(page.selectionSource()).append('\n');
            for (ScreenFieldBinding field : page.fields()) {
                result.append("    - ").append(field.label()).append(" [").append(field.dataRole()).append("] -> ")
                        .append(field.source().type());
                if (field.source().tableAlias() != null) result.append(' ').append(field.source().tableAlias()).append('.');
                if (field.source().column() != null) result.append(field.source().column());
                if (field.source().expression() != null) result.append(" expression=").append(field.source().expression());
                if (field.source().codeGroup() != null) result.append(" codeGroup=").append(field.source().codeGroup());
                result.append('\n');
            }
            result.append("    actions: ").append(page.actions()).append('\n');
        }
        return result.toString();
    }

    private void appendGradientHints(StringBuilder result, java.util.List<UiDesignSpec.NodeGeometry> nodes) {
        StringBuilder hints = new StringBuilder();
        appendGradientHintsRecursive(hints, nodes);
        if (!hints.isEmpty()) result.append("  gradientCssHints(자동 변환 참고값):\n").append(hints);
    }

    private void appendGradientHintsRecursive(StringBuilder result, java.util.List<UiDesignSpec.NodeGeometry> nodes) {
        for (UiDesignSpec.NodeGeometry node : nodes) {
            for (UiDesignSpec.PaintSpec paint : node.fills()) {
                String css = FigmaPaintCssConverter.toCss(paint);
                if (css != null) {
                    result.append("    - ").append(node.nodeId()).append(" background: ").append(css).append('\n');
                    break;
                }
            }
            appendGradientHintsRecursive(result, node.children());
        }
    }
}
