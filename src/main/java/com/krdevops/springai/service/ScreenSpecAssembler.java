package com.krdevops.springai.service;

import com.krdevops.springai.model.design.DataSourceSpec;
import com.krdevops.springai.model.design.FieldSource;
import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.FieldSelectionSource;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.SearchPanelPlacement;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenActionSpec;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.SpecIssue;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.UiFieldRole;
import com.krdevops.springai.policy.UiFieldRolePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScreenSpecAssembler {

    private static final int MAX_SELECTED_COLUMNS = 6;

    private final ScreenSpecValidator validator;

    public ScreenSpecification assemble(
            String database,
            String tableName,
            String screenName,
            String featureType,
            List<Map<String, Object>> rawColumns,
            UiDesignSpec uiSpec) {
        return assemble(database, tableName, screenName, featureType, rawColumns, uiSpec, null, null);
    }

    public ScreenSpecification assemble(
            String database,
            String tableName,
            String screenName,
            String featureType,
            List<Map<String, Object>> rawColumns,
            UiDesignSpec uiSpec,
            List<String> listColumns,
            List<String> detailColumns) {

        UiDesignSpec resolvedUi = uiSpec == null
                ? UiDesignSpec.empty(defaultArchetype(featureType)) : uiSpec;
        List<SpecIssue> issues = new ArrayList<>();
        BindingAssemblyResult bindings = new BindingAssemblyResult(
                bindingsFromSchema(rawColumns),
                resolvedUi.fieldHints().isEmpty()
                        ? List.of()
                        : bindingsFromHints(rawColumns, resolvedUi.fieldHints(), issues),
                rawColumns.stream()
                        .filter(column -> "PRI".equalsIgnoreCase(string(column, "COLUMN_KEY")))
                        .map(column -> string(column, "COLUMN_NAME"))
                        .filter(value -> !value.isBlank())
                        .toList());

        if (!resolvedUi.uncertainties().isEmpty()) {
            resolvedUi.uncertainties().forEach(uncertainty -> issues.add(new SpecIssue(
                    "DESIGN_UNCERTAINTY", SpecIssue.Severity.WARNING, uncertainty, null)));
        }

        String archetype = blank(resolvedUi.archetype())
                ? defaultArchetype(featureType) : resolvedUi.archetype();
        List<PageSpec> pages = pages(archetype, bindings, resolvedUi.actions(),
                normalizeExplicitColumns(listColumns), normalizeExplicitColumns(detailColumns));
        LayoutDensity density = LayoutDensity.from(
                resolvedUi.layout() == null ? null : resolvedUi.layout().density());
        FormColumnLayout formColumnLayout = FormColumnLayout.from(
                resolvedUi.layout() == null ? null : resolvedUi.layout().formColumnLayout());
        ActionPlacement actionPlacement = ActionPlacement.from(
                resolvedUi.layout() == null ? null : resolvedUi.layout().actionPlacement());
        SearchPanelPlacement searchPanelPlacement = SearchPanelPlacement.from(
                resolvedUi.layout() == null ? null : resolvedUi.layout().searchPanelPlacement());
        ScreenSpecification draft = new ScreenSpecification(
                UUID.randomUUID().toString(), 1, ScreenSpecStatus.DRAFT,
                blank(screenName) ? tableName : screenName,
                blank(featureType) ? "crud" : featureType,
                archetype, database, tableName,
                List.of(DataSourceSpec.primary(database, tableName)), pages, issues,
                density, formColumnLayout, actionPlacement, searchPanelPlacement, LocalDateTime.now(),
                null, null, resolvedUi.components(), resolvedUi.geometryTree());
        return validator.validate(draft);
    }

    private List<ScreenFieldBinding> bindingsFromSchema(List<Map<String, Object>> columns) {
        List<ScreenFieldBinding> result = new ArrayList<>();
        for (Map<String, Object> column : columns) {
            String name = string(column, "COLUMN_NAME");
            UiFieldRole role = UiFieldRolePolicy.inferRole(name);
            result.add(binding(column, camel(name), label(column, name), role,
                    FieldSource.column("t", name), 1.0, null));
        }
        return result;
    }

    private List<ScreenFieldBinding> bindingsFromHints(
            List<Map<String, Object>> columns,
            List<UiDesignSpec.FieldHint> hints,
            List<SpecIssue> issues) {
        List<ScreenFieldBinding> result = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (UiDesignSpec.FieldHint hint : hints) {
            if (hint.role() == UiFieldRole.ROW_NUMBER) {
                String id = blank(hint.id()) ? "rowNumber" : hint.id();
                result.add(new ScreenFieldBinding(
                        id, hint.label(), hint.role(), FieldSource.derived("PAGE_ROW_NUMBER"),
                        true, false, false, false, "TEXT", normalizedConfidence(hint.confidence())));
                continue;
            }
            Map<String, Object> column = findColumn(columns, hint, used);
            String id = blank(hint.id()) ? camel(hint.label()) : hint.id();
            if (column == null) {
                result.add(new ScreenFieldBinding(id, hint.label(), hint.role(), FieldSource.unmapped(),
                        true, false, false, false, hint.control(), hint.confidence()));
                issues.add(new SpecIssue("NO_COLUMN_CANDIDATE", SpecIssue.Severity.ERROR,
                        "화면 필드에 대응하는 컬럼 후보가 없습니다: " + hint.label(), id));
                continue;
            }
            String name = string(column, "COLUMN_NAME");
            used.add(name.toUpperCase(Locale.ROOT));
            FieldSource source = commonCodeCandidate(hint.role(), name)
                    ? FieldSource.commonCode("t", name, null)
                    : FieldSource.column("t", name);
            result.add(binding(column, id, hint.label(), hint.role(), source,
                    hint.confidence(), hint.control()));
            if (source.type() == com.krdevops.springai.model.design.FieldSourceType.COMMON_CODE) {
                issues.add(new SpecIssue("COMMON_CODE_GROUP_REQUIRED", SpecIssue.Severity.WARNING,
                        "공통코드 그룹(CODE_ID)을 확인해야 합니다: " + hint.label(), id));
            }
        }
        return result;
    }

    private boolean commonCodeCandidate(UiFieldRole role, String columnName) {
        String upper = columnName.toUpperCase(Locale.ROOT);
        return (role == UiFieldRole.STATUS || role == UiFieldRole.CATEGORY)
                && (upper.endsWith("_CODE") || upper.endsWith("_CD"));
    }

    private double normalizedConfidence(double confidence) {
        return confidence <= 0 ? 1.0 : confidence;
    }

    private Map<String, Object> findColumn(
            List<Map<String, Object>> columns, UiDesignSpec.FieldHint hint, Set<String> used) {
        List<String> candidates = UiFieldRolePolicy.candidateColumns(hint.role());
        for (String candidate : candidates) {
            for (Map<String, Object> column : columns) {
                String name = string(column, "COLUMN_NAME");
                if (!used.contains(name.toUpperCase(Locale.ROOT)) && name.equalsIgnoreCase(candidate)) {
                    return column;
                }
            }
        }
        String normalizedLabel = normalize(hint.label());
        return columns.stream()
                .filter(column -> !used.contains(string(column, "COLUMN_NAME").toUpperCase(Locale.ROOT)))
                .filter(column -> normalize(string(column, "COLUMN_COMMENT")).equals(normalizedLabel)
                        || normalize(string(column, "COLUMN_NAME")).equals(normalizedLabel))
                .findFirst().orElse(null);
    }

    private ScreenFieldBinding binding(
            Map<String, Object> column, String id, String label, UiFieldRole role,
            FieldSource source, double confidence, String requestedControl) {
        String dataType = string(column, "DATA_TYPE").toLowerCase(Locale.ROOT);
        boolean required = "NO".equalsIgnoreCase(string(column, "IS_NULLABLE"));
        String control = blank(requestedControl) ? control(dataType, role) : requestedControl;
        boolean searchable = role == UiFieldRole.TITLE || role == UiFieldRole.STATUS
                || role == UiFieldRole.CATEGORY || role == UiFieldRole.AUTHOR;
        boolean sortable = role == UiFieldRole.CREATED_AT || role == UiFieldRole.UPDATED_AT
                || role == UiFieldRole.TITLE || role == UiFieldRole.SORT_ORDER;
        return new ScreenFieldBinding(id, label, role, source, true, required,
                searchable, sortable, control, confidence <= 0 ? 1.0 : confidence);
    }

    private List<PageSpec> pages(
            String archetype,
            BindingAssemblyResult bindings,
            List<UiDesignSpec.ActionSpec> actions,
            List<String> listColumns,
            List<String> detailColumns) {
        List<String> actionNames = actions.stream().map(UiDesignSpec.ActionSpec::type).toList();
        List<ScreenActionSpec> resolvedActions = semanticActions(actionNames.isEmpty()
                ? List.of("SEARCH", "CREATE", "VIEW_DETAIL", "UPDATE", "DELETE") : actionNames);
        String base = archetype == null ? "CRUD" : archetype.replaceAll("_(LIST|DETAIL|FORM)$", "");
        PageSelection list = selectPageBindings("list", archetype, bindings, listColumns);
        PageSelection detail = selectPageBindings("detail", archetype, bindings, detailColumns);
        return List.of(
                new PageSpec("list", base + "_LIST", list.fields(), resolvedActions, list.source()),
                new PageSpec("detail", base + "_DETAIL", detail.fields(),
                        semanticActions(List.of("UPDATE", "DELETE", "BACK")), detail.source()),
                new PageSpec("regist", base + "_FORM", bindings.schemaBindings(),
                        semanticActions(List.of("SAVE", "CANCEL")), FieldSelectionSource.DEFAULT),
                new PageSpec("updt", base + "_FORM", bindings.schemaBindings(),
                        semanticActions(List.of("UPDATE", "CANCEL")), FieldSelectionSource.DEFAULT));
    }

    private List<ScreenActionSpec> semanticActions(List<String> commands) {
        return commands.stream().map(ScreenActionSpec::fromLegacyCommand).toList();
    }

    private PageSelection selectPageBindings(
            String pageId,
            String archetype,
            BindingAssemblyResult bindings,
            List<String> explicitColumns) {
        if (!explicitColumns.isEmpty()) {
            List<ScreenFieldBinding> selected = explicitBindings(
                    bindings.schemaBindings(), bindings.pkColumns(), explicitColumns);
            return new PageSelection(selected, FieldSelectionSource.EXPLICIT);
        }
        if (targetsPage(archetype, pageId) && !bindings.hintBindings().isEmpty()) {
            return new PageSelection(
                    mergePkBindings(bindings, bindings.hintBindings()),
                    FieldSelectionSource.DESIGN_REFERENCE);
        }
        return new PageSelection(bindings.schemaBindings(), FieldSelectionSource.DEFAULT);
    }

    private List<ScreenFieldBinding> explicitBindings(
            List<ScreenFieldBinding> schemaBindings, List<String> pkColumns, List<String> columns) {
        List<ScreenFieldBinding> pkBindings = schemaBindings.stream()
                .filter(binding -> binding.source() != null && binding.source().column() != null)
                .filter(binding -> pkColumns.stream()
                        .anyMatch(pk -> pk.equalsIgnoreCase(binding.source().column())))
                .toList();
        if (pkBindings.isEmpty() && !schemaBindings.isEmpty()) {
            pkBindings = List.of(schemaBindings.get(0));
        }
        List<ScreenFieldBinding> selected = new ArrayList<>(pkBindings);
        for (String column : columns) {
            ScreenFieldBinding binding = schemaBindings.stream()
                    .filter(candidate -> candidate.source() != null && candidate.source().column() != null)
                    .filter(candidate -> candidate.source().column().equalsIgnoreCase(column))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 명시 컬럼: " + column));
            if (selected.stream().noneMatch(existing -> existing.id().equals(binding.id()))) selected.add(binding);
        }
        if (selected.size() > MAX_SELECTED_COLUMNS) {
            throw new IllegalArgumentException(
                    "화면 표시 컬럼은 복합 PK를 포함해 최대 " + MAX_SELECTED_COLUMNS + "개까지 지정할 수 있습니다.");
        }
        return List.copyOf(selected);
    }

    private List<ScreenFieldBinding> mergePkBindings(
            BindingAssemblyResult bindings, List<ScreenFieldBinding> selectedBindings) {
        List<ScreenFieldBinding> selected = new ArrayList<>();
        List<ScreenFieldBinding> pkBindings = bindings.schemaBindings().stream()
                .filter(binding -> binding.source() != null && binding.source().column() != null)
                .filter(binding -> bindings.pkColumns().stream()
                        .anyMatch(pk -> pk.equalsIgnoreCase(binding.source().column())))
                .toList();
        if (pkBindings.isEmpty() && !bindings.schemaBindings().isEmpty()) {
            pkBindings = List.of(bindings.schemaBindings().get(0));
        }
        pkBindings.stream()
                .filter(pk -> selectedBindings.stream().noneMatch(candidate -> sameSourceColumn(pk, candidate)))
                .forEach(binding -> addBinding(selected, binding));
        selectedBindings.forEach(binding -> addBinding(selected, binding));
        if (selected.size() > MAX_SELECTED_COLUMNS) {
            throw new IllegalArgumentException(
                    "화면 표시 컬럼은 복합 PK를 포함해 최대 " + MAX_SELECTED_COLUMNS + "개까지 지정할 수 있습니다.");
        }
        return List.copyOf(selected);
    }

    private void addBinding(List<ScreenFieldBinding> selected, ScreenFieldBinding binding) {
        if (selected.stream().noneMatch(existing -> existing.id().equals(binding.id()))) {
            selected.add(binding);
        }
    }

    private boolean sameSourceColumn(ScreenFieldBinding left, ScreenFieldBinding right) {
        return left.source() != null && right.source() != null
                && left.source().column() != null && right.source().column() != null
                && left.source().column().equalsIgnoreCase(right.source().column());
    }

    private boolean targetsPage(String archetype, String pageId) {
        if (archetype == null || archetype.isBlank()) return false;
        String normalized = archetype.toUpperCase(Locale.ROOT);
        return normalized.endsWith("_" + pageId.toUpperCase(Locale.ROOT));
    }

    private List<String> normalizeExplicitColumns(List<String> columns) {
        if (columns == null) return List.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        columns.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .forEach(normalized::add);
        return List.copyOf(normalized);
    }

    private String control(String dataType, UiFieldRole role) {
        if (role == UiFieldRole.CONTENT) return "TEXTAREA";
        if (role == UiFieldRole.STATUS || role == UiFieldRole.CATEGORY) return "SELECT";
        if (dataType.contains("date") || dataType.contains("time")) return "DATE";
        if (dataType.contains("int") || dataType.contains("decimal") || dataType.contains("number")) return "NUMBER";
        return "TEXT";
    }

    private String defaultArchetype(String featureType) {
        if ("board".equalsIgnoreCase(featureType)) return "BOARD";
        if ("master-detail".equalsIgnoreCase(featureType)) return "MASTER_DETAIL";
        return "CRUD";
    }

    private String label(Map<String, Object> column, String fallback) {
        String comment = string(column, "COLUMN_COMMENT");
        return blank(comment) ? fallback : comment;
    }

    private String string(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) value = source.get(key.toLowerCase(Locale.ROOT));
        return value == null ? "" : value.toString();
    }

    private String camel(String value) {
        if (value == null) return "field";
        String[] parts = value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        StringBuilder result = new StringBuilder(parts.length == 0 ? "field" : parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) result.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return result.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9가-힣]", "").toLowerCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record BindingAssemblyResult(
            List<ScreenFieldBinding> schemaBindings,
            List<ScreenFieldBinding> hintBindings,
            List<String> pkColumns) {
    }

    private record PageSelection(List<ScreenFieldBinding> fields, FieldSelectionSource source) {
    }
}
