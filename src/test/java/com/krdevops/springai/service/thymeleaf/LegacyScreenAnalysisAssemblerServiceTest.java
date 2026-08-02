package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.thymeleaf.LegacyConversionRequest;
import com.krdevops.springai.model.thymeleaf.LegacyScreenAnalysis;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * I-2C: LegacyScreenAnalysisAssemblerService 테스트.
 * 골든 fixture(EgovEmployerList.jsp + EgovEmployerController.java + EmployerVO.java)를
 * 사용하여 정상 분석 및 보안 위반 시 예외를 검증한다.
 */
class LegacyScreenAnalysisAssemblerServiceTest {

    private static final Path BASELINE = Path.of("src/test/resources/generation/baseline/crud-jsp");

    private final OperationHashFactory hashFactory = new OperationHashFactory(new ObjectMapper().findAndRegisterModules());

    private LegacySourceInventoryService inventoryService;
    private JspSourceReader jspReader;
    private ControllerSourceReader controllerReader;
    private VoSourceReader voReader;
    private LegacyScreenAnalysisAssemblerService assembler;

    @BeforeEach
    void setUp() {
        inventoryService = new LegacySourceInventoryService(hashFactory);
        jspReader = new JspSourceReader();
        controllerReader = new ControllerSourceReader();
        voReader = new VoSourceReader();
        assembler = new LegacyScreenAnalysisAssemblerService(
                inventoryService, jspReader, controllerReader, voReader);
    }

    @Test
    void analyzesGoldenFixtureSuccessfully() throws Exception {
        assumeFixtureExists();

        String screenId = "emp-list-" + UUID.randomUUID();
        LegacyConversionRequest request = new LegacyConversionRequest(
                "req-" + UUID.randomUUID(),
                BASELINE.toAbsolutePath().toString(),
                screenId,
                LegacyScreenRole.LIST,
                "EgovEmployerList.jsp",
                "EgovEmployerController.java",
                "EmployerVO.java",
                Instant.now()
        );

        LegacyScreenAnalysis analysis = assembler.analyze(request);

        assertThat(analysis).isNotNull();
        assertThat(analysis.screenId()).isEqualTo(screenId);
        assertThat(analysis.screenRole()).isEqualTo(LegacyScreenRole.LIST);
        assertThat(analysis.jsp()).isNotNull();
        assertThat(analysis.controller()).isNotNull();
        assertThat(analysis.vo()).isNotNull();
        assertThat(analysis.sourceRevision()).isNotNull();
        assertThat(analysis.issues()).isEmpty();
    }

    @Test
    void jspEvidenceExtractsFormAndDisplayFields() throws Exception {
        assumeFixtureExists();

        LegacyConversionRequest request = goldenRequest("emp-list-" + UUID.randomUUID());
        LegacyScreenAnalysis analysis = assembler.analyze(request);

        // EgovEmployerList.jsp에서 form과 display field 추출 확인
        assertThat(analysis.jsp().forms()).isNotEmpty();
        assertThat(analysis.jsp().displayFields()).isNotEmpty();
    }

    @Test
    void controllerEvidenceExtractsMethods() throws Exception {
        assumeFixtureExists();

        LegacyConversionRequest request = goldenRequest("emp-list-" + UUID.randomUUID());
        LegacyScreenAnalysis analysis = assembler.analyze(request);

        // EgovEmployerController에서 메서드 추출 확인
        assertThat(analysis.controller().methods()).isNotEmpty();
    }

    @Test
    void voEvidenceExtractsFields() throws Exception {
        assumeFixtureExists();

        LegacyConversionRequest request = goldenRequest("emp-list-" + UUID.randomUUID());
        LegacyScreenAnalysis analysis = assembler.analyze(request);

        // EmployerVO에서 필드 추출 확인
        assertThat(analysis.vo().fields()).isNotEmpty();
    }

    @Test
    void sourceRevisionReflectsFileHashes() throws Exception {
        assumeFixtureExists();

        LegacyConversionRequest request = goldenRequest("emp-list-" + UUID.randomUUID());
        LegacyScreenAnalysis analysis1 = assembler.analyze(request);
        LegacyScreenAnalysis analysis2 = assembler.analyze(request);

        // 동일 파일의 revision은 동일해야 함 (deterministic hash)
        assertThat(analysis1.sourceRevision().revisionToken())
                .isEqualTo(analysis2.sourceRevision().revisionToken());
    }

    @Test
    void rejectsPathTraversal(@TempDir Path tempDir) throws Exception {
        // 임시 프로젝트 루트 설정
        Path projectRoot = tempDir.resolve("project");
        Files.createDirectories(projectRoot);
        Files.createDirectories(projectRoot.resolve("safe"));

        // 프로젝트 루트 외부에 파일 생성
        Files.createDirectories(tempDir.resolve("outside"));
        Files.writeString(tempDir.resolve("outside/test.jsp"), "<%@ page %>");

        // 프로젝트 내부 파일도 생성 (분석이 진행되면 안 되므로 의도적으로 경로 탈출 시도)
        Files.writeString(projectRoot.resolve("safe/Controller.java"), "public class Controller {}");
        Files.writeString(projectRoot.resolve("safe/VO.java"), "public class VO {}");

        // 상대 경로로 ../outside/ 접근 시도
        LegacyConversionRequest request = new LegacyConversionRequest(
                "req-test",
                projectRoot.toAbsolutePath().toString(),
                "test",
                LegacyScreenRole.LIST,
                "../outside/test.jsp",  // 경로 탈출 시도
                "safe/Controller.java",
                "safe/VO.java",
                Instant.now()
        );

        assertThatThrownBy(() -> assembler.analyze(request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SOURCE_PATH_TRAVERSAL");
    }

    @Test
    void rejectsExcludedDirectory(@TempDir Path tempDir) throws Exception {
        // build/ 디렉터리는 제외 목록에 있음
        Files.createDirectories(tempDir.resolve("build"));
        Files.writeString(tempDir.resolve("build/test.jsp"), "<%@ page %>");

        LegacyConversionRequest request = new LegacyConversionRequest(
                "req-test",
                tempDir.toAbsolutePath().toString(),
                "test",
                LegacyScreenRole.LIST,
                "build/test.jsp",  // 제외 디렉터리
                "Controller.java",
                "VO.java",
                Instant.now()
        );

        assertThatThrownBy(() -> assembler.analyze(request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SOURCE_PATH_EXCLUDED_SEGMENT");
    }

    @Test
    void rejectsCredentialFiles(@TempDir Path tempDir) throws Exception {
        // .env 파일은 민감 파일로 제외됨
        Files.writeString(tempDir.resolve(".env"), "DB_PASSWORD=secret");

        LegacyConversionRequest request = new LegacyConversionRequest(
                "req-test",
                tempDir.toAbsolutePath().toString(),
                "test",
                LegacyScreenRole.LIST,
                ".env",
                "Controller.java",
                "VO.java",
                Instant.now()
        );

        assertThatThrownBy(() -> assembler.analyze(request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SOURCE_FILE_EXCLUDED");
    }

    @Test
    void throwsOnMissingFile(@TempDir Path tempDir) {
        LegacyConversionRequest request = new LegacyConversionRequest(
                "req-test",
                tempDir.toAbsolutePath().toString(),
                "test",
                LegacyScreenRole.LIST,
                "nonexistent.jsp",
                "nonexistent.java",
                "nonexistent.java",
                Instant.now()
        );

        assertThatThrownBy(() -> assembler.analyze(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SOURCE_FILE_NOT_FOUND");
    }

    // ===== Helper Methods =====

    private LegacyConversionRequest goldenRequest(String screenId) {
        return new LegacyConversionRequest(
                "req-" + UUID.randomUUID(),
                BASELINE.toAbsolutePath().toString(),
                screenId,
                LegacyScreenRole.LIST,
                "EgovEmployerList.jsp",
                "EgovEmployerController.java",
                "EmployerVO.java",
                Instant.now()
        );
    }

    private void assumeFixtureExists() {
        if (!Files.exists(BASELINE)) {
            throw new AssertionError("Fixture directory not found: " + BASELINE);
        }
    }
}
