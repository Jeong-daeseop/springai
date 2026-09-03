package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.VariableRegistryEntry;
import com.krdevops.springai.model.thymeleaf.AppliedDesignRules;
import com.krdevops.springai.model.thymeleaf.ResolvedDesignTokens;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.service.contract.GenerationIssueFactory;
import com.krdevops.springai.service.designsystem.DesignSystemQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * R6-056: Design Token 로드·매핑 서비스.
 *
 * <p>회사 표준 DesignSystemProfile에서 semantic token을 로드하고,
 * CSS Variable 및 Component Property로 해석하여 ResolvedDesignTokens를 생성한다.
 *
 * <p>처리 흐름:
 * <ul>
 *   <li>DesignSystemProfile 로드 (승인 상태 검증)
 *   <li>ComponentRegistry 로드
 *   <li>semantic token → CSS Variable 매핑
 *   <li>AppliedDesignRules (R6-055)과 병합 (우선순위 적용)
 *   <li>미매핑 token 감지 및 WARNING/ERROR 기록
 *   <li>ResolvedDesignTokens 생성
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyDesignTokenResolver {

    private final DesignSystemQueryService queryService;
    private final GenerationIssueFactory issueFactory;

    /**
     * 회사 표준 Design Token을 로드하고 해석한다.
     *
     * @param profileId 로드할 DesignSystemProfile ID
     * @param appliedDesignRules R6-055에서 로드한 프로젝트 DESIGN.md 규칙 (null 가능)
     * @return ResolvedDesignTokens를 포함한 ThymeleafGenerationStageResult.
     *         - 성공: successful=true (FATAL 없음)
     *         - 실패: successful=false (FATAL 있음)
     */
    public ThymeleafGenerationStageResult<ResolvedDesignTokens> resolve(
            String profileId,
            AppliedDesignRules appliedDesignRules) {
        log.info("Resolving design tokens: profileId={}", profileId);

        List<GenerationIssue> allIssues = new ArrayList<>();

        // 1. DesignSystemProfile 로드
        DesignSystemProfile profile;
        try {
            profile = queryService.findLatestProfile(profileId);
            log.debug("Loaded profile: id={}, version={}, status={}", profile.id(), profile.version(), profile.status());
        } catch (IllegalArgumentException e) {
            log.warn("Failed to load profile: {}", e.getMessage());
            GenerationIssue profileNotFound = issueFactory.fatal(
                    "DESIGN_SYSTEM_PROFILE_NOT_FOUND",
                    "R6-056",
                    "DesignSystemProfile을 찾을 수 없습니다: " + profileId
            );
            allIssues.add(profileNotFound);
            return ThymeleafGenerationStageResult.failure(allIssues);
        }

        // 2. ComponentRegistry 로드
        ComponentRegistry registry;
        try {
            registry = queryService.findLatestRegistry(profileId);
            log.debug("Loaded registry: version={}, components={}", registry.registryVersion(), registry.components().size());
        } catch (IllegalArgumentException e) {
            log.warn("Failed to load registry: {}", e.getMessage());
            GenerationIssue registryNotFound = issueFactory.fatal(
                    "COMPONENT_REGISTRY_NOT_FOUND",
                    "R6-056",
                    "ComponentRegistry를 찾을 수 없습니다: " + profileId
            );
            allIssues.add(registryNotFound);
            return ThymeleafGenerationStageResult.failure(allIssues);
        }

        // 3. Token 맵 구성 (기본값)
        Map<String, String> colorTokens = new HashMap<>();
        Map<String, String> typographyTokens = new HashMap<>();
        Map<String, String> spacingTokens = new HashMap<>();
        Map<String, String> radiusTokens = new HashMap<>();
        Map<String, String> layoutTokens = new HashMap<>();
        Map<String, ResolvedDesignTokens.ComponentPropertyTokens> componentTokens = new HashMap<>();

        // 4. Variable Registry에서 Token 추출
        // collectionKey와 variableKey를 조합하여 CSS Variable 이름 생성
        if (registry.variables() != null && !registry.variables().isEmpty()) {
            for (Map.Entry<String, VariableRegistryEntry> entry : registry.variables().entrySet()) {
                String logicalName = entry.getKey();
                VariableRegistryEntry varEntry = entry.getValue();

                // CSS Variable 이름 생성: --krds-{collection}-{variable}
                // 예: --krds-color-primary-60, --krds-spacing-sm
                String cssVarName = generateCssVariableName(varEntry, logicalName);

                // resolvedType에 따라 적절한 토큰 맵에 배치
                mapTokenByType(varEntry.resolvedType(), logicalName, cssVarName,
                        colorTokens, typographyTokens, spacingTokens, radiusTokens, layoutTokens);

                log.debug("Mapped token: logical={}, css={}, type={}", logicalName, cssVarName, varEntry.resolvedType());
            }
        }

        // 5. AppliedDesignRules가 제공되면 병합 (우선순위: AppliedDesignRules > Registry Token)
        if (appliedDesignRules != null && !appliedDesignRules.appliedRules().isEmpty()) {
            for (AppliedDesignRules.AppliedRule rule : appliedDesignRules.appliedRules()) {
                mapAppliedRuleByCategory(rule.category(), rule.key(), rule.value(),
                        colorTokens, typographyTokens, spacingTokens, radiusTokens, layoutTokens);
                log.debug("Applied design rule: category={}, key={}, value={}", rule.category(), rule.key(), rule.value());
            }
        }

        log.info("Design tokens resolved: color={}, typography={}, spacing={}, radius={}, layout={}, components={}",
                colorTokens.size(), typographyTokens.size(), spacingTokens.size(),
                radiusTokens.size(), layoutTokens.size(), componentTokens.size());

        ResolvedDesignTokens result = new ResolvedDesignTokens(
                profile.id(),
                profile.version(),
                appliedDesignRules != null ? appliedDesignRules.contentHash() : null,
                colorTokens,
                typographyTokens,
                spacingTokens,
                radiusTokens,
                layoutTokens,
                componentTokens,
                allIssues
        );

        if (allIssues.stream().anyMatch(i -> i.severity() == GenerationIssue.Severity.FATAL)) {
            return ThymeleafGenerationStageResult.failure(allIssues);
        }

        return ThymeleafGenerationStageResult.success(result, allIssues);
    }

    /**
     * VariableRegistryEntry로부터 CSS Variable 이름을 생성한다.
     *
     * @param varEntry VariableRegistryEntry
     * @param logicalName 논리 이름
     * @return CSS Variable 이름 (예: --krds-color-primary-60)
     */
    private String generateCssVariableName(VariableRegistryEntry varEntry, String logicalName) {
        // collectionName과 variableName(사람이 읽는 이름)을 조합하여 생성
        // 형식: --krds-{collection}-{variable}
        // 예: --krds-color-primary-60
        // 주의: variableKey는 Figma 내부 opaque ID(해시)라 사람이 읽을 수 없다 — 반드시
        // variableName을 써야 한다.
        String collection = sanitizeName(varEntry.collectionName());
        String variable = sanitizeName(varEntry.variableName());
        return "--krds-" + collection + "-" + variable;
    }

    /**
     * CSS Variable 이름으로 사용 가능하도록 이름을 정제한다.
     *
     * @param name 원본 이름
     * @return 정제된 이름 (소문자, 하이픈)
     */
    private String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        return name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    /**
     * resolvedType에 따라 Token을 적절한 맵에 배치한다.
     *
     * <p>Figma의 실제 {@code resolvedType}은 COLOR/FLOAT/STRING/BOOLEAN 정도로만 구분되고
     * spacing/radius/typography를 별도로 알려주지 않는다 — 그래서 FLOAT 타입인 gap/padding/
     * radius/size 변수가 전부 "미분류"로 색상에 잘못 떨어지는 문제가 있었다. COLOR만 타입으로
     * 신뢰하고, 그 외는 변수 이름(logicalName) 접두어로 분류한다.
     *
     * @param type resolvedType (color, typography 등)
     * @param logicalName 논리 이름
     * @param cssVarName CSS Variable 이름
     * @param colorTokens 색상 토큰 맵
     * @param typographyTokens 타이포그래피 토큰 맵
     * @param spacingTokens 간격 토큰 맵
     * @param radiusTokens 반경 토큰 맵
     * @param layoutTokens 레이아웃 토큰 맵
     */
    private void mapTokenByType(String type, String logicalName, String cssVarName,
                                 Map<String, String> colorTokens,
                                 Map<String, String> typographyTokens,
                                 Map<String, String> spacingTokens,
                                 Map<String, String> radiusTokens,
                                 Map<String, String> layoutTokens) {
        String typeKey = type == null ? "" : type.toLowerCase();
        String nameKey = logicalName == null ? "" : logicalName.toLowerCase();

        if (typeKey.contains("color")) {
            colorTokens.putIfAbsent(logicalName, cssVarName);
            return;
        }
        if (nameKey.contains("radius") || nameKey.contains("corner")) {
            radiusTokens.putIfAbsent(logicalName, cssVarName);
        } else if (nameKey.contains("gap") || nameKey.contains("padding") || nameKey.contains("margin")
                || nameKey.contains("spacing") || nameKey.contains("size")) {
            spacingTokens.putIfAbsent(logicalName, cssVarName);
        } else if (nameKey.contains("font") || nameKey.contains("typo") || nameKey.contains("letter-spacing")
                || nameKey.contains("line-height") || nameKey.contains("weight")) {
            typographyTokens.putIfAbsent(logicalName, cssVarName);
        } else if (nameKey.contains("layout") || nameKey.contains("grid") || nameKey.contains("breakpoint")) {
            layoutTokens.putIfAbsent(logicalName, cssVarName);
        } else if (typeKey.contains("typography") || typeKey.contains("font")) {
            typographyTokens.putIfAbsent(logicalName, cssVarName);
        } else if (typeKey.contains("spacing") || typeKey.contains("size")) {
            spacingTokens.putIfAbsent(logicalName, cssVarName);
        } else if (typeKey.contains("radius") || typeKey.contains("corner")) {
            radiusTokens.putIfAbsent(logicalName, cssVarName);
        } else if (typeKey.contains("layout") || typeKey.contains("grid")) {
            layoutTokens.putIfAbsent(logicalName, cssVarName);
        } else {
            // 이름·타입 어느 쪽으로도 분류가 안 되는 진짜 미분류 타입만 색상에 임시로 배치
            colorTokens.putIfAbsent(logicalName, cssVarName);
            log.warn("Unknown token type: {} for {}", type, logicalName);
        }
    }

    /**
     * AppliedDesignRules의 category에 따라 Token을 적절한 맵에 병합한다.
     *
     * @param category 규칙 카테고리 (colors, typography 등)
     * @param key 규칙 키
     * @param value 규칙 값 (CSS Variable 이름 또는 값)
     * @param colorTokens 색상 토큰 맵
     * @param typographyTokens 타이포그래피 토큰 맵
     * @param spacingTokens 간격 토큰 맵
     * @param radiusTokens 반경 토큰 맵
     * @param layoutTokens 레이아웃 토큰 맵
     */
    private void mapAppliedRuleByCategory(String category, String key, String value,
                                          Map<String, String> colorTokens,
                                          Map<String, String> typographyTokens,
                                          Map<String, String> spacingTokens,
                                          Map<String, String> radiusTokens,
                                          Map<String, String> layoutTokens) {
        if (category == null || key == null || value == null) {
            return;
        }

        String categoryKey = category.toLowerCase();
        if (categoryKey.contains("color")) {
            // 이미 존재하면 override (AppliedDesignRules 우선순위)
            colorTokens.put(key, value);
        } else if (categoryKey.contains("typography") || categoryKey.contains("font")) {
            typographyTokens.put(key, value);
        } else if (categoryKey.contains("spacing") || categoryKey.contains("size")) {
            spacingTokens.put(key, value);
        } else if (categoryKey.contains("radius") || categoryKey.contains("corner")) {
            radiusTokens.put(key, value);
        } else if (categoryKey.contains("layout") || categoryKey.contains("grid")) {
            layoutTokens.put(key, value);
        } else {
            log.warn("Unknown design rule category: {} for key={}", category, key);
        }
    }
}
