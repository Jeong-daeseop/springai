package com.krdevops.springai.service;

import com.krdevops.springai.model.design.DataSourceSpec;
import com.krdevops.springai.model.design.FieldSource;
import com.krdevops.springai.model.design.FieldSourceType;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiFieldRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 스키마에서 관계와 표시 컬럼이 모두 확인된 경우에만 단일 컬럼 바인딩을 JOIN 바인딩으로 승격한다.
 * 후보가 모호하면 원래 COLUMN을 유지해 잘못된 JOIN을 자동 생성하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ScreenDataBindingResolver {

    private static final Map<UiFieldRole, List<String>> DISPLAY_COLUMNS = Map.of(
            UiFieldRole.DEPARTMENT, List.of("ORGNZT_NM", "DEPT_NM", "DEPARTMENT_NM", "DEPARTMENT_NAME")
    );

    private final CrudSchemaQueryService schemaQueryService;
    private final TableRelationService tableRelationService;

    public ScreenSpecification resolve(ScreenSpecification specification) {
        List<TableRelationService.RelationInfo> relations = new ArrayList<>();
        relations.addAll(tableRelationService.getPhysicalFkParents(
                specification.database(), specification.primaryTable()));
        relations.addAll(tableRelationService.getImplicitJoinCandidates(
                specification.database(), specification.primaryTable()));

        Map<String, ResolvedJoin> joinsBySourceColumn = new LinkedHashMap<>();
        for (PageSpec page : specification.pages()) {
            for (ScreenFieldBinding field : page.fields()) {
                resolveJoin(specification, field, relations)
                        .ifPresent(join -> joinsBySourceColumn.putIfAbsent(
                                join.relation().sourceColumn().toUpperCase(Locale.ROOT), join));
            }
        }
        if (joinsBySourceColumn.isEmpty()) return specification;

        Map<String, String> aliases = new LinkedHashMap<>();
        List<DataSourceSpec> dataSources = new ArrayList<>(specification.dataSources());
        int index = 1;
        for (ResolvedJoin join : joinsBySourceColumn.values()) {
            String alias = "j" + index++;
            aliases.put(join.relation().sourceColumn().toUpperCase(Locale.ROOT), alias);
            dataSources.add(new DataSourceSpec(
                    "join-" + alias, specification.database(), join.relation().targetTable(), alias,
                    false, "LEFT", "t." + join.relation().sourceColumn() + " = "
                    + alias + "." + join.relation().targetColumn()));
        }

        List<PageSpec> pages = specification.pages().stream()
                .map(page -> new PageSpec(page.id(), page.template(), page.fields().stream()
                        .map(field -> replaceField(field, joinsBySourceColumn, aliases)).toList(),
                        page.actions(), page.selectionSource()))
                .toList();
        return new ScreenSpecification(
                specification.id(), specification.version(), specification.status(),
                specification.screenName(), specification.featureType(), specification.archetype(),
                specification.database(), specification.primaryTable(), dataSources, pages,
                specification.issues(), specification.layoutDensity(), specification.formColumnLayout(),
                specification.actionPlacement(), specification.searchPanelPlacement(),
                specification.createdAt());
    }

    private java.util.Optional<ResolvedJoin> resolveJoin(
            ScreenSpecification specification,
            ScreenFieldBinding field,
            List<TableRelationService.RelationInfo> relations) {
        if (field.source() == null || field.source().type() != FieldSourceType.COLUMN
                || !DISPLAY_COLUMNS.containsKey(field.role())) {
            return java.util.Optional.empty();
        }
        List<ResolvedJoin> candidates = relations.stream()
                .filter(relation -> relation.sourceColumn().equalsIgnoreCase(field.source().column()))
                .map(relation -> displayColumn(specification.database(), relation, field.role()))
                .flatMap(java.util.Optional::stream)
                .toList();
        return candidates.size() == 1 ? java.util.Optional.of(candidates.get(0)) : java.util.Optional.empty();
    }

    private java.util.Optional<ResolvedJoin> displayColumn(
            String database, TableRelationService.RelationInfo relation, UiFieldRole role) {
        List<String> columns = schemaQueryService.fetchColumns(database, relation.targetTable()).stream()
                .map(row -> String.valueOf(row.get("COLUMN_NAME")))
                .toList();
        return DISPLAY_COLUMNS.get(role).stream()
                .filter(candidate -> columns.stream().anyMatch(candidate::equalsIgnoreCase))
                .findFirst()
                .map(column -> new ResolvedJoin(relation, column));
    }

    private ScreenFieldBinding replaceField(
            ScreenFieldBinding field,
            Map<String, ResolvedJoin> joins,
            Map<String, String> aliases) {
        if (field.source() == null || field.source().column() == null) return field;
        String key = field.source().column().toUpperCase(Locale.ROOT);
        ResolvedJoin join = joins.get(key);
        if (join == null) return field;
        return new ScreenFieldBinding(
                field.id(), field.label(), field.role(),
                FieldSource.joinColumn(aliases.get(key), join.displayColumn()),
                field.visible(), field.required(), field.searchable(), field.sortable(),
                field.control(), field.confidence());
    }

    private record ResolvedJoin(TableRelationService.RelationInfo relation, String displayColumn) {
    }
}
