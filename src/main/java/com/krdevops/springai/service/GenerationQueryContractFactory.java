package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.design.DataSourceSpec;
import com.krdevops.springai.model.design.FieldSourceType;
import com.krdevops.springai.model.design.GenerationJoinModel;
import com.krdevops.springai.model.design.GenerationProjectionModel;
import com.krdevops.springai.model.design.GenerationQueryContract;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 승인 화면명세를 결정론적 FTL SELECT/VO 모델로 변환한다. */
@Service
public class GenerationQueryContractFactory {

    public GenerationQueryContract create(ScreenSpecification specification, List<FieldModel> physicalFields) {
        return create(specification, physicalFields, "t", Set.of("t"));
    }

    public GenerationQueryContract create(
            ScreenSpecification specification, List<FieldModel> physicalFields, String primaryAlias) {
        return create(specification, physicalFields, primaryAlias, Set.of(primaryAlias));
    }

    public GenerationQueryContract create(
            ScreenSpecification specification,
            List<FieldModel> physicalFields,
            String primaryAlias,
            Set<String> reservedAliases) {
        if (specification == null) return GenerationQueryContract.empty();
        List<ScreenFieldBinding> listFields = specification.pages().stream()
                .filter(page -> "list".equalsIgnoreCase(page.id()))
                .findFirst().map(PageSpec::fields).orElse(List.of());
        List<GenerationJoinModel> joins = new ArrayList<>();
        Set<String> aliases = new HashSet<>(reservedAliases);
        for (DataSourceSpec source : specification.dataSources()) {
            if (source.primary()) continue;
            if (!aliases.add(source.alias())) {
                throw new IllegalArgumentException("생성 SQL alias가 예약 alias와 충돌합니다: " + source.alias());
            }
            joins.add(new GenerationJoinModel(
                    source.joinType().toUpperCase(Locale.ROOT), source.schema(), source.table(),
                    source.alias(), replacePrimaryAlias(source.joinExpression(), primaryAlias)));
        }

        List<GenerationProjectionModel> projections = new ArrayList<>();
        Set<String> javaNames = new HashSet<>();
        physicalFields.forEach(field -> javaNames.add(field.javaName()));
        int codeIndex = 1;
        for (ScreenFieldBinding binding : listFields) {
            if (!binding.visible() || binding.source() == null) continue;
            FieldSourceType type = binding.source().type();
            if (type == FieldSourceType.JOIN_COLUMN && javaNames.add(binding.id())) {
                FieldModel field = displayField(binding);
                projections.add(new GenerationProjectionModel(field,
                        binding.source().tableAlias() + "." + binding.source().column()
                                + " AS " + binding.id()));
            } else if (type == FieldSourceType.COMMON_CODE && javaNames.add(binding.id())) {
                String alias;
                do {
                    alias = "cc" + codeIndex++;
                } while (aliases.contains(alias));
                aliases.add(alias);
                joins.add(new GenerationJoinModel(
                        "LEFT", specification.database(), "LETTCCMMNDETAILCODE", alias,
                        replaceAlias(binding.source().tableAlias(), primaryAlias) + "." + binding.source().column()
                                + " = " + alias + ".CODE AND " + alias + ".CODE_ID = '"
                                + binding.source().codeGroup() + "'"));
                FieldModel field = displayField(binding);
                projections.add(new GenerationProjectionModel(field,
                        alias + ".CODE_NM AS " + binding.id()));
            }
        }
        return new GenerationQueryContract(joins, projections);
    }

    /**
     * screenSpecification의 pageId 페이지가 COLUMN 소스 필드에 부여한 커스텀 라벨(예: "답변상태")로
     * pageFields의 comment(=화면 표시 라벨)를 덮어쓴다. 스펙이 없거나 해당 컬럼에 라벨이 없으면
     * 원래 DB COLUMN_COMMENT를 그대로 유지한다.
     */
    public List<FieldModel> applyLabelOverrides(
            List<FieldModel> pageFields, ScreenSpecification specification, String pageId) {
        if (specification == null) return pageFields;
        Map<String, String> labelByColumn = specification.pages().stream()
                .filter(page -> pageId.equalsIgnoreCase(page.id()))
                .flatMap(page -> page.fields().stream())
                .filter(binding -> binding.source() != null
                        && binding.source().type() == FieldSourceType.COLUMN
                        && binding.source().column() != null)
                .filter(binding -> binding.label() != null && !binding.label().isBlank())
                .collect(Collectors.toMap(
                        binding -> binding.source().column().toUpperCase(Locale.ROOT),
                        ScreenFieldBinding::label,
                        (first, second) -> first));
        if (labelByColumn.isEmpty()) return pageFields;

        return pageFields.stream()
                .map(field -> {
                    String label = labelByColumn.get(field.columnName().toUpperCase(Locale.ROOT));
                    if (label == null || label.equals(field.comment())) return field;
                    return new FieldModel(field.columnName(), field.javaName(), field.javaType(),
                            label, field.pk(), field.required(), field.stringType(),
                            field.maxLength(), field.jdbcType());
                })
                .toList();
    }

    private String replacePrimaryAlias(String expression, String primaryAlias) {
        return "t".equals(primaryAlias) ? expression
                : expression.replaceAll("(?<![A-Za-z0-9_])t\\.", primaryAlias + ".");
    }

    private String replaceAlias(String alias, String primaryAlias) {
        return "t".equals(alias) ? primaryAlias : alias;
    }

    private FieldModel displayField(ScreenFieldBinding binding) {
        return new FieldModel(
                binding.id(), binding.id(), "String", binding.label(), false,
                false, true, null, "VARCHAR");
    }
}
