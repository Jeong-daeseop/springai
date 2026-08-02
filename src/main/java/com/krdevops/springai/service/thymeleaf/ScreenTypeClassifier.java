package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.PageSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * I-4A: 화면 유형(archetype, featureType) 판정.
 * ScreenSpecification의 구조와 데이터 소스 분석으로 LIST/FORM/DETAIL/DASHBOARD 및
 * CRUD/BOARD/MASTER_DETAIL 판정을 수행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScreenTypeClassifier {

    /**
     * archetype 판정: LIST, FORM, DETAIL, DASHBOARD
     *
     * @param spec ScreenSpecification
     * @return ArchetypeDecision (유형 + 신뢰도 + 이유)
     */
    public ArchetypeDecision determineArchetype(ScreenSpecification spec) {
        if (spec == null || spec.pages() == null || spec.pages().isEmpty()) {
            return new ArchetypeDecision(
                "FORM",
                0.5,
                "사용 가능한 PageSpec 없음. 기본값 FORM 선택."
            );
        }

        var pages = spec.pages();
        var pageCount = pages.size();

        // 1. 단일 페이지인 경우
        if (pageCount == 1) {
            var page = pages.get(0);
            return classifySinglePageArchetype(page, spec);
        }

        // 2. 다중 페이지인 경우
        return classifyMultiPageArchetype(pages, spec);
    }

    /**
     * featureType 판정: CRUD, BOARD, MASTER_DETAIL, DASHBOARD
     *
     * @param spec ScreenSpecification
     * @return FeatureTypeDecision (유형 + 신뢰도 + 이유)
     */
    public FeatureTypeDecision determineFeatureType(ScreenSpecification spec) {
        if (spec == null || spec.primaryTable() == null || spec.primaryTable().isEmpty()) {
            return new FeatureTypeDecision(
                "BOARD",
                0.5,
                "primaryTable 없음. 기본값 BOARD 선택 (자유 형식)."
            );
        }

        // 1. archetype 판정 결과 활용
        var archetypeDecision = determineArchetype(spec);

        // 2. 데이터 소스 구조 분석
        var dataSourceCount = spec.dataSources() != null ? spec.dataSources().size() : 1;

        // 3. featureType 결정
        if ("DASHBOARD".equals(archetypeDecision.archetype())) {
            return new FeatureTypeDecision(
                "DASHBOARD",
                archetypeDecision.confidence(),
                "Dashboard archetype → DASHBOARD featureType"
            );
        }

        if (dataSourceCount > 1) {
            return new FeatureTypeDecision(
                "MASTER_DETAIL",
                0.85,
                "다중 데이터 소스(" + dataSourceCount + ") → MASTER_DETAIL"
            );
        }

        // 기본값: CRUD
        return new FeatureTypeDecision(
            "CRUD",
            0.8,
            "단일 primaryTable + 단순 구조 → CRUD"
        );
    }

    private ArchetypeDecision classifySinglePageArchetype(PageSpec page, ScreenSpecification spec) {
        if (page == null) {
            return new ArchetypeDecision("FORM", 0.5, "PageSpec null");
        }

        var template = page.template();
        var fieldCount = page.fields() != null ? page.fields().size() : 0;

        // 1. template이 명시된 경우
        if ("LIST".equalsIgnoreCase(template)) {
            return new ArchetypeDecision(
                "LIST",
                0.95,
                "PageSpec.template = LIST (명시적)"
            );
        }

        if ("FORM".equalsIgnoreCase(template)) {
            return new ArchetypeDecision(
                "FORM",
                0.95,
                "PageSpec.template = FORM (명시적)"
            );
        }

        if ("DETAIL".equalsIgnoreCase(template)) {
            return new ArchetypeDecision(
                "DETAIL",
                0.95,
                "PageSpec.template = DETAIL (명시적)"
            );
        }

        // 2. template이 없거나 미지정인 경우 필드 수 기반 판정
        if (fieldCount <= 5) {
            return new ArchetypeDecision(
                "FORM",
                0.7,
                "필드 수(" + fieldCount + ") ≤ 5 → FORM 추정"
            );
        }

        if (fieldCount > 10) {
            return new ArchetypeDecision(
                "LIST",
                0.7,
                "필드 수(" + fieldCount + ") > 10 → LIST 추정"
            );
        }

        // 기본값
        return new ArchetypeDecision(
            "FORM",
            0.6,
            "필드 수(" + fieldCount + ") 6~10 → FORM 기본값"
        );
    }

    private ArchetypeDecision classifyMultiPageArchetype(java.util.List<PageSpec> pages, ScreenSpecification spec) {
        var templates = pages.stream()
            .map(PageSpec::template)
            .distinct()
            .toList();

        // 명시적 template이 다양한 경우: 다중 페이지 화면 (DASHBOARD 또는 FORM/LIST 혼합)
        if (templates.contains("LIST") && templates.contains("FORM")) {
            return new ArchetypeDecision(
                "DASHBOARD",
                0.75,
                "다중 template (LIST + FORM 등) → DASHBOARD"
            );
        }

        // 기본값
        return new ArchetypeDecision(
            "DASHBOARD",
            0.7,
            "다중 페이지(" + pages.size() + ") → DASHBOARD"
        );
    }

    public record ArchetypeDecision(
            String archetype,
            double confidence,
            String reasoning
    ) {}

    public record FeatureTypeDecision(
            String featureType,
            double confidence,
            String reasoning
    ) {}
}