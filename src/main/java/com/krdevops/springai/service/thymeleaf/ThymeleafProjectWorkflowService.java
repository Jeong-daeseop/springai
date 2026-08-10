package com.krdevops.springai.service.thymeleaf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.thymeleaf.BrowserValidationReport;
import com.krdevops.springai.model.thymeleaf.BrowserValidationRequest;
import com.krdevops.springai.model.thymeleaf.GateSeverity;
import com.krdevops.springai.model.thymeleaf.LegacySourceManifest;
import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafOperationSnapshot;
import com.krdevops.springai.model.thymeleaf.ThymeleafProjectOperation;
import com.krdevops.springai.model.thymeleaf.ValidationGateResult;
import com.krdevops.springai.model.thymeleaf.ValidationReport;
import com.krdevops.springai.model.write.ProjectChangeSet;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.model.operation.OperationEvent;
import com.krdevops.springai.model.operation.OperationLock;
import com.krdevops.springai.service.operation.OperationEventPort;
import com.krdevops.springai.service.operation.OperationLockPort;
import com.krdevops.springai.service.operation.NoopOperationEventPort;
import com.krdevops.springai.service.operation.NoopOperationLockPort;
import com.krdevops.springai.service.artifact.ArtifactService;
import com.krdevops.springai.service.write.ApplyOutcome;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.Duration;
import com.krdevops.springai.config.observability.ObservabilityContextHolder;

/**
 * Preview → hash 승인 → source revision 재검증 → 원자 적용/롤백 → 검증의 단일 진입점.
 *
 * <p>ARCH-WP4: 상태는 {@link ThymeleafOperationStore}(Port)에 위임한다 — 운영 기본값은
 * MySQL Adapter({@code ThymeleafProjectOperationRepository})라 재시작해도 승인·적용 이력이
 * 남는다(RISK-03 해소). {@code ThymeleafOperationStore} 없이 3~4-인자 생성자로 직접 생성하면
 * {@link InMemoryThymeleafOperationStore}를 기본값으로 쓴다(단위 테스트·임베디드 전용).
 *
 * <p>ARCH-WP7/ARCH-0715: {@code apply()}의 실제 파일 조작(staging→backup→atomic replace→실패 시
 * rollback)은 더 이상 이 클래스가 직접 하지 않고 공용 {@link ApprovedProjectWritePort}에 위임한다.
 * Operation 상태 전이·source/DESIGN.md revision 재검사·lock·이벤트 기록은 여전히 이 클래스의
 * 책임이다 — Port는 "승인된 변경을 파일에 반영"만 알고 Thymeleaf Operation의 존재 자체를 모른다.
 */
@Service
public class ThymeleafProjectWorkflowService {

    private final ProjectOperationStateService stateService;
    private final ValidationGateExecutor validationGate;
    private final OperationHashFactory hashFactory;
    private final LegacySourceInventoryService legacySourceInventory;
    private final DesignMdRuleLoader designRuleLoader;
    private final ThymeleafOperationStore store;
    private final OperationEventPort eventPort;
    private final OperationLockPort lockPort;
    private final ArtifactService artifactService;
    private final SafePathResolver pathResolver;
    private final ApprovedProjectWritePort writePort;
    private final BrowserValidationGateExecutor browserValidationGate;
    private final BrowserGateDirectoryResolver browserGateDirectories;
    private final ObjectMapper reportMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    public ThymeleafProjectWorkflowService(
            ProjectOperationStateService stateService,
            ValidationGateExecutor validationGate,
            OperationHashFactory hashFactory,
            LegacySourceInventoryService legacySourceInventory,
            DesignMdRuleLoader designRuleLoader,
            ThymeleafOperationStore store,
            OperationEventPort eventPort,
            OperationLockPort lockPort,
            ArtifactService artifactService,
            SafePathResolver pathResolver,
            ApprovedProjectWritePort writePort,
            BrowserValidationGateExecutor browserValidationGate,
            BrowserGateDirectoryResolver browserGateDirectories) {
        this.stateService = stateService;
        this.validationGate = validationGate;
        this.hashFactory = hashFactory;
        this.legacySourceInventory = legacySourceInventory;
        this.designRuleLoader = designRuleLoader;
        this.store = store;
        this.eventPort = eventPort;
        this.lockPort = lockPort;
        this.artifactService = artifactService;
        this.pathResolver = pathResolver;
        this.writePort = writePort;
        this.browserValidationGate = browserValidationGate;
        this.browserGateDirectories = browserGateDirectories;
    }

    /** legacy source manifest 도입 전 직접 생성 호출부 호환. */
    public ThymeleafProjectWorkflowService(
            ProjectOperationStateService stateService,
            ValidationGateExecutor validationGate,
            OperationHashFactory hashFactory,
            DesignMdRuleLoader designRuleLoader,
            ThymeleafOperationStore store,
            OperationEventPort eventPort,
            OperationLockPort lockPort,
            ArtifactService artifactService,
            SafePathResolver pathResolver,
            ApprovedProjectWritePort writePort,
            BrowserValidationGateExecutor browserValidationGate,
            BrowserGateDirectoryResolver browserGateDirectories) {
        this(stateService, validationGate, hashFactory, new LegacySourceInventoryService(hashFactory),
                designRuleLoader, store, eventPort, lockPort, artifactService, pathResolver, writePort,
                browserValidationGate, browserGateDirectories);
    }

    public ThymeleafProjectWorkflowService(ProjectOperationStateService stateService,
            ValidationGateExecutor validationGate, OperationHashFactory hashFactory,
            DesignMdRuleLoader designRuleLoader, ThymeleafOperationStore store) {
        this(stateService, validationGate, hashFactory, designRuleLoader, store,
                new NoopOperationEventPort(), new NoopOperationLockPort(), null,
                new SafePathResolver(), new FileSystemApprovedProjectWritePort(new SafePathResolver(), hashFactory),
                null, null);
    }

    /** DB 없이 Workflow 로직만 검증하려는 단위 테스트·임베디드 사용을 위한 호환 생성자. */
    public ThymeleafProjectWorkflowService(
            ProjectOperationStateService stateService,
            ValidationGateExecutor validationGate,
            OperationHashFactory hashFactory,
            DesignMdRuleLoader designRuleLoader) {
        this(stateService, validationGate, hashFactory, designRuleLoader, new InMemoryThymeleafOperationStore());
    }

    /** 순수 파일 Workflow 단위 테스트 및 임베디드 사용을 위한 호환 생성자. */
    public ThymeleafProjectWorkflowService(
            ProjectOperationStateService stateService,
            ValidationGateExecutor validationGate,
            OperationHashFactory hashFactory) {
        this(stateService, validationGate, hashFactory, null, new InMemoryThymeleafOperationStore());
    }

    public WorkflowResult preview(Path projectRoot, Map<String, String> generatedFiles) {
        return preview(projectRoot, generatedFiles, null, LegacySourceManifest.empty());
    }

    /** WP6 Binding 생성 전용 Preview: 원본 manifest와 계약 snapshot을 Operation에 함께 영속한다. */
    public WorkflowResult preview(
            Path projectRoot,
            Map<String, String> generatedFiles,
            ThymeleafBindingContract bindingContract,
            LegacySourceManifest legacySourceManifest) {
        Path root = pathResolver.realDirectory(projectRoot);
        String designRevision = currentDesignRevision(root);
        LegacySourceManifest manifest = legacySourceManifest == null
                ? LegacySourceManifest.empty() : legacySourceManifest;
        if (generatedFiles == null || generatedFiles.isEmpty()) {
            throw new IllegalArgumentException("generatedFiles는 최소 1개 이상이어야 합니다.");
        }
        Map<String, String> files = new LinkedHashMap<>();
        Map<String, String> sourceHashes = new LinkedHashMap<>();
        List<String> validationErrors = new ArrayList<>();
        generatedFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Path target = pathResolver.resolveTarget(root, entry.getKey());
            String content = entry.getValue() == null ? "" : entry.getValue();
            files.put(entry.getKey(), content);
            sourceHashes.put(entry.getKey(), currentHash(target));
            var parse = validationGate.validateThymeleafParse(content);
            if (!parse.passed()) {
                parse.issues().forEach(issue -> validationErrors.add(entry.getKey() + ": " + issue));
            }
            var overflow = validationGate.validateNoOverflow(content);
            if (!overflow.passed()) {
                overflow.issues().forEach(issue -> validationErrors.add(entry.getKey() + ": " + issue));
            }
        });

        ThymeleafProjectOperation operation = stateService.createOperation(root.toString());
        operation = stateService.transitionState(operation, ProjectOperationStatus.CONTRACT_READY);
        operation = copy(operation, ProjectOperationStatus.CONTRACT_READY, files,
                List.copyOf(files.keySet()), null, List.of(), validationErrors, false, null);
        operation = stateService.transitionState(operation, ProjectOperationStatus.PREVIEW_READY);
        Map<String, Object> previewBasis = new LinkedHashMap<>();
        previewBasis.put("generatedFiles", files);
        previewBasis.put("designMdRevision", designRevision);
        if (manifest.tracked()) {
            previewBasis.put("legacySourceFingerprint", manifest.fingerprint());
        }
        String previewHash = hashFactory.canonicalHash(previewBasis);

        ThymeleafOperationSnapshot initial = new ThymeleafOperationSnapshot(
                1, operation, root.toString(), sourceHashes, designRevision, previewHash,
                manifest, bindingContract);
        ThymeleafOperationSnapshot saved = store.createOrReuse(initial);
        recordEvent(saved, null, saved.operation().status(), "PREVIEW_CREATED");
        if (artifactService != null) {
            files.forEach((name, content) -> artifactService.ingestAndLink(
                    content.getBytes(StandardCharsets.UTF_8), mediaType(name), "THYMELEAF_PREVIEW",
                    designRevision, saved.operation().operationId(), "THYMELEAF_PROJECT"));
            persistBindingContract(saved);
        }
        return new WorkflowResult(saved.operation(), saved.previewHash());
    }

    public WorkflowResult approve(String operationId, String expectedPreviewHash) {
        ThymeleafOperationSnapshot snapshot = required(operationId);
        if (snapshot.operation().status() != ProjectOperationStatus.PREVIEW_READY) {
            recordEvent(snapshot, snapshot.operation().status(), snapshot.operation().status(), "APPROVAL_REJECTED");
            throw new IllegalStateException("THYMELEAF_APPROVAL_REQUIRES_PREVIEW_READY");
        }
        if (!snapshot.previewHash().equals(expectedPreviewHash)) {
            recordEvent(snapshot, snapshot.operation().status(), snapshot.operation().status(), "APPROVAL_REJECTED");
            throw new IllegalStateException("THYMELEAF_PREVIEW_HASH_MISMATCH");
        }
        if (!snapshot.operation().validationErrors().isEmpty()) {
            recordEvent(snapshot, snapshot.operation().status(), snapshot.operation().status(), "APPROVAL_REJECTED");
            throw new IllegalStateException("THYMELEAF_PREVIEW_VALIDATION_FAILED: "
                    + snapshot.operation().validationErrors());
        }
        ThymeleafProjectOperation approved = stateService.transitionState(
                snapshot.operation(), ProjectOperationStatus.APPROVED);
        approved = copy(approved, approved.status(), approved.previewArtifacts(), approved.targetFiles(),
                approved.backupPath(), approved.conflictingFiles(), approved.validationErrors(), true, null);
        ThymeleafOperationSnapshot saved = nextRevision(snapshot, approved);
        recordEvent(saved, snapshot.operation().status(), approved.status(), "APPROVED");
        return new WorkflowResult(saved.operation(), saved.previewHash());
    }

    public WorkflowResult apply(String operationId) {
        ThymeleafOperationSnapshot snapshot = required(operationId);
        String owner = UUID.randomUUID().toString();
        OperationLock lock = lockPort.acquire("THYMELEAF:" + snapshot.projectRoot(), operationId, owner,
                Duration.ofMinutes(5)).orElseThrow(() -> new IllegalStateException("THYMELEAF_OPERATION_LOCKED"));
        try {
        ThymeleafProjectOperation operation = snapshot.operation();
        if (!stateService.validateBeforeApply(operation)) {
            recordEvent(snapshot, operation.status(), operation.status(), "APPLY_REJECTED");
            throw new IllegalStateException("THYMELEAF_OPERATION_NOT_READY_FOR_APPLY");
        }
        Path root = Path.of(snapshot.projectRoot());

        // DESIGN.md drift는 개별 파일 hash가 아니라 Thymeleaf 도메인 고유의 규칙 hash라
        // ProjectChangeSet(WP7 공용 계약)에 자연스럽게 들어가지 않는다 — 파일 conflict와 별개로
        // 여기서 먼저 검사한다. drift가 있으면 공용 Port를 아예 호출하지 않고(파일 전혀 안 건드림)
        // 곧바로 CONFLICT로 전이한다.
        if (!snapshot.designRevision().equals(currentDesignRevision(root))) {
            return toConflict(snapshot, operation, List.of("DESIGN.md"));
        }

        List<String> legacySourceConflicts = legacySourceConflicts(root, snapshot.legacySourceManifest());
        if (!legacySourceConflicts.isEmpty()) {
            return toConflict(snapshot, operation, legacySourceConflicts);
        }

        ProjectChangeSet changeSet = new ProjectChangeSet(
                snapshot.projectRoot(), snapshot.designRevision(),
                toFileChanges(operation.previewArtifacts(), snapshot.sourceHashes()),
                List.of(), ProjectWritePolicy.ATOMIC_APPROVED);
        ApplyOutcome outcome = writePort.apply(changeSet);

        if (outcome.status() == ApplyOutcome.Status.CONFLICT) {
            return toConflict(snapshot, operation, outcome.conflictingPaths());
        }
        if (outcome.status() == ApplyOutcome.Status.ROLLED_BACK
                || outcome.status() == ApplyOutcome.Status.ROLLBACK_FAILED) {
            // ARCH-0713: ROLLBACK_FAILED는 "복구까지 실패해 파일이 원래 상태로 안 돌아갔을 수
            // 있다"는 뜻이라, ROLLED_BACK과 같은 값으로 취급해 아래로 흘려보내면 안 된다(성공
            // 처리로 오인하는 거짓 보고 버그가 됨) — 둘 다 FAILED로 전이시키되 이벤트 타입으로
            // 구분한다.
            boolean rollbackFailed = outcome.status() == ApplyOutcome.Status.ROLLBACK_FAILED;
            String eventType = rollbackFailed ? "APPLY_ROLLBACK_FAILED" : "APPLY_ROLLED_BACK";
            String errorPrefix = rollbackFailed ? "APPLY_ROLLBACK_FAILED: " : "APPLY_ROLLED_BACK: ";
            ThymeleafProjectOperation failed = stateService.transitionState(operation, ProjectOperationStatus.FAILED);
            failed = copy(failed, failed.status(), failed.previewArtifacts(), failed.targetFiles(),
                    outcome.backupPath(), List.of(),
                    List.of(errorPrefix + outcome.failureDetail()), false, null);
            nextRevision(snapshot, failed);
            recordEvent(snapshot, operation.status(), failed.status(), eventType);
            String exceptionCode = rollbackFailed ? "THYMELEAF_APPLY_ROLLBACK_FAILED" : "THYMELEAF_APPLY_ROLLED_BACK";
            throw new IllegalStateException(exceptionCode, new RuntimeException(outcome.failureDetail()));
        }

        ThymeleafProjectOperation applied = stateService.markAsApplied(operation);
        applied = copy(applied, applied.status(), applied.previewArtifacts(), applied.targetFiles(),
                outcome.backupPath(), List.of(), applied.validationErrors(), true, applied.appliedAt());
        ThymeleafOperationSnapshot saved = nextRevision(snapshot, applied);
        indexScreenOperationIfBound(root, saved);
        recordEvent(saved, operation.status(), applied.status(), "APPLIED");
        return new WorkflowResult(saved.operation(), saved.previewHash());
        } finally {
            lockPort.release(lock);
        }
    }

    private WorkflowResult toConflict(
            ThymeleafOperationSnapshot snapshot, ThymeleafProjectOperation operation, List<String> conflicts) {
        ThymeleafProjectOperation conflicted = stateService.transitionState(operation, ProjectOperationStatus.CONFLICT);
        conflicted = copy(conflicted, conflicted.status(), conflicted.previewArtifacts(), conflicted.targetFiles(),
                null, conflicts, conflicted.validationErrors(), false, null);
        ThymeleafOperationSnapshot saved = nextRevision(snapshot, conflicted);
        recordEvent(saved, operation.status(), conflicted.status(), "CONFLICT");
        return new WorkflowResult(saved.operation(), saved.previewHash());
    }

    /** preview 시점 hash(승인 기준 원본)를 {@code beforeHash}로 실어 공용 {@link ProjectChangeSet}을 만든다. */
    private List<ProjectChangeSet.FileChange> toFileChanges(
            Map<String, String> previewArtifacts, Map<String, String> sourceHashes) {
        List<ProjectChangeSet.FileChange> changes = new ArrayList<>();
        for (var entry : previewArtifacts.entrySet()) {
            changes.add(new ProjectChangeSet.FileChange(
                    entry.getKey(), sourceHashes.get(entry.getKey()), entry.getValue(), null));
        }
        return changes;
    }

    private List<String> legacySourceConflicts(Path root, LegacySourceManifest manifest) {
        if (manifest == null || !manifest.tracked()) {
            return List.of();
        }
        SourceReadBudget budget = SourceReadBudget.defaultBudget();
        List<String> conflicts = new ArrayList<>();
        for (LegacySourceManifest.SourceFile source : manifest.files()) {
            try {
                var current = legacySourceInventory.readSourceFile(root, source.relativePath(), budget);
                if (!source.sha256Hex().equals(current.sha256Hex())) {
                    conflicts.add("LEGACY_SOURCE:" + source.relativePath());
                }
            } catch (RuntimeException unavailableOrUnsafe) {
                conflicts.add("LEGACY_SOURCE:" + source.relativePath());
            }
        }
        return List.copyOf(conflicts);
    }

    public WorkflowResult revalidate(String operationId) {
        return revalidate(operationId, null);
    }

    /**
     * WP8 3차 pass/ARCH-0810/0811: {@code browserOptions}가 주어진 화면만 브라우저 Gate
     * (render/axe/visual parity)까지 실행하고, 파일별 Gate 결과를 {@link ValidationReport}로
     * 영속화한다. 저장된 Binding Contract가 있으면 browser 옵션과 무관하게 WP6/WP8 정적 Gate
     * (parse/실제 TemplateEngine render/binding/route/overflow)를 자동 실행한다. 과거 일반 Preview처럼
     * 계약이 없는 Operation은 parse-only 호환을 유지한다.
     *
     * <p>이 서버는 다른 프로젝트에 코드를 생성하는 도구라 생성 화면이 돌아가는 서버를 자동으로
     * 갖지 못한다. 그래서 렌더 대상은 호출자가 URL 또는 완성 HTML로 명시해야 하고, 브라우저
     * Gate가 설정되지 않은 인스턴스에 옵션이 들어오면 조용히 건너뛰지 않고 실패시킨다 —
     * "검증한 줄 알았는데 아무것도 안 했다"가 가장 위험한 결과이기 때문이다.
     */
    public WorkflowResult revalidate(String operationId, RevalidateBrowserOptions browserOptions) {
        ThymeleafOperationSnapshot snapshot = required(operationId);
        if (snapshot.operation().status() != ProjectOperationStatus.APPLIED) {
            throw new IllegalStateException("THYMELEAF_VALIDATION_REQUIRES_APPLIED");
        }
        Path root = Path.of(snapshot.projectRoot());
        List<String> errors = new ArrayList<>();
        Map<String, List<ValidationGateResult>> browserResults =
                runBrowserGates(root, operationId, browserOptions, errors);
        for (String relative : snapshot.operation().previewArtifacts().keySet()) {
            Path target = pathResolver.resolveTarget(root, relative);
            String content;
            try {
                content = Files.readString(target);
            } catch (IOException exception) {
                errors.add(relative + ": 파일 읽기 실패");
                continue;
            }
            List<ValidationGateResult> results = new ArrayList<>();
            if (snapshot.bindingContract() == null) {
                results.add(validationGate.validateThymeleafParse(content));
            } else {
                results.addAll(validationGate.runThymeleafGates(
                        snapshot.bindingContract(), content).results());
            }
            for (ValidationGateResult result : results) {
                if (result.passed() || validationGate.severityOf(result.gateType()) != GateSeverity.BLOCK) continue;
                result.issues().forEach(issue -> errors.add(relative + ": " + issue));
            }
            results.addAll(browserResults.getOrDefault(relative, List.of()));
            persistValidationReport(snapshot, relative, results, content);
        }
        if (!errors.isEmpty()) {
            ThymeleafProjectOperation failed = stateService.transitionState(
                    snapshot.operation(), ProjectOperationStatus.FAILED);
            failed = copy(failed, failed.status(), failed.previewArtifacts(), failed.targetFiles(),
                    failed.backupPath(), List.of(), errors, false, failed.appliedAt());
            ThymeleafOperationSnapshot saved = nextRevision(snapshot, failed);
            recordEvent(saved, snapshot.operation().status(), failed.status(), "VALIDATION_FAILED");
            return new WorkflowResult(saved.operation(), saved.previewHash());
        }
        ThymeleafProjectOperation validated = stateService.markAsValidated(snapshot.operation());
        ThymeleafOperationSnapshot saved = nextRevision(snapshot, validated);
        recordEvent(saved, snapshot.operation().status(), validated.status(), "VALIDATED");
        return new WorkflowResult(saved.operation(), saved.previewHash());
    }

    /**
     * @return 파일 경로(없으면 screenId)별 브라우저 Gate 결과. BLOCK severity 실패는 {@code errors}에
     *         합류시켜 기존 FAILED/VALIDATED 분기가 그대로 처리하게 한다.
     */
    private Map<String, List<ValidationGateResult>> runBrowserGates(
            Path root, String operationId, RevalidateBrowserOptions browserOptions, List<String> errors) {
        if (browserOptions == null || browserOptions.screens().isEmpty()) {
            return Map.of();
        }
        if (browserValidationGate == null || browserGateDirectories == null) {
            throw new IllegalStateException("THYMELEAF_BROWSER_GATE_NOT_CONFIGURED");
        }
        Path artifactDirectory = browserGateDirectories.artifactDirectory(root, operationId);
        Path baselineDirectory = browserGateDirectories.baselineDirectory(root);
        Map<String, List<ValidationGateResult>> byFile = new LinkedHashMap<>();
        for (BrowserScreenValidationRequest screen : browserOptions.screens()) {
            BrowserValidationReport report = browserValidationGate.validate(new BrowserValidationRequest(
                    screen.screenId(), screen.url(), screen.renderedHtml(),
                    artifactDirectory, baselineDirectory, screen.maskSelectors(), screen.readySelector(),
                    BrowserValidationRequest.DEFAULT_MAX_DIFFERENCE_RATIO,
                    BrowserValidationRequest.DEFAULT_TIMEOUT_MILLIS));
            List<ValidationGateResult> results = report.toGateResults();
            String label = screen.relativeFile() == null || screen.relativeFile().isBlank()
                    ? screen.screenId() : screen.relativeFile();
            byFile.computeIfAbsent(label, key -> new ArrayList<>()).addAll(results);
            for (ValidationGateResult result : results) {
                if (result.passed() || validationGate.severityOf(result.gateType()) != GateSeverity.BLOCK) continue;
                result.issues().forEach(issue -> errors.add(label + ": " + issue));
            }
        }
        return byFile;
    }

    private void persistValidationReport(ThymeleafOperationSnapshot snapshot, String relative,
                                         List<ValidationGateResult> results, String content) {
        if (artifactService == null) return;
        boolean blocked = results.stream().anyMatch(result -> !result.passed()
                && validationGate.severityOf(result.gateType()) == GateSeverity.BLOCK);
        ValidationReport report = new ValidationReport(relative, results, blocked,
                hashFactory.sha256Hex(content.getBytes(StandardCharsets.UTF_8)), java.time.Instant.now());
        try {
            artifactService.ingestAndLink(reportMapper.writeValueAsBytes(report), "application/json",
                    "THYMELEAF_VALIDATION_REPORT", snapshot.designRevision(),
                    snapshot.operation().operationId(), "THYMELEAF_PROJECT");
        } catch (IOException exception) {
            throw new IllegalStateException("THYMELEAF_VALIDATION_REPORT_SERIALIZATION_FAILED: " + relative, exception);
        }
    }

    private void persistBindingContract(ThymeleafOperationSnapshot snapshot) {
        if (artifactService == null || snapshot.bindingContract() == null) return;
        String sourceRevision = snapshot.legacySourceManifest().tracked()
                ? snapshot.legacySourceManifest().fingerprint() : snapshot.designRevision();
        try {
            artifactService.ingestAndLink(reportMapper.writeValueAsBytes(snapshot.bindingContract()),
                    "application/json", "THYMELEAF_BINDING_CONTRACT",
                    sourceRevision, snapshot.operation().operationId(),
                    "THYMELEAF_PROJECT");
        } catch (IOException exception) {
            throw new IllegalStateException("THYMELEAF_BINDING_CONTRACT_SERIALIZATION_FAILED", exception);
        }
    }

    public Optional<WorkflowResult> find(String operationId) {
        return store.findLatest(operationId).map(s -> new WorkflowResult(s.operation(), s.previewHash()));
    }

    /**
     * Regeneration diff: 같은 프로젝트·화면에 대해 마지막으로 실제 적용됐던(APPLIED) Operation의
     * snapshot을 돌려준다. draft preview는 포함하지 않는다({@link #indexScreenOperationIfBound}가
     * apply 성공 시에만 색인하기 때문).
     */
    public Optional<ThymeleafOperationSnapshot> findLatestByScreen(Path projectRoot, String screenId) {
        return store.findLatestByScreen(projectRootHash(projectRoot), screenId);
    }

    private void indexScreenOperationIfBound(Path root, ThymeleafOperationSnapshot saved) {
        ThymeleafBindingContract contract = saved.bindingContract();
        if (contract == null) {
            return;
        }
        store.indexScreenOperation(projectRootHash(root), contract.screenId(), saved.operation().operationId());
    }

    private String projectRootHash(Path projectRoot) {
        Path root = pathResolver.realDirectory(projectRoot);
        return hashFactory.sha256Hex(pathResolver.canonicalKey(root).getBytes(StandardCharsets.UTF_8));
    }

    private ThymeleafOperationSnapshot required(String id) {
        return store.findLatest(id)
                .orElseThrow(() -> new IllegalArgumentException("THYMELEAF_OPERATION_NOT_FOUND: " + id));
    }

    /** 이전 snapshot의 문맥(projectRoot·sourceHashes·designRevision·previewHash)을 유지한 채 다음 revision을 저장한다. */
    private ThymeleafOperationSnapshot nextRevision(ThymeleafOperationSnapshot previous, ThymeleafProjectOperation operation) {
        return store.save(new ThymeleafOperationSnapshot(
                previous.revision() + 1, operation, previous.projectRoot(),
                previous.sourceHashes(), previous.designRevision(), previous.previewHash(),
                previous.legacySourceManifest(), previous.bindingContract()));
    }

    private void recordEvent(ThymeleafOperationSnapshot snapshot, ProjectOperationStatus from,
                             ProjectOperationStatus to, String type) {
        // ARCH-0707(WP7 6차 pass, 2026-08-06): actor는 "system"으로 고정한다 — 구현 누락이
        // 아니라 이 서버의 인증 모델상 채울 수 있는 의미 있는 값이 없기 때문이다. MCP 인증은
        // 전역 공유 토큰 하나뿐이라 McpAuthenticationInterceptor가 principal을 항상 리터럴
        // "mcp-shared-token"으로 고정한다 — 그 값을 여기 그대로 넣으면 "개별 사용자를 식별했다"는
        // 오해만 유발할 뿐 실제 감사 가치는 없다. 이 제약이 CRUD/Board/Master-detail에 별도
        // 승인 워크플로우를 신설하지 않기로 한 결정과도 맞물려 있다(§11 6차 pass 실행 메모 참고).
        // 사용자별 자격증명(OAuth 등)이 도입되면 이 자리를 실제 principal로 교체한다.
        var context = ObservabilityContextHolder.current();
        try (var ignored = ObservabilityContextHolder.openOperation(snapshot.operation().operationId())) {
            eventPort.append(new OperationEvent(UUID.randomUUID().toString(), snapshot.operation().operationId(),
                    "THYMELEAF_PROJECT", snapshot.revision(), from == null ? null : from.name(), to.name(), type,
                    context.actorId() == null ? "system" : context.actorId(), context.correlationId(),
                    snapshot.previewHash(), java.time.Instant.now()));
        }
    }

    private String mediaType(String name) {
        return name.toLowerCase().endsWith(".html") ? "text/html" : "text/plain";
    }

    private String currentDesignRevision(Path root) {
        if (designRuleLoader == null) return "DESIGN_GATE_NOT_CONFIGURED";
        var result = designRuleLoader.load(root.toString());
        if (!result.successful() || result.hasFatalIssue()) {
            String codes = result.issues().stream().map(issue -> issue.code()).distinct()
                    .reduce((left, right) -> left + "," + right).orElse("UNKNOWN");
            throw new IllegalStateException("THYMELEAF_DESIGN_RULE_GATE_FAILED: " + codes);
        }
        return result.value().contentHash() == null ? "MISSING" : result.value().contentHash();
    }

    private String currentHash(Path path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return "MISSING";
        try {
            return hashFactory.sha256Hex(Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new IllegalStateException("SOURCE_REVISION_READ_FAILED: " + path, exception);
        }
    }

    private ThymeleafProjectOperation copy(
            ThymeleafProjectOperation source, ProjectOperationStatus status,
            Map<String, String> previews, List<String> targets, String backup,
            List<String> conflicts, List<String> errors, boolean ready, LocalDateTime appliedAt) {
        return new ThymeleafProjectOperation(source.operationId(), source.projectPath(), status,
                previews, targets, backup, conflicts, errors, ready, source.createdAt(), appliedAt);
    }

    public record WorkflowResult(ThymeleafProjectOperation operation, String previewHash) {}

    /** 비어 있거나 {@code null}이면 재검증은 브라우저 Gate를 아예 실행하지 않는다. */
    public record RevalidateBrowserOptions(List<BrowserScreenValidationRequest> screens) {
        public RevalidateBrowserOptions {
            screens = screens == null ? List.of() : List.copyOf(screens);
        }
    }

    /** {@code url}과 {@code renderedHtml} 중 정확히 하나만 지정한다. */
    public record BrowserScreenValidationRequest(
            String screenId, String relativeFile, String url, String renderedHtml,
            List<String> maskSelectors, String readySelector) {
        public BrowserScreenValidationRequest {
            maskSelectors = maskSelectors == null ? List.of() : List.copyOf(maskSelectors);
        }
    }
}
