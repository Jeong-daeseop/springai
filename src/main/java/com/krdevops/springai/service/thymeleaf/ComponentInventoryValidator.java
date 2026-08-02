package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * I-4A: Component Inventory 검증 및 선택.
 * ComponentRegistry에서 사용 가능한 컴포넌트를 확인하고,
 * 요청된 컴포넌트를 검증하거나 fallback을 제시한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentInventoryValidator {

    private final ComponentRegistryRepository componentRegistryRepository;

    /**
     * ComponentRegistry 검증: 요청된 profileId와 버전이 유효한지 확인.
     *
     * @param profileId Design System Profile ID
     * @param registryVersion Registry 버전
     * @return ComponentValidationResult
     */
    public ComponentValidationResult validateRegistry(String profileId, String registryVersion) {
        if (profileId == null || profileId.isEmpty()) {
            return new ComponentValidationResult(
                false,
                List.of("profileId이 비어있음"),
                null
            );
        }

        try {
            var registry = componentRegistryRepository.findVersion(profileId, registryVersion);
            if (registry.isEmpty()) {
                return new ComponentValidationResult(
                    false,
                    List.of("ComponentRegistry를 찾을 수 없음: " + profileId + " v" + registryVersion),
                    null
                );
            }

            return new ComponentValidationResult(
                true,
                List.of(),
                registry.get()
            );
        } catch (Exception e) {
            return new ComponentValidationResult(
                false,
                List.of("Registry 조회 실패: " + e.getMessage()),
                null
            );
        }
    }

    /**
     * 요청된 컴포넌트 키 목록의 가용성 확인.
     *
     * @param registry ComponentRegistry
     * @param requestedComponentKeys 요청 컴포넌트 키 목록
     * @return ComponentSelectionResult (선택된 컴포넌트 + fallback)
     */
    public ComponentSelectionResult resolveComponentSelection(
            ComponentRegistry registry,
            List<String> requestedComponentKeys) {

        if (registry == null || registry.components() == null || registry.components().isEmpty()) {
            return new ComponentSelectionResult(
                List.of(),
                List.of("INPUT_FIELD", "BUTTON", "TABLE"), // 기본 fallback
                Map.of(),
                List.of("Registry 컴포넌트 없음, 기본 fallback 사용")
            );
        }

        if (requestedComponentKeys == null || requestedComponentKeys.isEmpty()) {
            return new ComponentSelectionResult(
                List.of(),
                getDefaultFallbackComponents(registry),
                Map.of(),
                List.of("요청 컴포넌트 없음, 기본 구성 사용")
            );
        }

        var selectedKeys = new java.util.ArrayList<String>();
        var fallbackKeys = new java.util.ArrayList<String>();
        var confidenceMap = new java.util.HashMap<String, Double>();
        var issues = new java.util.ArrayList<String>();

        var registryComponentKeys = registry.components().keySet();

        for (var requestedKey : requestedComponentKeys) {
            if (registryComponentKeys.contains(requestedKey)) {
                selectedKeys.add(requestedKey);
                confidenceMap.put(requestedKey, 0.95);
            } else {
                // Fallback 제시
                var fallback = findSimilarComponent(requestedKey, registry);
                if (fallback.isPresent()) {
                    fallbackKeys.add(fallback.get());
                    confidenceMap.put(requestedKey, 0.6);
                    issues.add("컴포넌트 '" + requestedKey + "'를 찾을 수 없음. "
                        + "Fallback: " + fallback.get());
                } else {
                    confidenceMap.put(requestedKey, 0.0);
                    issues.add("컴포넌트 '" + requestedKey + "'와 유사한 fallback 없음");
                }
            }
        }

        return new ComponentSelectionResult(
            selectedKeys,
            fallbackKeys.isEmpty() ? getDefaultFallbackComponents(registry) : fallbackKeys,
            confidenceMap,
            issues
        );
    }

    /**
     * 요청 컴포넌트와 유사한 컴포넌트 찾기 (이름 기반 유사도).
     *
     * @param requestedKey 요청 컴포넌트 키
     * @param registry ComponentRegistry
     * @return Optional의 유사 컴포넌트 키
     */
    private Optional<String> findSimilarComponent(String requestedKey, ComponentRegistry registry) {
        if (requestedKey == null || requestedKey.isEmpty()) {
            return Optional.empty();
        }

        var lowerRequested = requestedKey.toLowerCase();
        return registry.components().keySet().stream()
            .filter(key -> key.toLowerCase().contains(lowerRequested.substring(0, Math.min(3, lowerRequested.length()))))
            .findFirst();
    }

    /**
     * 기본 fallback 컴포넌트 목록 반환.
     *
     * @param registry ComponentRegistry
     * @return fallback 컴포넌트 키 목록
     */
    private List<String> getDefaultFallbackComponents(ComponentRegistry registry) {
        // Registry에 있는 기본 컴포넌트들 우선 사용
        var basicComponents = List.of("INPUT_FIELD", "BUTTON", "TABLE", "FORM_GROUP", "PANEL");
        return registry.components().keySet().stream()
            .filter(key -> basicComponents.stream().anyMatch(key.toUpperCase()::contains))
            .limit(3)
            .toList();
    }

    public record ComponentValidationResult(
            boolean isValid,
            List<String> issues,
            ComponentRegistry registry
    ) {}

    public record ComponentSelectionResult(
            List<String> selectedComponentKeys,
            List<String> fallbackComponentKeys,
            Map<String, Double> componentConfidence,
            List<String> issues
    ) {}
}