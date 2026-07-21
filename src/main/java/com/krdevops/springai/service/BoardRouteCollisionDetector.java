package com.krdevops.springai.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 생성 전에 다른 Controller가 동일 DB alias path를 사용하는지 보수적으로 검사한다.
 *
 * <p>Spring MVC는 클래스 레벨 {@code @RequestMapping("...")}(base path)과 메서드 레벨
 * {@code @GetMapping("...")} 등을 이어붙여 최종 URL을 만든다. 단순 문자열 검색은 이렇게
 * base+상대경로로 나뉜 흔한 작성 방식, 경로 생략(base만으로 매핑되는 {@code @GetMapping}),
 * {@code method = RequestMethod.X} 속성, 클래스 레벨 다중 경로({@code @RequestMapping({"/a","/b"})})를
 * 모두 놓치므로 이 클래스가 최대한 실제 Spring 매핑 해석에 가깝게 흉내 낸다(완전한 파서는 아니다).
 */
@Service
public class BoardRouteCollisionDetector {

    /** 괄호가 없는 {@code @GetMapping} 같은 무인자 애노테이션도 인식하도록 괄호 부분을 선택적으로 둔다. */
    private static final Pattern MAPPING_CALL = Pattern.compile(
            "@(GetMapping|PostMapping|PutMapping|DeleteMapping|RequestMapping)\\s*(?:\\(([^)]*)\\))?");
    private static final Pattern QUOTED_VALUE = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern REQUEST_METHOD = Pattern.compile("RequestMethod\\.(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)");
    private static final Pattern CLASS_KEYWORD = Pattern.compile("\\bclass\\s+\\w");

    /** 기존 호출자 하위 호환용 — HTTP 메서드를 구분하지 않고(=ANY) 검사한다. */
    public List<String> findConflicts(String outputPath, String aliasPath, String targetControllerFileName) {
        return findConflicts(outputPath, aliasPath, null, targetControllerFileName);
    }

    /**
     * @param httpMethod 검사할 HTTP 메서드("GET"/"POST" 등). null이면 메서드를 구분하지 않고
     *                    경로가 일치하는 모든 매핑을 충돌로 본다(기존 동작 유지).
     * @param excludeTarget 지금 생성 중인 대상 Controller의 절대경로 또는 파일명. 절대경로가
     *                    주어지면 그 파일만 정확히 제외하고, 같은 파일명을 쓰는 다른 패키지의
     *                    기존 Controller는 정상적으로 검사 대상에 포함한다(하위 호환: 파일명만
     *                    와도 동작하되, 이 경우 같은 파일명의 다른 경로도 함께 제외된다).
     */
    public List<String> findConflicts(String outputPath, String aliasPath, String httpMethod,
                                      String excludeTarget) {
        if (aliasPath == null || aliasPath.isBlank()) return List.of();
        Path javaRoot = Path.of(outputPath, "src/main/java");
        if (!Files.isDirectory(javaRoot)) return List.of();
        Path excludePath = excludeTarget != null && excludeTarget.contains("/")
                ? Path.of(excludeTarget).toAbsolutePath().normalize() : null;
        try (var paths = Files.walk(javaRoot)) {
            return paths.filter(path -> path.toString().endsWith("Controller.java"))
                    .filter(path -> !isExcluded(path, excludeTarget, excludePath))
                    .filter(path -> hasConflict(path, aliasPath, httpMethod))
                    .map(Path::toString)
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Controller URL alias 충돌 검사 실패: " + e.getMessage(), e);
        }
    }

    private boolean isExcluded(Path path, String excludeTarget, Path excludePath) {
        if (excludeTarget == null) return false;
        if (excludePath != null) {
            return path.toAbsolutePath().normalize().equals(excludePath);
        }
        return path.getFileName().toString().equals(excludeTarget);
    }

    private boolean hasConflict(Path path, String aliasPath, String httpMethod) {
        String content = read(path);
        List<String> classBases = extractClassBasePaths(content);
        int classKeywordIdx = firstClassKeywordIndex(content);
        for (MappingCall call : extractMappingCalls(content)) {
            boolean isClassLevel = classKeywordIdx >= 0 && call.startIndex() < classKeywordIdx;
            // 클래스 레벨 @RequestMapping 자체는 handler가 아니다 — 메서드 레벨 매핑과 결합할 때만
            // 실제 요청을 받는 최종 경로가 된다(extractClassBasePaths()가 base로 이미 반영한다).
            // 여기서 클래스 레벨 원문을 단독으로 alias와 비교하면, 그 경로를 처리하는 handler가
            // 하나도 없어도 충돌로 오탐할 수 있다.
            if (isClassLevel) continue;
            // 클래스 레벨 base(복수 가능)와 합쳐서만 최종 경로로 평가한다 — Spring은 메서드 경로가
            // "/"로 시작해도 클래스 base를 무시하는 절대경로로 취급하지 않으므로, base가 있는데
            // 메서드 rawValue만 단독으로 alias와 비교하면 오탐이 난다(예: base=/api, method=/users를
            // alias=/users와 매칭). base가 아예 없으면 join(null, rawValue)가 rawValue 그대로를
            // 비교하므로 별도 분기가 필요 없다. rawValue가 빈 문자열이면(경로 생략된 @GetMapping 등)
            // base 자체가 최종 경로가 된다.
            List<String> values = call.values().isEmpty() ? List.of("") : call.values();
            List<String> bases = classBases.isEmpty() ? java.util.Collections.singletonList(null) : classBases;
            for (String rawValue : values) {
                for (String base : bases) {
                    String joined = join(base, rawValue);
                    if (!joined.isBlank() && joined.equals(aliasPath) && methodMatches(call, httpMethod)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean methodMatches(MappingCall call, String requestedMethod) {
        if (requestedMethod == null) return true;
        return switch (call.annotation()) {
            case "GetMapping" -> "GET".equalsIgnoreCase(requestedMethod);
            case "PostMapping" -> "POST".equalsIgnoreCase(requestedMethod);
            case "PutMapping" -> "PUT".equalsIgnoreCase(requestedMethod);
            case "DeleteMapping" -> "DELETE".equalsIgnoreCase(requestedMethod);
            // method= 속성이 명시돼 있으면 그 집합에만 매칭, 없으면(메서드 미한정) 어떤 메서드와도 충돌 가능
            case "RequestMapping" -> call.explicitMethods().isEmpty()
                    || call.explicitMethods().stream().anyMatch(m -> m.equalsIgnoreCase(requestedMethod));
            default -> true;
        };
    }

    /** 첫 class 선언 이전 영역의 마지막 @RequestMapping이 갖는 경로값 전체(다중 경로 지원)를 클래스 base로 본다. */
    private List<String> extractClassBasePaths(String content) {
        int classKeywordIdx = firstClassKeywordIndex(content);
        if (classKeywordIdx < 0) return List.of();
        String header = content.substring(0, classKeywordIdx);
        Matcher matcher = MAPPING_CALL.matcher(header);
        List<String> bases = List.of();
        while (matcher.find()) {
            if (!"RequestMapping".equals(matcher.group(1))) continue;
            List<String> values = extractQuotedValues(nullToEmpty(matcher.group(2)));
            if (!values.isEmpty()) bases = values;
        }
        return bases;
    }

    private int firstClassKeywordIndex(String content) {
        Matcher matcher = CLASS_KEYWORD.matcher(content);
        return matcher.find() ? matcher.start() : -1;
    }

    private record MappingCall(String annotation, List<String> values, Set<String> explicitMethods, int startIndex) {}

    private List<MappingCall> extractMappingCalls(String content) {
        List<MappingCall> calls = new ArrayList<>();
        Matcher matcher = MAPPING_CALL.matcher(content);
        while (matcher.find()) {
            String annotation = matcher.group(1);
            String args = nullToEmpty(matcher.group(2));
            List<String> values = extractQuotedValues(args);
            Set<String> methods = "RequestMapping".equals(annotation) ? extractRequestMethods(args) : Set.of();
            calls.add(new MappingCall(annotation, values, methods, matcher.start()));
        }
        return calls;
    }

    private List<String> extractQuotedValues(String annotationArgs) {
        List<String> values = new ArrayList<>();
        Matcher matcher = QUOTED_VALUE.matcher(annotationArgs);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private Set<String> extractRequestMethods(String annotationArgs) {
        Set<String> methods = new LinkedHashSet<>();
        Matcher matcher = REQUEST_METHOD.matcher(annotationArgs);
        while (matcher.find()) {
            methods.add(matcher.group(1));
        }
        return methods;
    }

    /** Spring이 클래스 base + 메서드 상대경로를 합치는 방식과 동일하게 슬래시를 정규화한다. */
    private String join(String base, String rel) {
        String r = rel == null ? "" : rel;
        if (base == null || base.isBlank()) {
            return r.isBlank() ? "" : normalizeSlashes(r);
        }
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        if (r.isBlank()) return normalizeSlashes(b);
        String rr = r.startsWith("/") ? r : "/" + r;
        return normalizeSlashes(b + rr);
    }

    private String normalizeSlashes(String path) {
        return path.replaceAll("/{2,}", "/");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Controller 읽기 실패: " + path, e);
        }
    }
}
