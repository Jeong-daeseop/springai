package com.krdevops.springai.service.generation.layout;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 생성된 프로젝트 트리의 {@code *.java} 를 현재 테스트 런타임 classpath + Lombok 어노테이션
 * 프로세서로 in-process 컴파일하고, 결과 클래스를 격리된 {@link URLClassLoader} 로 노출한다.
 *
 * <p>"generateThymeleafLayout() 이 뱉은 소스가 실제로 컴파일되는가 / 그 클래스로 부팅되는가" 를
 * 별도 Gradle 하위 빌드 없이 검증하기 위한 테스트 전용 유틸.
 */
final class GeneratedProjectCompiler {

    private GeneratedProjectCompiler() {}

    record Compiled(Path classesDir, URLClassLoader classLoader, List<String> errors, List<String> warnings)
            implements AutoCloseable {
        boolean succeeded() {
            return errors.isEmpty();
        }

        @Override
        public void close() {
            try {
                classLoader.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    /** 테스트 JVM 에 javac(=JDK) 가 있어야 한다. 없으면 호출측이 assumeTrue 로 skip. */
    static boolean compilerAvailable() {
        return ToolProvider.getSystemJavaCompiler() != null;
    }

    static Compiled compileJavaTree(Path projectRoot) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("system Java compiler 없음 — JRE 로 테스트가 실행되고 있습니다.");
        }

        List<Path> sources;
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            sources = walk.filter(p -> p.toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (sources.isEmpty()) {
            throw new IllegalStateException("컴파일할 .java 소스가 없습니다: " + projectRoot);
        }

        Path classesDir;
        try {
            classesDir = Files.createDirectories(projectRoot.resolve("_test-classes"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String classpath = System.getProperty("java.class.path");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            fm.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classesDir.toFile()));

            List<String> options = List.of(
                    "-classpath", classpath,
                    "-processorpath", classpath,   // Lombok 은 processorpath 의 ServiceLoader 로 자동 등록
                    "-encoding", "UTF-8",
                    "-nowarn");

            Iterable<? extends JavaFileObject> units =
                    fm.getJavaFileObjectsFromPaths(sources.stream().map(Path::toAbsolutePath).toList());
            boolean ok = compiler.getTask(null, fm, diagnostics, options, null, units).call();

            List<String> errors = diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .map(GeneratedProjectCompiler::render)
                    .collect(Collectors.toList());
            List<String> warnings = diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.WARNING
                            || d.getKind() == Diagnostic.Kind.MANDATORY_WARNING)
                    .map(GeneratedProjectCompiler::render)
                    .collect(Collectors.toList());
            if (!ok && errors.isEmpty()) {
                errors.add("컴파일 실패했으나 ERROR 진단이 없습니다(annotation processing 문제 가능).");
            }

            URLClassLoader cl = new URLClassLoader(
                    new URL[]{classesDir.toUri().toURL()},
                    GeneratedProjectCompiler.class.getClassLoader());
            return new Compiled(classesDir, cl, errors, warnings);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String render(Diagnostic<? extends JavaFileObject> d) {
        JavaFileObject src = d.getSource();
        String where = src == null ? "" : (src.getName() + ":" + d.getLineNumber() + " ");
        return where + d.getMessage(null);
    }
}
