package com.krdevops.springai.service.write;

import com.krdevops.springai.model.write.ProjectChangeSet;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.contract.OperationHashFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WP7/ARCH-0703/0706/0710/0711/0712: {@code ThymeleafProjectWorkflowService.apply()}가 검증해온
 * staging→backup→atomic replace→실패 시 rollback 절차를 {@link ProjectChangeSet} 기반으로
 * 일반화한 구현체. {@link ProjectWritePolicy#ATOMIC_APPROVED}는 drift가 하나라도 있으면 아무 것도
 * 쓰지 않고 {@link ApplyOutcome.Status#CONFLICT}를 반환한다("전부 아니면 전무"). 경로 escape는
 * {@link SafePathResolver}가 즉시 {@link SecurityException}으로 막는다 — 이건 "충돌"이 아니라
 * 공격/버그이므로 outcome이 아니라 예외로 표현한다.
 */
@Component
public class FileSystemApprovedProjectWritePort implements ApprovedProjectWritePort {

    private final SafePathResolver pathResolver;
    private final OperationHashFactory hashFactory;

    public FileSystemApprovedProjectWritePort(SafePathResolver pathResolver, OperationHashFactory hashFactory) {
        this.pathResolver = pathResolver;
        this.hashFactory = hashFactory;
    }

    @Override
    public ApplyOutcome apply(ProjectChangeSet changeSet) {
        Path requestedRoot = Path.of(changeSet.projectRootRef());
        // BEST_EFFORT_COMPATIBILITY는 신규 프로젝트 최초 생성처럼 root가 아직 없는 경우를 지원해야
        // 한다 — codeService.saveGeneratedCode가 지금까지 Files.createDirectories로 그래왔듯.
        // ATOMIC_APPROVED(Thymeleaf)는 항상 기존 프로젝트를 다루므로 이 완화가 필요하지 않다.
        if (changeSet.policy() == ProjectWritePolicy.BEST_EFFORT_COMPATIBILITY) {
            createDirectoriesIfMissing(requestedRoot);
        }
        Path root = pathResolver.realDirectory(requestedRoot);

        // 경로 검증은 drift 검사보다 먼저 한다 — root 이탈은 승인된 변경으로도 정당화될 수 없는
        // 별도 위반이라 CONFLICT가 아니라 예외로 즉시 중단한다.
        for (var change : changeSet.generatedFiles()) {
            pathResolver.resolveTarget(root, change.path());
        }
        for (var deletion : changeSet.deletions()) {
            pathResolver.resolveTarget(root, deletion.path());
        }

        if (changeSet.policy() == ProjectWritePolicy.BEST_EFFORT_COMPATIBILITY) {
            return applyBestEffort(root, changeSet);
        }

        List<String> conflicts = detectConflicts(root, changeSet);
        if (!conflicts.isEmpty()) {
            return ApplyOutcome.conflict(conflicts);
        }

        return stageAndApply(root, changeSet);
    }

    /**
     * {@link ProjectWritePolicy#BEST_EFFORT_COMPATIBILITY} 전용: drift 검사·staging·backup·rollback
     * 없이 파일마다 독립적으로 쓴다. 승인·hash 검증 없이 즉시 쓰던 기존 Tool(예: {@code CodeService})과
     * 동일한 보장 수준을 그대로 유지한다 — 하나 실패해도 이미 쓴 다른 파일은 되돌리지 않는다.
     */
    private ApplyOutcome applyBestEffort(Path root, ProjectChangeSet changeSet) {
        List<String> appliedPaths = new ArrayList<>();
        Map<String, String> failureMessages = new LinkedHashMap<>();
        for (var change : changeSet.generatedFiles()) {
            try {
                Path target = pathResolver.resolveTarget(root, change.path());
                Files.createDirectories(target.getParent());
                writeContent(target, change);
                appliedPaths.add(change.path());
            } catch (IOException exception) {
                failureMessages.put(change.path(), exception.getMessage());
            }
        }
        for (var deletion : changeSet.deletions()) {
            try {
                Files.deleteIfExists(pathResolver.resolveTarget(root, deletion.path()));
                appliedPaths.add(deletion.path());
            } catch (IOException exception) {
                failureMessages.put(deletion.path(), exception.getMessage());
            }
        }
        return failureMessages.isEmpty()
                ? ApplyOutcome.applied(appliedPaths, null)
                : ApplyOutcome.partiallyApplied(appliedPaths, failureMessages);
    }

    private void writeContent(Path target, ProjectChangeSet.FileChange change) throws IOException {
        if (change.isBinary()) {
            Files.write(target, change.binaryContent());
        } else {
            Files.writeString(target, change.content(), StandardCharsets.UTF_8);
        }
    }

    private void createDirectoriesIfMissing(Path root) {
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("PROJECT_ROOT_CREATE_FAILED: " + root, exception);
        }
    }

    private List<String> detectConflicts(Path root, ProjectChangeSet changeSet) {
        List<String> conflicts = new ArrayList<>();
        for (var change : changeSet.generatedFiles()) {
            if (!change.beforeHash().equals(currentHash(pathResolver.resolveTarget(root, change.path())))) {
                conflicts.add(change.path());
            }
        }
        for (var deletion : changeSet.deletions()) {
            if (!deletion.beforeHash().equals(currentHash(pathResolver.resolveTarget(root, deletion.path())))) {
                conflicts.add(deletion.path());
            }
        }
        return conflicts;
    }

    private ApplyOutcome stageAndApply(Path root, ProjectChangeSet changeSet) {
        Path staging = null;
        Path backup = null;
        List<Path> appliedTargets = new ArrayList<>();
        Set<Path> deletedTargets = new LinkedHashSet<>();
        try {
            staging = Files.createTempDirectory(root, ".write-stage-");
            backup = Files.createTempDirectory(root, ".write-backup-");

            for (var change : changeSet.generatedFiles()) {
                Path staged = pathResolver.resolveWithin(staging, change.path());
                Files.createDirectories(staged.getParent());
                writeContent(staged, change);
                backupIfExists(root, backup, change.path());
            }
            for (var deletion : changeSet.deletions()) {
                backupIfExists(root, backup, deletion.path());
            }

            List<String> appliedPaths = new ArrayList<>();
            for (var change : changeSet.generatedFiles()) {
                Path target = pathResolver.resolveTarget(root, change.path());
                Files.createDirectories(target.getParent());
                moveReplacing(pathResolver.resolveWithin(staging, change.path()), target);
                appliedTargets.add(target);
                appliedPaths.add(change.path());
            }
            for (var deletion : changeSet.deletions()) {
                Path target = pathResolver.resolveTarget(root, deletion.path());
                Files.deleteIfExists(target);
                deletedTargets.add(target);
                appliedPaths.add(deletion.path());
            }

            deleteTree(staging);
            // backup은 성공 후에도 지우지 않는다 — 사후 수동 복구를 위해 남겨둔다(ApplyOutcome
            // Javadoc 참고). staging만 임시 산출물이라 지운다.
            return ApplyOutcome.applied(appliedPaths, backup.toString());
        } catch (Exception exception) {
            rollback(root, backup, appliedTargets, deletedTargets);
            deleteTree(staging);
            // backup은 지우지 않는다 — 파일은 이미 이 백업으로 복구됐지만, 실패 이후 무엇을
            // 시도했었는지 사후 확인할 수 있게 남겨둔다(ApplyOutcome.rolledBack Javadoc 참고).
            return ApplyOutcome.rolledBack(exception.getMessage(), backup == null ? null : backup.toString());
        }
    }

    private void backupIfExists(Path root, Path backup, String relative) throws IOException {
        Path target = pathResolver.resolveTarget(root, relative);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            Path savedBackup = pathResolver.resolveWithin(backup, relative);
            Files.createDirectories(savedBackup.getParent());
            Files.copy(target, savedBackup, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 이미 적용/삭제된 대상을 역순으로 되돌린다. 실패한 항목은 무시하고 계속 진행한다(최대한 복구). */
    private void rollback(Path root, Path backup, List<Path> appliedTargets, Set<Path> deletedTargets) {
        List<Path> allTargets = new ArrayList<>(appliedTargets);
        allTargets.addAll(deletedTargets);
        for (int index = allTargets.size() - 1; index >= 0; index--) {
            Path target = allTargets.get(index);
            try {
                Path saved = backup == null ? null : backup.resolve(root.relativize(target)).normalize();
                if (saved != null && saved.startsWith(backup) && Files.exists(saved)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(saved, target, StandardCopyOption.REPLACE_EXISTING);
                } else if (!deletedTargets.contains(target)) {
                    Files.deleteIfExists(target);
                }
            } catch (IOException ignored) {
                // 원래 실패를 outcome.failureDetail()에 보존한다 — 개별 rollback 실패까지 여기서
                // 다시 던지면 원인이 rollback 실패로 덮여쓰기 때문에 최대한 복구만 시도한다.
            }
        }
    }

    private String currentHash(Path path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return "MISSING";
        }
        try {
            return hashFactory.sha256Hex(Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new IllegalStateException("PROJECT_SOURCE_READ_FAILED: " + path, exception);
        }
    }

    private void deleteTree(Path path) {
        if (path == null || !Files.exists(path) || Files.isSymbolicLink(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path item : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        } catch (IOException ignored) {
        }
    }
}
