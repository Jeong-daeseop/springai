package com.krdevops.springai.service.thymeleaf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.service.contract.OperationHashFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** I-2B 완료 게이트: allowed root 밖 경로·심볼릭 링크 우회·제외 파일·예산 초과를 모두 차단한다. */
class LegacySourceInventoryServiceTest {

    private final LegacySourceInventoryService service =
            new LegacySourceInventoryService(new OperationHashFactory(new ObjectMapper()));

    @TempDir
    Path projectRoot;

    private Path srcJava;

    @BeforeEach
    void setUp() throws IOException {
        srcJava = Files.createDirectories(projectRoot.resolve("src/main/java/egovframework/let/emp/web"));
        Files.writeString(srcJava.resolve("EmpController.java"), "public class EmpController {}");
    }

    @Test
    void resolvesAFileInsideAllowedRoot() {
        Path resolved = service.resolveAllowedFile(
                projectRoot, "src/main/java/egovframework/let/emp/web/EmpController.java");
        assertThat(resolved).isRegularFile();
    }

    @Test
    void rejectsPathTraversalOutsideRoot() throws IOException {
        Path outsideRoot = Files.createTempDirectory("outside-root");
        Path outsideFile = Files.writeString(outsideRoot.resolve("outside.java"), "class Outside {}");
        String traversalPath = projectRoot.relativize(outsideFile).toString();

        assertThatThrownBy(() -> service.resolveAllowedFile(projectRoot, traversalPath))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SOURCE_PATH_TRAVERSAL");
    }

    @Test
    void rejectsSymlinkEscapingRoot() throws IOException {
        Path outside = Files.createTempDirectory("outside-root");
        Path secretFile = Files.writeString(outside.resolve("secret.java"), "class Secret {}");
        Path symlink = projectRoot.resolve("src/main/java/link.java");
        try {
            Files.createSymbolicLink(symlink, secretFile);
        } catch (UnsupportedOperationException | IOException unsupported) {
            return;
        }
        assertThatThrownBy(() -> service.resolveAllowedFile(projectRoot, "src/main/java/link.java"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SOURCE_PATH_TRAVERSAL");
    }

    @Test
    void rejectsEnvFile() throws IOException {
        Files.writeString(projectRoot.resolve(".env"), "DB_PASSWORD=secret");
        assertThatThrownBy(() -> service.resolveAllowedFile(projectRoot, ".env"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SOURCE_FILE_EXCLUDED_ENV");
    }

    @Test
    void rejectsKeyAndCredentialFiles() throws IOException {
        Files.writeString(projectRoot.resolve("server.key"), "-----BEGIN KEY-----");
        Files.writeString(projectRoot.resolve("db-credential.properties"), "user=root");

        assertThatThrownBy(() -> service.resolveAllowedFile(projectRoot, "server.key"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SOURCE_FILE_EXCLUDED_EXTENSION");
        assertThatThrownBy(() -> service.resolveAllowedFile(projectRoot, "db-credential.properties"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SOURCE_FILE_EXCLUDED_NAME");
    }

    @Test
    void rejectsExcludedDirectorySegments() throws IOException {
        Path gitDir = Files.createDirectories(projectRoot.resolve(".git"));
        Files.writeString(gitDir.resolve("config"), "[core]");
        Path nodeModules = Files.createDirectories(projectRoot.resolve("node_modules/pkg"));
        Files.writeString(nodeModules.resolve("index.js"), "module.exports = {};");

        assertThatThrownBy(() -> service.resolveAllowedFile(projectRoot, ".git/config"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SOURCE_PATH_EXCLUDED_SEGMENT");
        assertThatThrownBy(() -> service.resolveAllowedFile(projectRoot, "node_modules/pkg/index.js"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SOURCE_PATH_EXCLUDED_SEGMENT");
    }

    @Test
    void readSourceFileReturnsContentAndHash() {
        LegacySourceInventoryService.ReadSourceFile read = service.readSourceFile(
                projectRoot, "src/main/java/egovframework/let/emp/web/EmpController.java",
                SourceReadBudget.defaultBudget());

        assertThat(read.content()).isEqualTo("public class EmpController {}");
        assertThat(read.sha256Hex()).matches("^[a-f0-9]{64}$");
        assertThat(read.sizeBytes()).isEqualTo("public class EmpController {}".getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void budgetRejectsFileLargerThanPerFileLimit() {
        SourceReadBudget tinyBudget = new SourceReadBudget(10, 5, 100);
        assertThatThrownBy(() -> service.readSourceFile(
                projectRoot, "src/main/java/egovframework/let/emp/web/EmpController.java", tinyBudget))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SOURCE_FILE_TOO_LARGE");
    }

    @Test
    void budgetRejectsAfterFileCountExceeded() throws IOException {
        Files.writeString(srcJava.resolve("Second.java"), "class Second {}");
        SourceReadBudget budget = new SourceReadBudget(1, 1024, 1024);

        service.readSourceFile(
                projectRoot, "src/main/java/egovframework/let/emp/web/EmpController.java", budget);

        assertThatThrownBy(() -> service.readSourceFile(
                projectRoot, "src/main/java/egovframework/let/emp/web/Second.java", budget))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SOURCE_FILE_COUNT_EXCEEDED");
    }

    @Test
    void writeFileWithBackupCreatesNewFileWithoutBackupWhenAbsent() {
        LegacySourceInventoryService.WriteResult result = service.writeFileWithBackup(
                projectRoot, "output/employer/EgovEmployerList.html", "<html>list</html>");

        assertThat(result.backupPath()).isNull();
        assertThat(Files.exists(result.writtenPath())).isTrue();
    }

    @Test
    void writeFileWithBackupPreservesPreviousContentOnOverwrite() throws IOException {
        Path relative = Path.of("output/employer/EgovEmployerList.html");
        Files.createDirectories(projectRoot.resolve("output/employer"));
        Files.writeString(projectRoot.resolve(relative), "<html>old</html>");

        LegacySourceInventoryService.WriteResult result = service.writeFileWithBackup(
                projectRoot, relative.toString(), "<html>new</html>");

        assertThat(result.backupPath()).isNotNull();
        assertThat(Files.readString(result.backupPath())).isEqualTo("<html>old</html>");
        assertThat(Files.readString(result.writtenPath())).isEqualTo("<html>new</html>");
    }

    @Test
    void writeTargetRejectsPathTraversalOutsideRoot() throws IOException {
        Path outsideRoot = Files.createTempDirectory("outside-root-write");
        Path traversalTarget = outsideRoot.resolve("evil.html");
        String traversalPath = projectRoot.relativize(traversalTarget).toString();

        assertThatThrownBy(() -> service.writeFileWithBackup(projectRoot, traversalPath, "<html></html>"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SOURCE_PATH_TRAVERSAL");
    }

    @Test
    void projectFingerprintIsDeterministicForSameHashes() {
        java.util.List<String> hashes = java.util.List.of("aaa", "bbb", "ccc");
        assertThat(service.projectFingerprint(hashes)).isEqualTo(service.projectFingerprint(hashes));
        assertThat(service.projectFingerprint(hashes)).matches("^[a-f0-9]{64}$");
    }
}
