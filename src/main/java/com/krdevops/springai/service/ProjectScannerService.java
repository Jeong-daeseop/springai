package com.krdevops.springai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class ProjectScannerService {

    /**
     * eGovFrame 프로젝트 구조 전체 스캔
     */
    public String scanProjectStructure(String projectRootPath) {
        Path root = Paths.get(projectRootPath);
        if (!Files.exists(root)) {
            return "프로젝트 경로가 존재하지 않습니다: " + projectRootPath;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== eGovFrame 프로젝트 구조 분석 ===\n");
        sb.append("경로: ").append(projectRootPath).append("\n\n");

        // 1. 패키지 루트 탐지
        String packageRoot = detectPackageRoot(root);
        sb.append("[패키지 루트]\n").append(packageRoot.isEmpty() ? "탐지 실패" : packageRoot).append("\n\n");

        // 2. 도메인 목록
        List<String> domains = detectDomains(root, packageRoot);
        sb.append("[도메인 목록] (").append(domains.size()).append("개)\n");
        domains.forEach(d -> sb.append("  - ").append(d).append("\n"));
        sb.append("\n");

        // 3. URL 패턴
        List<String> urlPatterns = detectUrlPatterns(root);
        sb.append("[URL 패턴] (").append(urlPatterns.size()).append("건 샘플)\n");
        urlPatterns.stream().limit(10).forEach(u -> sb.append("  ").append(u).append("\n"));
        sb.append("\n");

        // 4. 레이어별 디렉터리
        Map<String, String> layerDirs = detectLayerDirectories(root);
        sb.append("[레이어별 디렉터리]\n");
        layerDirs.forEach((layer, path) -> sb.append("  ").append(layer).append(": ").append(path).append("\n"));
        sb.append("\n");

        // 5. 주요 설정 파일
        List<String> configFiles = detectConfigFiles(root);
        sb.append("[주요 설정 파일]\n");
        configFiles.forEach(f -> sb.append("  ").append(f).append("\n"));
        sb.append("\n");

        // 6. 소스 통계
        sb.append(buildStatistics(root));

        log.info("프로젝트 구조 스캔 완료: {}", projectRootPath);
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // 패키지 루트 탐지 — egovframework.let.* 또는 최상위 공통 패키지 추출
    // -------------------------------------------------------------------------
    private String detectPackageRoot(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .map(p -> {
                    try {
                        return Files.readString(p);
                    } catch (IOException e) {
                        return "";
                    }
                })
                .filter(c -> c.startsWith("package "))
                .map(c -> c.lines().findFirst().orElse(""))
                .filter(line -> line.startsWith("package "))
                .map(line -> line.replace("package ", "").replace(";", "").trim())
                .filter(pkg -> !pkg.isEmpty())
                .collect(Collectors.groupingBy(
                    pkg -> pkg.contains(".") ? pkg.substring(0, pkg.lastIndexOf(".")) : pkg,
                    Collectors.counting()
                ))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
        } catch (IOException e) {
            return "";
        }
    }

    // -------------------------------------------------------------------------
    // 도메인 목록 — 레이어 디렉터리(web/service/impl) 하위 서브패키지명 수집
    // -------------------------------------------------------------------------
    private List<String> detectDomains(Path root, String packageRoot) {
        Set<String> domains = new LinkedHashSet<>();

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                .map(p -> {
                    try {
                        String content = Files.readString(p);
                        return content.lines()
                            .filter(l -> l.startsWith("package "))
                            .findFirst().orElse("");
                    } catch (IOException e) {
                        return "";
                    }
                })
                .filter(pkg -> pkg.contains(".web"))
                .map(pkg -> {
                    // package egovframework.let.emp.web; → emp
                    String clean = pkg.replace("package ", "").replace(";", "").trim();
                    String[] parts = clean.split("\\.");
                    for (int i = 0; i < parts.length - 1; i++) {
                        if (parts[i + 1].equals("web")) return parts[i];
                    }
                    return "";
                })
                .filter(d -> !d.isEmpty())
                .forEach(domains::add);
        } catch (IOException e) {
            log.warn("도메인 탐지 실패: {}", e.getMessage());
        }

        return new ArrayList<>(domains);
    }

    // -------------------------------------------------------------------------
    // URL 패턴 — @RequestMapping 값 수집
    // -------------------------------------------------------------------------
    private List<String> detectUrlPatterns(Path root) {
        List<String> patterns = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                .forEach(p -> {
                    try {
                        Files.readString(p).lines()
                            .filter(l -> l.contains("@RequestMapping"))
                            .map(l -> l.replaceAll(".*@RequestMapping\\(\"([^\"]+)\"\\).*", "$1"))
                            .filter(l -> l.startsWith("/"))
                            .forEach(patterns::add);
                    } catch (IOException e) {
                        // skip
                    }
                });
        } catch (IOException e) {
            log.warn("URL 패턴 탐지 실패: {}", e.getMessage());
        }

        return patterns.stream().distinct().sorted().collect(Collectors.toList());
    }

    /**
     * 프로젝트 루트 경로를 받아 레이어별 디렉터리 Map을 반환합니다. (OutputPathResolverService 전용)
     * key: web / service / impl / vo / mapper / mapperXml / jsp
     * value: 절대경로 문자열
     */
    public Map<String, String> getLayerDirectories(String projectRootPath) {
        Path root = Paths.get(projectRootPath);
        if (!Files.exists(root)) return Map.of();
        return detectLayerDirectories(root);
    }

    // -------------------------------------------------------------------------
    // 레이어별 디렉터리 경로 수집
    // -------------------------------------------------------------------------
    private Map<String, String> detectLayerDirectories(Path root) {
        Map<String, String> result = new LinkedHashMap<>();
        String[] targets = {"web", "service", "impl", "vo", "mapper", "dao"};

        try (Stream<Path> paths = Files.walk(root)) {
            Map<String, Path> found = new LinkedHashMap<>();
            paths.filter(Files::isDirectory)
                .forEach(dir -> {
                    String name = dir.getFileName().toString().toLowerCase();
                    for (String t : targets) {
                        if (name.equals(t) && !found.containsKey(t)) {
                            found.put(t, dir);
                        }
                    }
                });
            found.forEach((key, path) -> result.put(key, path.toString()));
        } catch (IOException e) {
            log.warn("레이어 디렉터리 탐지 실패: {}", e.getMessage());
        }

        // Mapper XML 위치 별도 탐지
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.getFileName().toString().endsWith("Mapper.xml"))
                .findFirst()
                .ifPresent(p -> result.put("mapperXml", p.getParent().toString()));
        } catch (IOException e) {
            log.warn("MapperXML 디렉터리 탐지 실패: {}", e.getMessage());
        }

        // JSP 위치 탐지
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.getFileName().toString().endsWith(".jsp"))
                .findFirst()
                .ifPresent(p -> result.put("jsp", p.getParent().toString()));
        } catch (IOException e) {
            log.warn("JSP 디렉터리 탐지 실패: {}", e.getMessage());
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // 주요 설정 파일 탐지
    // -------------------------------------------------------------------------
    private List<String> detectConfigFiles(Path root) {
        List<String> configPatterns = List.of(
            "context-*.xml", "dispatcher-servlet.xml", "web.xml",
            "application.properties", "application.yml", "application.yaml",
            "logback*.xml", "log4j*.xml", "egov-com-*.xml"
        );

        List<String> found = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                .forEach(p -> {
                    String name = p.getFileName().toString();
                    for (String pattern : configPatterns) {
                        String regex = pattern.replace(".", "\\.").replace("*", ".*");
                        if (name.matches(regex)) {
                            found.add(p.toString());
                            break;
                        }
                    }
                });
        } catch (IOException e) {
            log.warn("설정 파일 탐지 실패: {}", e.getMessage());
        }

        return found.stream().sorted().collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // 소스 통계
    // -------------------------------------------------------------------------
    private String buildStatistics(Path root) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("Controller.java", 0L);
        counts.put("ServiceImpl.java", 0L);
        counts.put("Mapper.java", 0L);
        counts.put("VO.java", 0L);
        counts.put("Mapper.xml", 0L);
        counts.put(".jsp", 0L);

        try (Stream<Path> paths = Files.walk(root)) {
            Map<String, Long> raw = paths
                .filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .collect(Collectors.groupingBy(
                    name -> {
                        for (String key : counts.keySet()) {
                            if (name.endsWith(key)) return key;
                        }
                        return "기타";
                    },
                    Collectors.counting()
                ));
            raw.forEach(counts::put);
        } catch (IOException e) {
            log.warn("통계 수집 실패: {}", e.getMessage());
        }

        StringBuilder sb = new StringBuilder("[소스 통계]\n");
        counts.entrySet().stream()
            .filter(e -> !e.getKey().equals("기타") && e.getValue() > 0)
            .forEach(e -> sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("개\n"));
        return sb.toString();
    }
}
