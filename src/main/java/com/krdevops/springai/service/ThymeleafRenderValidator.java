package com.krdevops.springai.service;

import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 생성 HTML을 실제 Thymeleaf 엔진으로 처리하는 선택형 품질 게이트. */
@Service
public class ThymeleafRenderValidator {

    private final SpringTemplateEngine templateEngine;

    public ThymeleafRenderValidator() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);
        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
    }

    public RenderReport validateDirectory(String directoryPath) {
        Path root = Path.of(directoryPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return new RenderReport(0, 0, List.of("디렉터리가 없습니다: " + root));
        }
        List<String> failures = new ArrayList<>();
        int[] counts = new int[2];
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".html"))
                    .forEach(path -> {
                        counts[0]++;
                        try {
                            String source = Files.readString(path, StandardCharsets.UTF_8);
                            templateEngine.process(source, fixtureContext());
                            counts[1]++;
                        } catch (Exception e) {
                            failures.add(root.relativize(path) + " — " + rootMessage(e));
                        }
                    });
        } catch (Exception e) {
            failures.add("Thymeleaf 파일 탐색 실패: " + e.getMessage());
        }
        return new RenderReport(counts[0], counts[1], failures);
    }

    private Context fixtureContext() {
        Map<String, Object> variables = new HashMap<>();
        Map<String, Object> emptyModel = new HashMap<>();
        variables.put("resultList", List.of());
        variables.put("result", emptyModel);
        variables.put("searchVO", emptyModel);
        variables.put("paginationInfo", emptyModel);
        variables.put("message", "");
        variables.put("error", false);
        return new Context(Locale.KOREA, variables);
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return current.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    public record RenderReport(int totalFiles, int renderedFiles, List<String> failures) {
        public RenderReport {
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        public boolean passed() {
            return failures.isEmpty() && totalFiles == renderedFiles;
        }
    }
}
