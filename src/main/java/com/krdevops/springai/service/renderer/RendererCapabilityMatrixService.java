package com.krdevops.springai.service.renderer;

import com.krdevops.springai.model.renderer.RendererCapabilityRequirement;
import com.krdevops.springai.model.renderer.RendererFallback;
import com.krdevops.springai.model.renderer.RendererFeature;
import com.krdevops.springai.model.renderer.RendererProfile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** RendererProfile 선언을 Feature 지원/Fallback 금지 Matrix로 변환하고 요청 충족 여부를 판정한다. */
@Service
public class RendererCapabilityMatrixService {

    public ProfileCapabilityResult inspect(RendererProfile profile) {
        if (profile == null) throw new IllegalArgumentException("profile은 필수입니다.");
        List<CapabilityIssue> issues = new ArrayList<>();
        Set<RendererFeature> supported = parseFeatures(profile.supportedFeatures(), issues);
        Set<RendererFallback> forbidden = parseFallbacks(profile.forbiddenFallbacks(), issues);
        EnumMap<RendererFeature, Boolean> featureMatrix = new EnumMap<>(RendererFeature.class);
        for (RendererFeature feature : RendererFeature.values()) {
            featureMatrix.put(feature, supported.contains(feature));
        }
        EnumMap<RendererFallback, Boolean> fallbackMatrix = new EnumMap<>(RendererFallback.class);
        for (RendererFallback fallback : RendererFallback.values()) {
            fallbackMatrix.put(fallback, forbidden.contains(fallback));
        }
        return new ProfileCapabilityResult(
                profile.profileId(), profile.version(), featureMatrix, fallbackMatrix, issues);
    }

    public CapabilityAssessment assess(
            RendererProfile profile, RendererCapabilityRequirement requirement) {
        if (requirement == null) throw new IllegalArgumentException("requirement는 필수입니다.");
        ProfileCapabilityResult matrix = inspect(profile);
        List<CapabilityIssue> issues = new ArrayList<>(matrix.issues());
        requirement.requiredFeatures().stream()
                .filter(feature -> !matrix.supportedFeatures().getOrDefault(feature, false))
                .sorted()
                .forEach(feature -> issues.add(new CapabilityIssue(
                        "RENDERER_FEATURE_UNSUPPORTED", Severity.ERROR, feature.name(),
                        "생성 요청에 필요한 Feature를 RendererProfile이 지원하지 않습니다.")));
        requirement.attemptedFallbacks().stream()
                .filter(fallback -> matrix.forbiddenFallbacks().getOrDefault(fallback, false))
                .sorted()
                .forEach(fallback -> issues.add(new CapabilityIssue(
                        "RENDERER_FALLBACK_FORBIDDEN", Severity.ERROR, fallback.name(),
                        "RendererProfile이 금지한 fallback은 생성에 사용할 수 없습니다.")));
        return new CapabilityAssessment(matrix, requirement, issues);
    }

    public CapabilityAssessment requireSupported(
            RendererProfile profile, RendererCapabilityRequirement requirement) {
        CapabilityAssessment result = assess(profile, requirement);
        if (!result.supported()) throw new RendererCapabilityException(result);
        return result;
    }

    private Set<RendererFeature> parseFeatures(
            List<String> values, List<CapabilityIssue> issues) {
        EnumSet<RendererFeature> result = EnumSet.noneOf(RendererFeature.class);
        for (String value : values) {
            try {
                result.add(RendererFeature.valueOf(value));
            } catch (IllegalArgumentException exception) {
                issues.add(new CapabilityIssue("RENDERER_FEATURE_UNKNOWN", Severity.ERROR, value,
                        "알 수 없는 Renderer Feature입니다."));
            }
        }
        return result;
    }

    private Set<RendererFallback> parseFallbacks(
            List<String> values, List<CapabilityIssue> issues) {
        EnumSet<RendererFallback> result = EnumSet.noneOf(RendererFallback.class);
        for (String value : values) {
            try {
                result.add(RendererFallback.valueOf(value));
            } catch (IllegalArgumentException exception) {
                issues.add(new CapabilityIssue("RENDERER_FALLBACK_UNKNOWN", Severity.ERROR, value,
                        "알 수 없는 Renderer fallback입니다."));
            }
        }
        return result;
    }

    public enum Severity { WARNING, ERROR }

    public record CapabilityIssue(String code, Severity severity, String target, String message) {}

    public record ProfileCapabilityResult(
            String profileId,
            String version,
            Map<RendererFeature, Boolean> supportedFeatures,
            Map<RendererFallback, Boolean> forbiddenFallbacks,
            List<CapabilityIssue> issues
    ) {
        public ProfileCapabilityResult {
            supportedFeatures = Map.copyOf(supportedFeatures);
            forbiddenFallbacks = Map.copyOf(forbiddenFallbacks);
            issues = List.copyOf(issues);
        }

        public boolean valid() {
            return issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR);
        }
    }

    public record CapabilityAssessment(
            ProfileCapabilityResult matrix,
            RendererCapabilityRequirement requirement,
            List<CapabilityIssue> issues
    ) {
        public CapabilityAssessment {
            issues = List.copyOf(issues);
        }

        public boolean supported() {
            return issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR);
        }
    }

    public static final class RendererCapabilityException extends IllegalStateException {
        private final CapabilityAssessment assessment;

        public RendererCapabilityException(CapabilityAssessment assessment) {
            super("Renderer Capability 요구사항을 충족하지 못했습니다: "
                    + assessment.matrix().profileId() + "@" + assessment.matrix().version());
            this.assessment = assessment;
        }

        public CapabilityAssessment assessment() {
            return assessment;
        }
    }
}
