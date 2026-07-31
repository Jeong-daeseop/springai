package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.DesignSystemProfileRepository;
import com.krdevops.springai.mapper.FigmaReviewHistoryRepository;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistrySyncResult;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.DesignSystemSpec;
import com.krdevops.springai.model.designsystem.FigmaReviewEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** R6 REST·MCP가 공유하는 Design System 조회·검증·Review facade. */
@Service
public class DesignSystemQueryService {

    private final DesignSystemProfileRepository profileRepository;
    private final ComponentRegistryRepository registryRepository;
    private final FigmaReviewHistoryRepository reviewRepository;
    private final DesignSystemSpecValidator specValidator;
    private final DesignSystemProfileValidator profileValidator;
    private final ComponentRegistryValidator registryValidator;
    private final ComponentRegistrySyncService registrySyncService;
    private final ComponentRegistryResolver registryResolver;

    public DesignSystemQueryService(
            DesignSystemProfileRepository profileRepository,
            ComponentRegistryRepository registryRepository,
            FigmaReviewHistoryRepository reviewRepository,
            DesignSystemSpecValidator specValidator,
            DesignSystemProfileValidator profileValidator,
            ComponentRegistryValidator registryValidator,
            ComponentRegistrySyncService registrySyncService,
            ComponentRegistryResolver registryResolver
    ) {
        this.profileRepository = profileRepository;
        this.registryRepository = registryRepository;
        this.reviewRepository = reviewRepository;
        this.specValidator = specValidator;
        this.profileValidator = profileValidator;
        this.registryValidator = registryValidator;
        this.registrySyncService = registrySyncService;
        this.registryResolver = registryResolver;
    }

    public DesignSystemProfile findLatestProfile(String profileId) {
        return profileRepository.findLatest(profileId)
                .orElseThrow(() -> new IllegalArgumentException("DesignSystemProfile을 찾을 수 없습니다: " + profileId));
    }

    public DesignSystemProfile findProfileVersion(String profileId, String version) {
        return profileRepository.findVersion(profileId, version)
                .orElseThrow(() -> new IllegalArgumentException(
                        "DesignSystemProfile 버전을 찾을 수 없습니다: " + profileId + " v" + version));
    }

    public ComponentRegistry findLatestRegistry(String profileId) {
        return registryRepository.findLatest(profileId)
                .orElseThrow(() -> new IllegalArgumentException("ComponentRegistry를 찾을 수 없습니다: " + profileId));
    }

    public ComponentRegistry findRegistryVersion(String profileId, String version) {
        return registryRepository.findVersion(profileId, version)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ComponentRegistry 버전을 찾을 수 없습니다: " + profileId + " v" + version));
    }

    public List<DesignSystemIssue> validateSpec(DesignSystemSpec spec) {
        return specValidator.validate(spec);
    }

    public List<DesignSystemIssue> validateProfile(DesignSystemProfile profile) {
        return profileValidator.validate(profile);
    }

    public List<DesignSystemIssue> validateRegistry(ComponentRegistry registry) {
        return registryValidator.validatePublished(registry);
    }

    public ComponentRegistrySyncResult previewRegistry(ComponentRegistry registry) {
        return registrySyncService.preview(registry);
    }

    public ComponentRegistrySyncResult applyRegistry(ComponentRegistry registry, boolean confirmed) {
        return registrySyncService.apply(registry, confirmed);
    }

    public ComponentRegistrySyncResult retryRegistry(
            ComponentRegistry registry,
            String retryToken,
            boolean confirmed
    ) {
        return registrySyncService.retry(registry, retryToken, confirmed);
    }

    /** R8: 사람이 확인한 경우에만 Profile을 저장된 Registry 한 버전으로 되돌린다. */
    public DesignSystemProfile rollbackProfile(
            String profileId,
            String profileVersion,
            String registryVersion,
            boolean confirmed
    ) {
        return registrySyncService.rollbackProfile(
                profileId, profileVersion, registryVersion, confirmed);
    }

    public RegistryAuditResult auditRegistry(String profileId, String version) {
        ComponentRegistry registry = version == null || version.isBlank()
                ? findLatestRegistry(profileId)
                : findRegistryVersion(profileId, version);
        List<DesignSystemIssue> issues = new ArrayList<>(registryValidator.validatePublished(registry));
        DesignSystemProfile profile = profileRepository.findVersion(profileId, registry.profileVersion()).orElse(null);
        if (profile == null) {
            issues.add(new DesignSystemIssue(
                    "PROFILE_NOT_FOUND",
                    DesignSystemIssue.Severity.ERROR,
                    "Registry에 연결된 DesignSystemProfile이 없습니다.",
                    profileId));
        } else {
            if (!registry.registryVersion().equals(profile.registryVersion())) {
                issues.add(new DesignSystemIssue(
                        "PROFILE_REGISTRY_VERSION_MISMATCH",
                        DesignSystemIssue.Severity.ERROR,
                        "Profile과 Registry version이 다릅니다.",
                        profileId));
            }
            if (registry.library() != null && profile.libraryFileKey() != null
                    && !profile.libraryFileKey().equals(registry.library().fileKey())) {
                issues.add(new DesignSystemIssue(
                        "LIBRARY_FILE_KEY_MISMATCH",
                        DesignSystemIssue.Severity.ERROR,
                        "Profile과 Registry Library fileKey가 다릅니다.",
                        profileId));
            }
        }
        boolean valid = issues.stream().noneMatch(issue ->
                issue.severity() == DesignSystemIssue.Severity.FATAL
                        || issue.severity() == DesignSystemIssue.Severity.ERROR);
        return new RegistryAuditResult(profileId, registry.registryVersion(), valid, issues.size(), List.copyOf(issues));
    }

    /** 화면 생성 전에 Profile/Registry 드리프트와 필요한 컴포넌트의 해석 가능 여부를 점검한다. */
    public RegistryPreflightResult preflightRegistry(
            String profileId,
            String registryVersion,
            List<String> requiredLogicalTypes
    ) {
        ComponentRegistry registry = registryVersion == null || registryVersion.isBlank()
                ? findLatestRegistry(profileId)
                : findRegistryVersion(profileId, registryVersion);
        RegistryAuditResult audit = auditRegistry(profileId, registry.registryVersion());
        List<DesignSystemIssue> issues = new ArrayList<>(audit.issues());
        Map<String, String> resolutions = new LinkedHashMap<>();
        for (String required : requiredLogicalTypes == null ? List.<String>of() : requiredLogicalTypes) {
            ComponentRegistryResolver.Resolution resolution = registryResolver.resolve(registry, required);
            if (!resolution.resolved()) {
                String code = resolution.kind() == ComponentRegistryResolver.ResolutionKind.CYCLE
                        ? "COMPONENT_REPLACEMENT_CYCLE" : "COMPONENT_NOT_IN_REGISTRY";
                issues.add(new DesignSystemIssue(code, DesignSystemIssue.Severity.ERROR,
                        "필수 컴포넌트를 해석할 수 없습니다: " + required, required));
                continue;
            }
            resolutions.put(required, resolution.resolvedLogicalType());
            if (resolution.kind() != ComponentRegistryResolver.ResolutionKind.DIRECT) {
                issues.add(new DesignSystemIssue(
                        "COMPONENT_REGISTRY_REDIRECT",
                        DesignSystemIssue.Severity.WARNING,
                        required + "가 " + resolution.resolvedLogicalType() + "로 해석되었습니다.",
                        required));
            }
        }
        boolean valid = issues.stream().noneMatch(issue ->
                issue.severity() == DesignSystemIssue.Severity.FATAL
                        || issue.severity() == DesignSystemIssue.Severity.ERROR);
        return new RegistryPreflightResult(profileId, registry.registryVersion(), valid,
                Map.copyOf(resolutions), List.copyOf(issues));
    }

    public FigmaReviewEvent saveReview(FigmaReviewEvent event) {
        reviewRepository.save(event);
        return event;
    }

    public List<FigmaReviewEvent> findReviews(
            FigmaReviewEvent.TargetType targetType,
            String targetId,
            String targetVersion
    ) {
        return reviewRepository.findByTarget(targetType, targetId, targetVersion);
    }

    /** MCP 결과에는 Component/Variable Key를 포함하지 않는 요약만 노출한다. */
    public record RegistryAuditResult(
            String profileId,
            String registryVersion,
            boolean valid,
            int issueCount,
            List<DesignSystemIssue> issues
    ) {}

    public record RegistryPreflightResult(
            String profileId,
            String registryVersion,
            boolean valid,
            Map<String, String> resolutions,
            List<DesignSystemIssue> issues
    ) {}
}
