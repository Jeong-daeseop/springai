package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.service.contract.OperationHashFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * I-2B: 대상 eGovFrame 프로젝트의 JSP·Controller·VO 소스를 안전하게 읽기 위한 경로 검증과 예산 관리.
 * 설정·build script는 절대 실행하지 않는다 — 이 서비스는 파일을 읽기만 하며 어디에서도
 * {@code ProcessBuilder}/{@code Runtime.exec}를 호출하지 않는다.
 */
@Service
public class LegacySourceInventoryService {

    private static final Set<String> EXCLUDED_DIR_SEGMENTS = Set.of(
            ".git", "build", "node_modules", "upload", "uploads", "temp", "tmp",
            "target", "out", ".idea", ".gradle", ".svn");
    private static final Set<String> EXCLUDED_FILE_EXTENSIONS = Set.of(
            "env", "pem", "key", "crt", "cer", "p12", "jks", "keystore");
    private static final List<String> EXCLUDED_FILENAME_SUBSTRINGS = List.of(
            "credential", "secret", "password");

    private final OperationHashFactory operationHashFactory;

    public LegacySourceInventoryService(OperationHashFactory operationHashFactory) {
        this.operationHashFactory = operationHashFactory;
    }

    /** 원본 root 아래 실제 경로로만 정규화되고, 제외 목록에 해당하지 않는 파일을 검증한다. */
    public Path resolveAllowedFile(Path allowedRoot, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw securityViolation("SOURCE_PATH_BLANK", relativePath);
        }

        Path realRoot = toRealPath(allowedRoot, "SOURCE_ROOT_NOT_FOUND");
        Path candidate = realRoot.resolve(relativePath).normalize();
        Path realCandidate = toRealPath(candidate, "SOURCE_FILE_NOT_FOUND");

        if (!realCandidate.startsWith(realRoot)) {
            throw securityViolation("SOURCE_PATH_TRAVERSAL", relativePath);
        }
        if (!Files.isRegularFile(realCandidate)) {
            throw new IllegalArgumentException("SOURCE_NOT_REGULAR_FILE: " + relativePath);
        }

        for (Path segment : realRoot.relativize(realCandidate)) {
            if (EXCLUDED_DIR_SEGMENTS.contains(segment.toString().toLowerCase(Locale.ROOT))) {
                throw securityViolation("SOURCE_PATH_EXCLUDED_SEGMENT", relativePath);
            }
        }

        String fileName = realCandidate.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.equals(".env")) {
            throw securityViolation("SOURCE_FILE_EXCLUDED_ENV", relativePath);
        }
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1);
        if (EXCLUDED_FILE_EXTENSIONS.contains(extension)) {
            throw securityViolation("SOURCE_FILE_EXCLUDED_EXTENSION", relativePath);
        }
        for (String needle : EXCLUDED_FILENAME_SUBSTRINGS) {
            if (fileName.contains(needle)) {
                throw securityViolation("SOURCE_FILE_EXCLUDED_NAME", relativePath);
            }
        }

        return realCandidate;
    }

    /** {@code budget}이 소진되면 이 호출에서 즉시 실패한다(개별 파일이 아니라 누적 기준). */
    public ReadSourceFile readSourceFile(Path allowedRoot, String relativePath, SourceReadBudget budget) {
        Path file = resolveAllowedFile(allowedRoot, relativePath);
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new IllegalStateException("SOURCE_FILE_READ_FAILED: " + relativePath, exception);
        }
        budget.consume(bytes.length);
        String content = new String(bytes, StandardCharsets.UTF_8);
        return new ReadSourceFile(relativePath, content, operationHashFactory.sha256Hex(bytes), bytes.length);
    }

    /**
     * I-5B: {@link #resolveAllowedFile}과 달리 대상 파일이 아직 없어도(신규 생성) 통과한다.
     * root 실제 경로 안에서만 정규화되고, 이미 존재하면 symlink까지 실제 경로로 재검증한다.
     */
    public Path resolveAllowedWriteTarget(Path allowedRoot, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw securityViolation("SOURCE_PATH_BLANK", relativePath);
        }

        Path realRoot = toRealPath(allowedRoot, "SOURCE_ROOT_NOT_FOUND");
        Path candidate = realRoot.resolve(relativePath).normalize();
        if (!candidate.startsWith(realRoot)) {
            throw securityViolation("SOURCE_PATH_TRAVERSAL", relativePath);
        }
        if (Files.exists(candidate)) {
            Path realCandidate = toRealPath(candidate, "SOURCE_FILE_NOT_FOUND");
            if (!realCandidate.startsWith(realRoot)) {
                throw securityViolation("SOURCE_PATH_TRAVERSAL", relativePath);
            }
            candidate = realCandidate;
        }

        for (Path segment : realRoot.relativize(candidate)) {
            if (EXCLUDED_DIR_SEGMENTS.contains(segment.toString().toLowerCase(Locale.ROOT))) {
                throw securityViolation("SOURCE_PATH_EXCLUDED_SEGMENT", relativePath);
            }
        }

        String fileName = candidate.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.equals(".env")) {
            throw securityViolation("SOURCE_FILE_EXCLUDED_ENV", relativePath);
        }
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1);
        if (EXCLUDED_FILE_EXTENSIONS.contains(extension)) {
            throw securityViolation("SOURCE_FILE_EXCLUDED_EXTENSION", relativePath);
        }
        for (String needle : EXCLUDED_FILENAME_SUBSTRINGS) {
            if (fileName.contains(needle)) {
                throw securityViolation("SOURCE_FILE_EXCLUDED_NAME", relativePath);
            }
        }

        return candidate;
    }

    /**
     * I-5B "기존 파일 Backup과 충돌 보고": 기존 파일이 있으면 형제 경로에 타임스탬프 백업을 남긴
     * 뒤에만 덮어쓴다. 새 파일이면 백업 없이 바로 생성한다({@code backupPath=null}).
     */
    public WriteResult writeFileWithBackup(Path allowedRoot, String relativePath, String content) {
        Path target = resolveAllowedWriteTarget(allowedRoot, relativePath);
        try {
            Path backupPath = null;
            if (Files.exists(target)) {
                backupPath = target.resolveSibling(
                        target.getFileName() + ".bak-" + System.currentTimeMillis());
                Files.copy(target, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
            return new WriteResult(target, backupPath);
        } catch (IOException exception) {
            throw new IllegalStateException("SOURCE_FILE_WRITE_FAILED: " + relativePath, exception);
        }
    }

    /** 화면 분석에 관여한 여러 파일 hash를 정렬된 순서로 묶어 하나의 project fingerprint로 만든다. */
    public String projectFingerprint(List<String> fileHashesInDeterministicOrder) {
        if (fileHashesInDeterministicOrder == null || fileHashesInDeterministicOrder.isEmpty()) {
            throw new IllegalArgumentException("fileHashesInDeterministicOrder는 최소 1개 이상이어야 합니다.");
        }
        String joined = String.join("|", fileHashesInDeterministicOrder);
        return operationHashFactory.sha256Hex(joined.getBytes(StandardCharsets.UTF_8));
    }

    private Path toRealPath(Path path, String errorCode) {
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            throw new IllegalArgumentException(errorCode + ": " + path, exception);
        }
    }

    private SecurityException securityViolation(String code, String relativePath) {
        return new SecurityException(code + ": " + relativePath);
    }

    public record ReadSourceFile(String relativePath, String content, String sha256Hex, long sizeBytes) {
    }

    public record WriteResult(Path writtenPath, Path backupPath) {
    }
}
