package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.write.ProjectChangeSet;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.write.ApplyOutcome;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * V2_APPLY(픽셀 재현) 경로에서 CRUD 화면이 {@code th:replace}로 참조하는 KRDS 컴포넌트
 * fragment 6종({@code templates/components/krds-*.html})을 대상 프로젝트에 멱등적으로 기록한다.
 *
 * <p>이 fragment 파일들은 {@link ThymeleafKrdsComponentMappingSeeder}가 등록한
 * {@code DesignCodeComponentMapping.thymeleafFragment}("components/krds-button :: button" 등)가
 * 가리키는 실제 마크업이며, {@link ThymeleafFragmentContractValidator}의 정적 계약 검사 대상이다.
 * MCP 서버 클래스패스({@code templates/components/})에 정본이 있고, 그 바이트를 그대로 복사한다.
 *
 * <p>저장은 {@link ApprovedProjectWritePort}({@link ProjectWritePolicy#ATOMIC_APPROVED}) 한 배치로
 * 묶어 부분 적용을 막는다. 6종이 모두 최신이면 디스크를 건드리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KrdsComponentFragmentWriter {

    /** 정본 위치이자 대상 프로젝트에 복사할 파일 목록. */
    static final List<String> FRAGMENT_FILES = List.of(
            "krds-button.html", "krds-text-input.html", "krds-select.html",
            "krds-date-input.html", "krds-data-table.html", "krds-pagination.html");

    private static final String CLASSPATH_DIR = "templates/components/";
    private static final String BOOT_RELATIVE_DIR = "src/main/resources/templates/components";
    private static final String WAR_RELATIVE_DIR = "src/main/webapp/WEB-INF/templates/components";
    private static final String BOOT_TEMPLATES = "src/main/resources/templates";
    private static final String WAR_TEMPLATES = "src/main/webapp/WEB-INF/templates";

    private final CodeService codeService;
    private final ApprovedProjectWritePort writePort;
    private final OperationHashFactory hashFactory;

    public FragmentWriteResult ensureComponentFragments(String outputPath) {
        String relativeDir = resolveRelativeDir(outputPath);
        if (relativeDir == null) {
            return new FragmentWriteResult(Status.NOT_FOUND, List.of(),
                    "templates 디렉터리를 찾을 수 없습니다. generateThymeleafLayout를 먼저 실행하세요.");
        }
        List<ProjectChangeSet.FileChange> changes = new ArrayList<>();
        List<String> written = new ArrayList<>();
        try {
            for (String name : FRAGMENT_FILES) {
                String desired = readClasspath(CLASSPATH_DIR + name);
                String relativePath = relativeDir + "/" + name;
                Path target = Path.of(outputPath, relativePath);
                boolean existed = Files.exists(target);
                String current = existed ? Files.readString(target, StandardCharsets.UTF_8) : null;
                if (desired.equals(current)) {
                    continue;
                }
                String beforeHash = existed
                        ? hashFactory.sha256Hex(current.getBytes(StandardCharsets.UTF_8)) : null;
                changes.add(new ProjectChangeSet.FileChange(relativePath, beforeHash, desired, null));
                written.add(name);
            }
        } catch (IOException e) {
            log.warn("[krds-fragment] fragment 정본 읽기/비교 실패: {}", e.getMessage());
            return new FragmentWriteResult(Status.FAILED, List.of(), e.getMessage());
        }
        if (changes.isEmpty()) {
            return new FragmentWriteResult(Status.PRESERVED, List.of(),
                    "KRDS 컴포넌트 fragment 6종 최신 상태 유지");
        }
        codeService.validateOutputRoot(outputPath);
        ProjectChangeSet changeSet = new ProjectChangeSet(
                outputPath, null, changes, List.of(), ProjectWritePolicy.ATOMIC_APPROVED);
        ApplyOutcome outcome = writePort.apply(changeSet);
        if (outcome.status() != ApplyOutcome.Status.APPLIED) {
            return new FragmentWriteResult(Status.FAILED, List.of(), describe(outcome));
        }
        written.forEach(name -> log.info("[krds-fragment] fragment 기록 완료: {}/{}", relativeDir, name));
        return new FragmentWriteResult(Status.WRITTEN, List.copyOf(written),
                written.size() + "종 KRDS 컴포넌트 fragment 기록");
    }

    private String resolveRelativeDir(String outputPath) {
        if (Files.isDirectory(Path.of(outputPath, WAR_TEMPLATES))) {
            return WAR_RELATIVE_DIR;
        }
        if (Files.isDirectory(Path.of(outputPath, BOOT_TEMPLATES))) {
            return BOOT_RELATIVE_DIR;
        }
        return null;
    }

    private String readClasspath(String resource) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("클래스패스 fragment 정본 없음: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String describe(ApplyOutcome outcome) {
        return switch (outcome.status()) {
            case CONFLICT -> "적용 직전 파일이 변경됨: " + outcome.conflictingPaths();
            case ROLLED_BACK -> outcome.failureDetail();
            case ROLLBACK_FAILED -> "복구까지 실패함(" + outcome.failureDetail()
                    + ") — 원본 상태로 안 돌아갔을 수 있습니다: " + outcome.failureMessages();
            default -> "알 수 없는 결과: " + outcome.status();
        };
    }

    public enum Status { WRITTEN, PRESERVED, NOT_FOUND, FAILED }

    public record FragmentWriteResult(Status status, List<String> writtenFiles, String message) {
        public FragmentWriteResult {
            writtenFiles = List.copyOf(writtenFiles);
        }

        public boolean failed() {
            return status == Status.FAILED || status == Status.NOT_FOUND;
        }
    }
}
