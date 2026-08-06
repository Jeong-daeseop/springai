package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafOperationSnapshot;
import com.krdevops.springai.model.thymeleaf.ThymeleafProjectOperation;
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
    private final DesignMdRuleLoader designRuleLoader;
    private final ThymeleafOperationStore store;
    private final OperationEventPort eventPort;
    private final OperationLockPort lockPort;
    private final ArtifactService artifactService;
    private final SafePathResolver pathResolver;
    private final ApprovedProjectWritePort writePort;

    @Autowired
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
            ApprovedProjectWritePort writePort) {
        this.stateService = stateService;
        this.validationGate = validationGate;
        this.hashFactory = hashFactory;
        this.designRuleLoader = designRuleLoader;
        this.store = store;
        this.eventPort = eventPort;
        this.lockPort = lockPort;
        this.artifactService = artifactService;
        this.pathResolver = pathResolver;
        this.writePort = writePort;
    }

    public ThymeleafProjectWorkflowService(ProjectOperationStateService stateService,
            ValidationGateExecutor validationGate, OperationHashFactory hashFactory,
            DesignMdRuleLoader designRuleLoader, ThymeleafOperationStore store) {
        this(stateService, validationGate, hashFactory, designRuleLoader, store,
                new NoopOperationEventPort(), new NoopOperationLockPort(), null,
                new SafePathResolver(), new FileSystemApprovedProjectWritePort(new SafePathResolver(), hashFactory));
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
        Path root = pathResolver.realDirectory(projectRoot);
        String designRevision = currentDesignRevision(root);
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

    public WorkflowResult revalidate(String operationId) {
        ThymeleafOperationSnapshot snapshot = required(operationId);
        if (snapshot.operation().status() != ProjectOperationStatus.APPLIED) {
            throw new IllegalStateException("THYMELEAF_VALIDATION_REQUIRES_APPLIED");
        }
        Path root = Path.of(snapshot.projectRoot());
        List<String> errors = new ArrayList<>();
        for (String relative : snapshot.operation().previewArtifacts().keySet()) {
            Path target = pathResolver.resolveTarget(root, relative);
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
}
