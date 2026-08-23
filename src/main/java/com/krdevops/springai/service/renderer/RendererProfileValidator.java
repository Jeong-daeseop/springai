package com.krdevops.springai.service.renderer;

import com.krdevops.springai.model.renderer.RendererProfile;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** RendererProfile의 현재 SpringAI 생성 Target 지원 여부와 Apply 승인 상태를 검증한다. */
@Service
public class RendererProfileValidator {

    private static final Set<String> SUPPORTED_VIEW_TYPES = Set.of("THYMELEAF");
    private final TemplateSetFingerprintService fingerprintService;
    private final RendererCapabilityMatrixService capabilityMatrixService;
    private final ValidatorProfileLoader validatorProfileLoader;

    public RendererProfileValidator() {
        this(null, null, null);
    }

    public RendererProfileValidator(TemplateSetFingerprintService fingerprintService) {
        this(fingerprintService, null, null);
    }

    public RendererProfileValidator(
            TemplateSetFingerprintService fingerprintService,
            RendererCapabilityMatrixService capabilityMatrixService) {
        this(fingerprintService, capabilityMatrixService, null);
    }

    @Autowired
    public RendererProfileValidator(
            TemplateSetFingerprintService fingerprintService,
            RendererCapabilityMatrixService capabilityMatrixService,
            ValidatorProfileLoader validatorProfileLoader) {
        this.fingerprintService = fingerprintService;
        this.capabilityMatrixService = capabilityMatrixService;
        this.validatorProfileLoader = validatorProfileLoader;
    }

    public ValidationResult validate(RendererProfile profile, Purpose purpose) {
        if (profile == null) throw new IllegalArgumentException("profile은 필수입니다.");
        Purpose resolvedPurpose = purpose == null ? Purpose.PREVIEW : purpose;
        List<ValidationIssue> issues = new ArrayList<>();
        if (profile.rendererType() != RendererProfile.RendererType.THYMELEAF) {
            issues.add(error("RENDERER_TYPE_UNSUPPORTED", "rendererType",
                    "현재 생성 Target은 THYMELEAF만 지원합니다."));
        }
        if (profile.templateEngine() != RendererProfile.TemplateEngine.FREEMARKER) {
            issues.add(error("TEMPLATE_ENGINE_UNSUPPORTED", "templateEngine",
                    "현재 Template Engine은 FREEMARKER만 지원합니다."));
        }
        profile.supportedViewTypes().stream()
                .filter(value -> !SUPPORTED_VIEW_TYPES.contains(value))
                .forEach(value -> issues.add(error("VIEW_TYPE_UNSUPPORTED", value,
                        "현재 Renderer Profile이 선언할 수 없는 View Type입니다.")));
        if (resolvedPurpose == Purpose.APPLY
                && profile.status() != RendererProfile.Status.APPROVED) {
            issues.add(error("RENDERER_PROFILE_NOT_APPROVED", "status",
                    "Apply에는 APPROVED RendererProfile만 사용할 수 있습니다."));
        }
        if (fingerprintService != null) {
            var actual = fingerprintService.calculate();
            if (!profile.templateSetVersion().equals(actual.templateSetVersion())) {
                issues.add(error("TEMPLATE_SET_VERSION_MISMATCH", "templateSetVersion",
                        "RendererProfile과 배포 Template Set Version이 다릅니다."));
            }
            if (!profile.templateSetHash().equals(actual.templateSetHash())) {
                issues.add(error("TEMPLATE_SET_HASH_MISMATCH", "templateSetHash",
                        "RendererProfile과 배포 Template Set Hash가 다릅니다. actual="
                                + actual.templateSetHash()));
            }
        }
        if (capabilityMatrixService != null) {
            capabilityMatrixService.inspect(profile).issues().forEach(issue ->
                    issues.add(error(issue.code(), issue.target(), issue.message())));
        }
        if (validatorProfileLoader != null) {
            try {
                validatorProfileLoader.loadApproved(profile.validatorProfileReference());
            } catch (ValidatorProfileLoader.ValidatorProfileLoadException exception) {
                issues.add(error(exception.code(), "validatorProfile", exception.getMessage()));
            }
        }
        return new ValidationResult(profile.profileId(), profile.version(), resolvedPurpose, issues);
    }

    public RendererProfile requireValid(RendererProfile profile, Purpose purpose) {
        ValidationResult result = validate(profile, purpose);
        if (!result.valid()) throw new RendererProfileValidationException(result);
        return profile;
    }

    private ValidationIssue error(String code, String target, String message) {
        return new ValidationIssue(code, Severity.ERROR, target, message);
    }

    public enum Purpose { PREVIEW, APPLY }
    public enum Severity { WARNING, ERROR }

    public record ValidationIssue(String code, Severity severity, String target, String message) {}

    public record ValidationResult(
            String profileId,
            String version,
            Purpose purpose,
            List<ValidationIssue> issues
    ) {
        public ValidationResult {
            issues = List.copyOf(issues);
        }

        public boolean valid() {
            return issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR);
        }
    }

    public static final class RendererProfileValidationException extends IllegalStateException {
        private final ValidationResult result;

        public RendererProfileValidationException(ValidationResult result) {
            super("RendererProfile 검증 실패: " + result.profileId() + "@" + result.version());
            this.result = result;
        }

        public ValidationResult result() {
            return result;
        }
    }
}
