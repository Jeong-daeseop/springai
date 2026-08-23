package com.krdevops.springai.service.generation;

import com.krdevops.springai.service.generation.model.FileBlueprint;
import com.krdevops.springai.service.generation.model.GenerationBlueprint;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 파일 계획을 Scope Manifest의 네 가지 범주로 결정적으로 분류한다. */
@Service
public class GenerationScopeClassifier {

    private static final java.util.Set<String> VALIDATION_ONLY_LAYERS = java.util.Set.of(
            "controlleradvice");
    private static final java.util.Set<String> DEPENDENCY_LAYERS = java.util.Set.of(
            "layoutHtml", "layoutGnbHtml", "layoutLnbHtml", "layoutBreadcrumbHtml",
            "layoutFooterHtml", "layoutGnbMenuVo", "layoutGnbMenuMapper",
            "layoutGnbMenuMapperXml", "layoutGnbMenuInterceptor");
    private static final java.util.Set<String> ROOT_LAYERS = java.util.Set.of(
            "vo", "mapper", "mapperXml", "service", "serviceImpl", "controller",
            "jspList", "jspDetail", "jspRegist", "jspUpdt", "thymeleafList",
            "thymeleafDetail", "thymeleafRegist", "thymeleafUpdt");

    public ScopeClassification classify(GenerationBlueprint blueprint) {
        if (blueprint == null) throw new IllegalArgumentException("GenerationBlueprint는 필수입니다.");
        return classify(blueprint.files());
    }

    public ScopeClassification classify(List<FileBlueprint> files) {
        EnumMap<Category, java.util.ArrayList<FileBlueprint>> grouped = new EnumMap<>(Category.class);
        for (Category category : Category.values()) grouped.put(category, new java.util.ArrayList<>());
        for (FileBlueprint file : files == null ? List.<FileBlueprint>of() : files) {
            if (file == null) throw new IllegalArgumentException("파일 계획에 null 항목이 있습니다.");
            grouped.get(classifyLayer(file.layerKey())).add(file);
        }
        return new ScopeClassification(
                grouped.get(Category.ROOT), grouped.get(Category.DEPENDENCY),
                grouped.get(Category.VALIDATION_ONLY), grouped.get(Category.PRESERVED));
    }

    /** 알 수 없는 layer는 사용자 작성 영역으로 보수적으로 보존한다. */
    public Category classifyLayer(String layerKey) {
        if (layerKey == null || layerKey.isBlank()) {
            throw new IllegalArgumentException("layerKey는 필수입니다.");
        }
        if (VALIDATION_ONLY_LAYERS.contains(layerKey)) return Category.VALIDATION_ONLY;
        if (DEPENDENCY_LAYERS.contains(layerKey) || layerKey.startsWith("layout")) {
            return Category.DEPENDENCY;
        }
        if (ROOT_LAYERS.contains(layerKey)) return Category.ROOT;
        if (layerKey.startsWith("validation")) return Category.VALIDATION_ONLY;
        if (layerKey.startsWith("preserved")) return Category.PRESERVED;
        return Category.PRESERVED;
    }

    public enum Category { ROOT, DEPENDENCY, VALIDATION_ONLY, PRESERVED }

    public record ScopeClassification(
            List<FileBlueprint> root,
            List<FileBlueprint> dependency,
            List<FileBlueprint> validationOnly,
            List<FileBlueprint> preserved
    ) {
        public ScopeClassification {
            root = List.copyOf(root == null ? List.of() : root);
            dependency = List.copyOf(dependency == null ? List.of() : dependency);
            validationOnly = List.copyOf(validationOnly == null ? List.of() : validationOnly);
            preserved = List.copyOf(preserved == null ? List.of() : preserved);
        }

        public List<FileBlueprint> all() {
            return java.util.stream.Stream.of(root, dependency, validationOnly, preserved)
                    .flatMap(List::stream).toList();
        }

        public Map<Category, List<FileBlueprint>> byCategory() {
            EnumMap<Category, List<FileBlueprint>> result = new EnumMap<>(Category.class);
            result.put(Category.ROOT, root);
            result.put(Category.DEPENDENCY, dependency);
            result.put(Category.VALIDATION_ONLY, validationOnly);
            result.put(Category.PRESERVED, preserved);
            return Map.copyOf(result);
        }
    }
}
