package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafOperationSnapshot;
import com.krdevops.springai.model.thymeleaf.ThymeleafProjectOperation;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.model.operation.OperationEvent;
import com.krdevops.springai.model.operation.OperationLock;
import com.krdevops.springai.service.operation.OperationEventPort;
import com.krdevops.springai.service.operation.OperationLockPort;
import com.krdevops.springai.service.operation.NoopOperationEventPort;
import com.krdevops.springai.service.operation.NoopOperationLockPort;
import com.krdevops.springai.service.artifact.ArtifactService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.Duration;

/**
 * Preview → hash 승인 → source revision 재검증 → 원자 적용/롤백 → 검증의 단일 진입점.
 *
 * <p>ARCH-WP4: 상태는 {@link ThymeleafOperationStore}(Port)에 위임한다 — 운영 기본값은
 * MySQL Adapter({@code ThymeleafProjectOperationRepository})라 재시작해도 승인·적용 이력이
 * 남는다(RISK-03 해소). {@code ThymeleafOperationStore} 없이 3~4-인자 생성자로 직접 생성하면
 * {@link InMemoryThymeleafOperationStore}를 기본값으로 쓴다(단위 테스트·임베디드 전용).
 */
@Service
public class ThymeleafProjectWorkflowService {

    private final ProjectOperationStateService stateService;
    private final ValidationGateExecutor validationGate;
    private final OperationHashFactory hashFactory;
    private final DesignMdRuleLoader designRuleLoader;
    private final ThymeleafOperationStore store;
    private final OperationEventPort eventPort;
    private final OperationLockPort lockPort;
    private final ArtifactService artifactService;

    @Autowired
    public ThymeleafProjectWorkflowService(
            ProjectOperationStateService stateService,
            ValidationGateExecutor validationGate,
            OperationHashFactory hashFactory,
            DesignMdRuleLoader designRuleLoader,
            ThymeleafOperationStore store,
            OperationEventPort eventPort,
            OperationLockPort lockPort,
            ArtifactService artifactService) {
        this.stateService = stateService;
        this.validationGate = validationGate;
        this.hashFactory = hashFactory;
        this.designRuleLoader = designRuleLoader;
        this.store = store;
        this.eventPort = eventPort;
        this.lockPort = lockPort;
        this.artifactService = artifactService;
    }

    public ThymeleafProjectWorkflowService(ProjectOperationStateService stateService,
            ValidationGateExecutor validationGate, OperationHashFactory hashFactory,
            DesignMdRuleLoader designRuleLoader, ThymeleafOperationStore store) {
        this(stateService, validationGate, hashFactory, designRuleLoader, store,
                new NoopOperationEventPort(), new NoopOperationLockPort(), null);
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
        Path root = realDirectory(projectRoot);
        String designRevision = currentDesignRevision(root);
        if (generatedFiles == null || generatedFiles.isEmpty()) {
            throw new IllegalArgumentException("generatedFiles는 최소 1개 이상이어야 합니다.");
        }
        Map<String, String> files = new LinkedHashMap<>();
        Map<String, String> sourceHashes = new LinkedHashMap<>();
        List<String> validationErrors = new ArrayList<>();
        generatedFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Path target = resolveTarget(root, entry.getKey());
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
        String previewHash = hashFactory.canonicalHash(Map.of(
                "generatedFiles", files,
                "designMdRevision", designRevision));

        ThymeleafOperationSnapshot initial = new ThymeleafOperationSnapshot(
                1, operation, root.toString(), sourceHashes, designRevision, previewHash);
        ThymeleafOperationSnapshot saved = store.createOrReuse(initial);
        recordEvent(saved, null, saved.operation().status(), "PREVIEW_CREATED");
        if (artifactService != null) {
            files.forEach((name, content) -> artifactService.ingestAndLink(
                    content.getBytes(StandardCharsets.UTF_8), mediaType(name), "THYMELEAF_PREVIEW",
                    designRevision, saved.operation().operationId(), "THYMELEAF_PROJECT"));
        }
        return new WorkflowResult(saved.operation(), saved.previewHash());
    }

    public WorkflowResult approve(String operationId, String expectedPreviewHash) {
        ThymeleafOperationSnapshot snapshot = required(operationId);
        if (snapshot.operation().status() != ProjectOperationStatus.PREVIEW_READY) {
            throw new IllegalStateException("THYMELEAF_APPROVAL_REQUIRES_PREVIEW_READY");
        }
        if (!snapshot.previewHash().equals(expectedPreviewHash)) {
            throw new IllegalStateException("THYMELEAF_PREVIEW_HASH_MISMATCH");
        }
        if (!snapshot.operation().validationErrors().isEmpty()) {
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
            throw new IllegalStateException("THYMELEAF_OPERATION_NOT_READY_FOR_APPLY");
        }
        Path root = Path.of(snapshot.projectRoot());
        List<String> conflicts = snapshot.sourceHashes().entrySet().stream()
                .filter(entry -> !entry.getValue().equals(currentHash(resolveTarget(root, entry.getKey()))))
                .map(Map.Entry::getKey).toList();
        if (!snapshot.designRevision().equals(currentDesignRevision(root))) {
            conflicts = new ArrayList<>(conflicts);
            conflicts.add("DESIGN.md");
        }
        if (!conflicts.isEmpty()) {
            ThymeleafProjectOperation conflicted = stateService.transitionState(operation, ProjectOperationStatus.CONFLICT);
            conflicted = copy(conflicted, conflicted.status(), conflicted.previewArtifacts(), conflicted.targetFiles(),
                    null, conflicts, conflicted.validationErrors(), false, null);
            ThymeleafOperationSnapshot saved = nextRevision(snapshot, conflicted);
            recordEvent(saved, operation.status(), conflicted.status(), "CONFLICT");
            return new WorkflowResult(saved.operation(), saved.previewHash());
        }

        Path staging = null;
        Path backup = null;
        List<Path> appliedTargets = new ArrayList<>();
        try {
            staging = Files.createTempDirectory(root, ".thymeleaf-stage-");
            backup = Files.createTempDirectory(root, ".thymeleaf-backup-");
            for (var entry : operation.previewArtifacts().entrySet()) {
                Path staged = resolveWithin(staging, entry.getKey());
                Files.createDirectories(staged.getParent());
                Files.writeString(staged, entry.getValue(), StandardCharsets.UTF_8);
                Path target = resolveTarget(root, entry.getKey());
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    Path savedBackup = resolveWithin(backup, entry.getKey());
                    Files.createDirectories(savedBackup.getParent());
                    Files.copy(target, savedBackup, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
            for (String relative : operation.previewArtifacts().keySet()) {
                Path target = resolveTarget(root, relative);
                Files.createDirectories(target.getParent());
                moveReplacing(resolveWithin(staging, relative), target);
                appliedTargets.add(target);
            }
            ThymeleafProjectOperation applied = stateService.markAsApplied(operation);
            applied = copy(applied, applied.status(), applied.previewArtifacts(), applied.targetFiles(),
                    backup.toString(), List.of(), applied.validationErrors(), true, applied.appliedAt());
            ThymeleafOperationSnapshot saved = nextRevision(snapshot, applied);
            recordEvent(saved, operation.status(), applied.status(), "APPLIED");
            deleteTree(staging);
            return new WorkflowResult(saved.operation(), saved.previewHash());
        } catch (Exception exception) {
            rollback(root, backup, appliedTargets);
            deleteTree(staging);
            ThymeleafProjectOperation failed = stateService.transitionState(operation, ProjectOperationStatus.FAILED);
            failed = copy(failed, failed.status(), failed.previewArtifacts(), failed.targetFiles(),
                    backup == null ? null : backup.toString(), List.of(),
                    List.of("APPLY_ROLLED_BACK: " + exception.getMessage()), false, null);
            nextRevision(snapshot, failed);
            recordEvent(snapshot, operation.status(), failed.status(), "APPLY_ROLLED_BACK");
            throw new IllegalStateException("THYMELEAF_APPLY_ROLLED_BACK", exception);
        }
        } finally {
            lockPort.release(lock);
        }
    }

    public WorkflowResult revalidate(String operationId) {
        ThymeleafOperationSnapshot snapshot = required(operationId);
        if (snapshot.operation().status() != ProjectOperationStatus.APPLIED) {
            throw new IllegalStateException("THYMELEAF_VALIDATION_REQUIRES_APPLIED");
        }
        Path root = Path.of(snapshot.projectRoot());
        List<String> errors = new ArrayList<>();
        for (String relative : snapshot.operation().previewArtifacts().keySet()) {
            Path target = resolveTarget(root, relative);
            String content;
            try {
                content = Files.readString(target);
            } catch (IOException exception) {
                errors.add(relative + ": 파일 읽기 실패");
                continue;
            }
            var parse = validationGate.validateThymeleafParse(content);
            if (!parse.passed()) parse.issues().forEach(issue -> errors.add(relative + ": " + issue));
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

    public Optional<WorkflowResult> find(String operationId) {
        return store.findLatest(operationId).map(s -> new WorkflowResult(s.operation(), s.previewHash()));
    }

    private ThymeleafOperationSnapshot required(String id) {
        return store.findLatest(id)
                .orElseThrow(() -> new IllegalArgumentException("THYMELEAF_OPERATION_NOT_FOUND: " + id));
    }

    /** 이전 snapshot의 문맥(projectRoot·sourceHashes·designRevision·previewHash)을 유지한 채 다음 revision을 저장한다. */
    private ThymeleafOperationSnapshot nextRevision(ThymeleafOperationSnapshot previous, ThymeleafProjectOperation operation) {
        return store.save(new ThymeleafOperationSnapshot(
                previous.revision() + 1, operation, previous.projectRoot(),
                previous.sourceHashes(), previous.designRevision(), previous.previewHash()));
    }

    private void recordEvent(ThymeleafOperationSnapshot snapshot, ProjectOperationStatus from,
                             ProjectOperationStatus to, String type) {
        eventPort.append(new OperationEvent(UUID.randomUUID().toString(), snapshot.operation().operationId(),
                "THYMELEAF_PROJECT", snapshot.revision(), from == null ? null : from.name(), to.name(), type,
                "system", UUID.randomUUID().toString(), snapshot.previewHash(), java.time.Instant.now()));
    }

    private String mediaType(String name) {
        return name.toLowerCase().endsWith(".html") ? "text/html" : "text/plain";
    }

    private Path realDirectory(Path root) {
        try {
            Path real = root.toRealPath();
            if (!Files.isDirectory(real) || Files.isSymbolicLink(root)) {
                throw new IllegalArgumentException("PROJECT_ROOT_INVALID: " + root);
            }
            return real;
        } catch (IOException exception) {
            throw new IllegalArgumentException("PROJECT_ROOT_NOT_FOUND: " + root, exception);
        }
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

    private Path resolveTarget(Path root, String relative) {
        if (relative == null || relative.isBlank() || Path.of(relative).isAbsolute()) {
            throw new SecurityException("THYMELEAF_TARGET_PATH_INVALID: " + relative);
        }
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || containsSymbolicLink(root, target)) {
            throw new SecurityException("THYMELEAF_TARGET_PATH_ESCAPE: " + relative);
        }
        return target;
    }

    private boolean containsSymbolicLink(Path root, Path target) {
        Path current = root;
        for (Path part : root.relativize(target)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) return true;
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) break;
        }
        return false;
    }

    private Path resolveWithin(Path root, String relative) {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) throw new SecurityException("THYMELEAF_STAGE_PATH_ESCAPE");
        return resolved;
    }

    private String currentHash(Path path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return "MISSING";
        try {
            return hashFactory.sha256Hex(Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new IllegalStateException("SOURCE_REVISION_READ_FAILED: " + path, exception);
        }
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void rollback(Path root, Path backup, List<Path> appliedTargets) {
        for (int index = appliedTargets.size() - 1; index >= 0; index--) {
            Path target = appliedTargets.get(index);
            try {
                Path saved = backup == null ? null : backup.resolve(root.relativize(target)).normalize();
                if (saved != null && saved.startsWith(backup) && Files.exists(saved)) {
                    Files.copy(saved, target, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.deleteIfExists(target);
                }
            } catch (IOException ignored) {
                // 원래 실패를 보존하고 Workflow 결과에 rollback 실패를 기록할 후속 확장 지점.
            }
        }
    }

    private void deleteTree(Path path) {
        if (path == null || !Files.exists(path) || Files.isSymbolicLink(path)) return;
        try (var paths = Files.walk(path)) {
            for (Path item : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
        } catch (IOException ignored) {
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
}
