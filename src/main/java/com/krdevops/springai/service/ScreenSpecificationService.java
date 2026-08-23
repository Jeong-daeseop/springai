package com.krdevops.springai.service;

import com.krdevops.springai.mapper.ScreenSpecRepository;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.FieldSourceType;
import com.krdevops.springai.model.design.SpecIssue;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.UiDesignSpecV2;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.HashMap;
import com.krdevops.springai.model.design.DataSourceSpec;

@Service
public class ScreenSpecificationService {

    private final CrudSchemaQueryService schemaQueryService;
    private final ScreenSpecAssembler assembler;
    private final ScreenDataBindingResolver dataBindingResolver;
    private final ScreenSpecValidator validator;
    private final ScreenSpecRepository repository;
    private final UiDesignSpecV2ToV1Projection v2Projection;
    private final UiDesignSpecV2QualityValidator v2QualityValidator;

    @Autowired
    public ScreenSpecificationService(
            CrudSchemaQueryService schemaQueryService,
            ScreenSpecAssembler assembler,
            ScreenDataBindingResolver dataBindingResolver,
            ScreenSpecValidator validator,
            ScreenSpecRepository repository,
            UiDesignSpecV2ToV1Projection v2Projection,
            UiDesignSpecV2QualityValidator v2QualityValidator) {
        this.schemaQueryService = schemaQueryService;
        this.assembler = assembler;
        this.dataBindingResolver = dataBindingResolver;
        this.validator = validator;
        this.repository = repository;
        this.v2Projection = v2Projection;
        this.v2QualityValidator = v2QualityValidator;
    }

    /** v2 의존성 도입 전 단위 테스트·Java 호출자 호환. v1 메서드만 사용할 수 있다. */
    public ScreenSpecificationService(
            CrudSchemaQueryService schemaQueryService,
            ScreenSpecAssembler assembler,
            ScreenDataBindingResolver dataBindingResolver,
            ScreenSpecValidator validator,
            ScreenSpecRepository repository) {
        this(schemaQueryService, assembler, dataBindingResolver, validator, repository, null, null);
    }

    public ScreenSpecification create(
            String database, String tableName, String screenName, String featureType, UiDesignSpec uiSpec) {
        return create(database, tableName, screenName, featureType, uiSpec, null, null);
    }

    public ScreenSpecification create(
            String database, String tableName, String screenName, String featureType, UiDesignSpec uiSpec,
            List<String> listColumns, List<String> detailColumns) {
        List<Map<String, Object>> columns = schemaQueryService.fetchColumns(database, tableName);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("테이블이 존재하지 않거나 컬럼이 없습니다: " + database + "." + tableName);
        }
        ScreenSpecification specification = assembler.assemble(
                database, tableName, screenName, featureType, columns, uiSpec, listColumns, detailColumns);
        specification = validator.validate(dataBindingResolver.resolve(specification));
        repository.save(specification);
        return specification;
    }

    public ScreenSpecification createFromV2(
            String database, String tableName, String screenName, String featureType,
            UiDesignSpecV2 uiSpec, List<String> listColumns, List<String> detailColumns) {
        if (uiSpec == null) throw new IllegalArgumentException("UiDesignSpecV2는 필수입니다.");
        if (v2Projection == null || v2QualityValidator == null) {
            throw new IllegalStateException("UiDesignSpecV2 생성 의존성이 구성되지 않았습니다.");
        }
        List<Map<String, Object>> columns = schemaQueryService.fetchColumns(database, tableName);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("테이블이 존재하지 않거나 컬럼이 없습니다: "
                    + database + "." + tableName);
        }
        ScreenSpecification specification = assembler.assemble(
                database, tableName, screenName, featureType, columns,
                v2Projection.project(uiSpec, featureType), listColumns, detailColumns);
        VersionedArtifactReference designRef = new VersionedArtifactReference(
                uiSpec.specId(), "UI_DESIGN_SPEC_V2", uiSpec.schemaVersion(),
                uiSpec.contentHash(), uiSpec.source().sourceRevision());
        specification = specification.withDesignContext(
                designRef, uiSpec.designSystemSnapshotRef(), qualityIssues(uiSpec));
        specification = validator.validate(dataBindingResolver.resolve(specification));
        repository.save(specification);
        return specification;
    }

    public ScreenSpecification createFromV2(
            String database, String tableName, String screenName, String featureType,
            UiDesignSpecV2 uiSpec) {
        return createFromV2(database, tableName, screenName, featureType, uiSpec, null, null);
    }

    public ScreenSpecification get(String specificationId) {
        return repository.findLatest(specificationId)
                .orElseThrow(() -> new IllegalArgumentException("화면명세를 찾을 수 없습니다: " + specificationId));
    }

    public ScreenSpecification approve(String specificationId) {
        ScreenSpecification approved = validator.approve(get(specificationId));
        repository.save(approved);
        return approved;
    }

    public ScreenSpecification revalidate(ScreenSpecification specification) {
        return validator.validate(specification);
    }

    public ScreenSpecification revise(ScreenSpecification proposed) {
        if (proposed == null || proposed.id() == null || proposed.id().isBlank()) {
            throw new IllegalArgumentException("수정할 화면명세 ID가 필요합니다.");
        }
        ScreenSpecification current = get(proposed.id());
        if (!current.database().equalsIgnoreCase(proposed.database())
                || !current.primaryTable().equalsIgnoreCase(proposed.primaryTable())) {
            throw new IllegalArgumentException("화면명세 수정 시 주 데이터 소스를 변경할 수 없습니다.");
        }
        List<Map<String, Object>> columns = schemaQueryService.fetchColumns(
                proposed.database(), proposed.primaryTable());
        List<SpecIssue> issues = new ArrayList<>();
        Map<String, Set<String>> columnsByAlias = new HashMap<>();
        columnsByAlias.put("t", columnNames(columns));
        for (DataSourceSpec dataSource : proposed.dataSources()) {
            if (dataSource.alias() == null || dataSource.alias().isBlank()) continue;
            if (dataSource.primary()) {
                if (!current.database().equalsIgnoreCase(dataSource.schema())
                        || !current.primaryTable().equalsIgnoreCase(dataSource.table())) {
                    issues.add(new SpecIssue(
                            "PRIMARY_DATA_SOURCE_IMMUTABLE", SpecIssue.Severity.ERROR,
                            "주 데이터 소스는 수정할 수 없습니다.", null));
                }
                columnsByAlias.put(dataSource.alias(), columnNames(columns));
            } else if (schemaQueryService.tableExists(dataSource.schema(), dataSource.table())) {
                columnsByAlias.put(dataSource.alias(), columnNames(schemaQueryService.fetchColumns(
                        dataSource.schema(), dataSource.table())));
            } else {
                issues.add(new SpecIssue(
                        "UNKNOWN_JOIN_TABLE", SpecIssue.Severity.ERROR,
                        "JOIN 테이블이 존재하지 않습니다: " + dataSource.schema() + "." + dataSource.table(), null));
            }
        }
        proposed.pages().stream().flatMap(page -> page.fields().stream())
                .filter(field -> field.source() != null)
                .filter(field -> field.source().type() == FieldSourceType.COLUMN
                        || field.source().type() == FieldSourceType.COMMON_CODE
                        || field.source().type() == FieldSourceType.JOIN_COLUMN)
                .filter(field -> !knownColumn(columnsByAlias, field.source().tableAlias(), field.source().column()))
                .forEach(field -> issues.add(new SpecIssue(
                        "UNKNOWN_COLUMN", SpecIssue.Severity.ERROR,
                        "데이터 소스에 존재하지 않는 컬럼입니다: "
                                + field.source().tableAlias() + "." + field.source().column(), field.id())));
        ScreenSpecification revision = new ScreenSpecification(
                current.id(), current.version() + 1, ScreenSpecStatus.DRAFT,
                proposed.screenName(), proposed.featureType(), proposed.archetype(),
                current.database(), current.primaryTable(), proposed.dataSources(), proposed.pages(),
                issues, current.layoutDensity(), current.formColumnLayout(),
                current.actionPlacement(), current.searchPanelPlacement(), LocalDateTime.now(),
                current.uiDesignSpecReference(), current.designSystemSnapshotReference());
        ScreenSpecification validated = validator.validate(revision);
        repository.save(validated);
        return validated;
    }

    private Set<String> columnNames(List<Map<String, Object>> columns) {
        return columns.stream()
                .map(column -> {
                    Object value = column.get("COLUMN_NAME");
                    if (value == null) value = column.get("column_name");
                    return value == null ? "" : value.toString().toUpperCase(Locale.ROOT);
                })
                .collect(Collectors.toSet());
    }

    private boolean knownColumn(Map<String, Set<String>> columnsByAlias, String alias, String column) {
        if (alias == null || column == null) return false;
        Set<String> columns = columnsByAlias.get(alias);
        return columns != null && columns.contains(column.toUpperCase(Locale.ROOT));
    }

    private List<SpecIssue> qualityIssues(UiDesignSpecV2 uiSpec) {
        return v2QualityValidator.validateForAutoApproval(uiSpec).issues().stream()
                .map(issue -> new SpecIssue(
                        issue.code().name(),
                        issue.severity() == UiDesignSpecV2QualityValidator.Severity.BLOCK
                                ? SpecIssue.Severity.ERROR : SpecIssue.Severity.WARNING,
                        issue.message(), issue.target()))
                .toList();
    }
}
