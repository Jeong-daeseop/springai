package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.design.role.SemanticRole;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.thymeleaf.ResolvedDesignTokens;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.service.thymeleaf.CompanyDesignTokenResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * ComponentRegistry(Figma "Publish"로 게시된 Variable)를 DESIGN.md YAML frontmatter로
 * 내보낸다.
 *
 * <p>Figma 프레임 raw 값을 다시 분석하지 않고, 이미 검증된 {@link CompanyDesignTokenResolver}의
 * 결과를 그대로 재사용한다 — 값은 항상 CSS 변수 이름이며 raw hex/px 값은 담기지 않는다. 이렇게
 * 내보낸 DESIGN.md는 ComponentRegistry의 내보내기 시점 스냅샷이며, ComponentRegistry가 갱신돼도
 * 자동으로 다시 반영되지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentRegistryToDesignMdExporter {

    private final CompanyDesignTokenResolver tokenResolver;
    private final DesignSystemQueryService designSystemQueryService;

    /**
     * @param profileId DesignSystemProfile 식별자
     * @return DESIGN.md 전체 콘텐츠(YAML frontmatter + 안내 본문). profileId를 찾을 수 없거나
     *         토큰 해석에 실패하면 안내 문구만 담긴 최소 DESIGN.md를 반환한다(예외를 던지지 않음).
     */
    public String export(String profileId) {
        ThymeleafGenerationStageResult<ResolvedDesignTokens> result = tokenResolver.resolve(profileId, null);
        if (!result.successful()) {
            log.warn("[design-md-export] 디자인 토큰 해석 실패, 빈 DESIGN.md 반환: profileId={}", profileId);
            return emptyDocument(profileId);
        }
        return render(profileId, result.value());
    }

    private String render(String profileId, ResolvedDesignTokens tokens) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("schemaVersion", "1.0");
        putIfNotEmpty(doc, "colors", tokens.colorTokens());
        putIfNotEmpty(doc, "typography", tokens.typographyTokens());
        putIfNotEmpty(doc, "spacing", tokens.spacingTokens());
        putIfNotEmpty(doc, "radius", tokens.radiusTokens());
        putIfNotEmpty(doc, "layout", tokens.layoutTokens());
        Map<String, Object> components = componentsSection(profileId);
        if (!components.isEmpty()) {
            doc.put("components", components);
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        String yaml = new Yaml(options).dump(doc);

        return "---\n" + yaml + "---\n\n"
                + "# DESIGN.md (자동 생성)\n\n"
                + "이 파일은 `ComponentRegistryToDesignMdExporter`가 profileId=`" + profileId
                + "`의 ComponentRegistry(Figma에 Publish된 Variable)에서 " + Instant.now()
                + "에 자동 내보낸 스냅샷입니다.\n\n"
                + "값은 모두 CSS 변수 **이름**이며 실제 색상/치수 값이 아닙니다 — 실제 값은 "
                + "`_ds_bundle.css`(KRDS 원본 자산)가 정의합니다.\n\n"
                + "ComponentRegistry가 갱신되면 이 파일은 자동으로 다시 반영되지 않습니다 — "
                + "최신 상태를 유지하려면 내보내기를 다시 실행하세요.\n";
    }

    /**
     * {@link CompanyDesignTokenResolver}는 {@code registry.variables()}만 다루고
     * {@code registry.components()}는 전혀 참조하지 않는다 — 여기서 별도로 조회해 DESIGN.md의
     * {@code components} 카테고리(DesignMdRuleLoader가 이미 지원)에 채운다. 현재 생성에 쓸 수
     * 있는(PUBLISHED + ACTIVE/CURRENT) 컴포넌트만 포함한다.
     */
    private Map<String, Object> componentsSection(String profileId) {
        ComponentRegistry registry;
        try {
            registry = designSystemQueryService.findLatestRegistry(profileId);
        } catch (IllegalArgumentException e) {
            log.warn("[design-md-export] ComponentRegistry 조회 실패, components 섹션 건너뜀: profileId={}",
                    profileId);
            return Map.of();
        }
        Map<String, Object> section = new TreeMap<>();
        for (Map.Entry<String, ComponentRegistryEntry> entry : registry.components().entrySet()) {
            ComponentRegistryEntry component = entry.getValue();
            if (!component.currentForGeneration()) {
                continue;
            }
            Map<String, Object> descriptor = new LinkedHashMap<>();
            if (component.codeComponent() != null && !component.codeComponent().isBlank()) {
                descriptor.put("codeComponent", component.codeComponent());
            }
            List<String> roleCodes = component.roles().stream()
                    .map(SemanticRole::code)
                    .sorted()
                    .toList();
            if (!roleCodes.isEmpty()) {
                descriptor.put("roles", roleCodes);
            }
            if (!descriptor.isEmpty()) {
                section.put(entry.getKey(), descriptor);
            }
        }
        return section;
    }

    private String emptyDocument(String profileId) {
        return "---\nschemaVersion: \"1.0\"\n---\n\n"
                + "# DESIGN.md (자동 생성 실패)\n\n"
                + "profileId=`" + profileId + "`에 대한 ComponentRegistry/DesignSystemProfile을 찾지 못해 "
                + "토큰 없이 생성되었습니다. 수동으로 채우거나 올바른 profileId로 다시 내보내세요.\n";
    }

    private static void putIfNotEmpty(Map<String, Object> doc, String key, Map<String, String> tokens) {
        if (tokens != null && !tokens.isEmpty()) {
            doc.put(key, new LinkedHashMap<>(tokens));
        }
    }
}
