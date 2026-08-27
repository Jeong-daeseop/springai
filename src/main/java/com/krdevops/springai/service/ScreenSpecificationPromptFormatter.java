package com.krdevops.springai.service;

import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.DataSourceSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiDesignSpec;
import org.springframework.stereotype.Service;

@Service
public class ScreenSpecificationPromptFormatter {

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
}
