package com.krdevops.springai.service;

import com.krdevops.springai.config.EgovProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 운영자가 명시적으로 활성화한 경우에만 생성 대상 프로젝트를 독립 컴파일한다. */
@Service
@RequiredArgsConstructor
public class GeneratedProjectBuildValidator {

    private final EgovProperties properties;

    public BuildValidationReport validate(String projectRootPath) {
        long startedAt = System.nanoTime();
        if (!properties.getValidation().isAllowBuildExecution()) {
            return BuildValidationReport.blocked(
                    "빌드 실행이 비활성화되어 있습니다. 검토 후 EGOV_ALLOW_BUILD_EXECUTION=true로 활성화하세요.");
        }
        try {
            Path root = Path.of(projectRootPath).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) return BuildValidationReport.blocked("프로젝트 디렉터리가 없습니다: " + root);
            Path realRoot = root.toRealPath();
            if (!allowed(realRoot)) return BuildValidationReport.blocked("허용 경로 밖 프로젝트 빌드를 차단했습니다: " + realRoot);

            BuildCommand build = detect(realRoot);
            if (build == null) {
                return BuildValidationReport.blocked("지원하는 빌드 파일이 없습니다: pom.xml 또는 build.gradle(.kts)");
            }
            ProcessBuilder processBuilder = new ProcessBuilder(build.command());
            processBuilder.directory(realRoot.toFile());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            int max = Math.max(1000, properties.getValidation().getMaxOutputChars());
            StringBuilder output = new StringBuilder(Math.min(max, 8192));
            Thread outputReader = startOutputReader(process, output, max);
            boolean finished = process.waitFor(
                    Math.max(1, properties.getValidation().getBuildTimeoutSeconds()), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                outputReader.join(5000);
                return new BuildValidationReport(true, false, true, build.tool(), -1,
                        build.command(), "빌드 제한 시간을 초과했습니다.\n" + outputSnapshot(output),
                        elapsed(startedAt));
            }
            outputReader.join(5000);
            return new BuildValidationReport(true, process.exitValue() == 0, false, build.tool(),
                    process.exitValue(), build.command(), outputSnapshot(output), elapsed(startedAt));
        } catch (Exception e) {
            return new BuildValidationReport(true, false, false, "unknown", -1, List.of(),
                    e.getClass().getSimpleName() + ": " + e.getMessage(), elapsed(startedAt));
        }
    }

    private Thread startOutputReader(Process process, StringBuilder output, int max) {
        Thread reader = new Thread(() -> {
            try (InputStreamReader input = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                char[] buffer = new char[4096];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    synchronized (output) {
                        output.append(buffer, 0, read);
                        int overflow = output.length() - max;
                        if (overflow > 0) output.delete(0, overflow);
                    }
                }
            } catch (Exception ignored) {
                // 프로세스 강제 종료 시 스트림 close는 정상적인 종료 경로다.
            }
        }, "generated-project-build-output");
        reader.setDaemon(true);
        reader.start();
        return reader;
    }

    private Duration elapsed(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    private String outputSnapshot(StringBuilder output) {
        synchronized (output) {
            return output.toString();
        }
    }

    private boolean allowed(Path target) {
        List<Path> roots = new ArrayList<>();
        Path base = canonicalRoot(Path.of(properties.getOutput().getBasePath()));
        roots.add(base);
        if (base.getParent() != null) roots.add(base.getParent());
        properties.getOutput().getAllowedPaths().stream()
                .map(path -> canonicalRoot(Path.of(path)))
                .forEach(roots::add);
        return roots.stream().anyMatch(target::startsWith);
    }

    private Path canonicalRoot(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        try {
            return Files.exists(normalized) ? normalized.toRealPath() : normalized;
        } catch (Exception ignored) {
            return normalized;
        }
    }

    private BuildCommand detect(Path root) {
        if (Files.isRegularFile(root.resolve("pom.xml"))) {
            return new BuildCommand("maven", List.of("mvn", "-o", "-DskipTests", "compile"));
        }
        if (Files.isRegularFile(root.resolve("build.gradle"))
                || Files.isRegularFile(root.resolve("build.gradle.kts"))) {
            return new BuildCommand("gradle", List.of("gradle", "--offline", "compileJava"));
        }
        return null;
    }

    private record BuildCommand(String tool, List<String> command) {
    }

    public record BuildValidationReport(
            boolean executed,
            boolean passed,
            boolean timedOut,
            String buildTool,
            int exitCode,
            List<String> command,
            String output,
            Duration duration
    ) {
        public BuildValidationReport {
            command = command == null ? List.of() : List.copyOf(command);
        }

        static BuildValidationReport blocked(String reason) {
            return new BuildValidationReport(false, false, false, "none", -1, List.of(), reason, Duration.ZERO);
        }
    }
}
