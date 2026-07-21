package com.krdevops.springai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectHealthService {

    private final JdbcTemplate jdbcTemplate;
    private final CodeValidatorService codeValidatorService;
    private final ProgramMetadataQueryService programMetadataQueryService;
    private final WebCaptureHealthService webCaptureHealthService;

    public String checkProjectHealth(String projectRootPath, String domain) {
        Path root = Paths.get(projectRootPath);
        if (!Files.exists(root)) {
            return "프로젝트 경로가 존재하지 않습니다: " + projectRootPath;
        }

        String domainCap = capitalize(domain);

        // 디렉터리를 1회만 순회하여 파일 인덱스 구성 (15회 walk → 1회)
        Map<String, Path> fileIndex;
        try {
            fileIndex = buildFileIndex(root);
        } catch (IOException e) {
            log.error("파일 인덱스 구성 실패: {}", e.getMessage());
            return "프로젝트 디렉터리 읽기 실패: " + e.getMessage();
        }

        // 1. 레이어별 파일 존재 확인
        Map<String, Boolean> fileStatus = checkFileExistence(fileIndex, domain, domainCap);

        // 2. 표준 준수 검사
        Map<String, String> validationStatus = checkStandardCompliance(fileIndex, domain, domainCap);

        // 3. 메뉴·권한 등록 확인
        boolean menuRegistered  = checkMenuRegistration(domain, domainCap);
        boolean authRegistered  = checkAuthRegistration(domain, domainCap);

        // 4. 테스트 코드 존재 확인
        boolean testExists = checkTestCode(fileIndex, domainCap);

        // 5. 완성도 계산 및 리포트 생성
        String report = buildReport(domain, domainCap, fileStatus, validationStatus,
                           menuRegistered, authRegistered, testExists);
        if (webCaptureHealthService == null) return report;
        var capture = webCaptureHealthService.check();
        return report + "\n[WEB_CAPTURE]\n  상태: " + capture.status()
                + "\n  extractor: " + (capture.extractorReady() ? "✅" : "❌")
                + "\n  artifact 경로: " + (capture.artifactPathWritable() ? "✅" : "❌")
                + "\n  schema: " + capture.schemaVersion() + "\n";
    }

    // -------------------------------------------------------------------------

    /** 디렉터리 1회 순회 → 파일명:절대경로 Map 구성 */
    private Map<String, Path> buildFileIndex(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                .filter(Files::isRegularFile)
                .collect(Collectors.toMap(
                    p -> p.getFileName().toString(),
                    p -> p,
                    (a, b) -> a   // 동명 파일 충돌 시 먼저 발견된 파일 사용
                ));
        }
    }

    private Map<String, Boolean> checkFileExistence(Map<String, Path> fileIndex,
                                                     String domain, String domainCap) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        String[][] targets = {
            {domainCap + "VO.java",                   "VO"},
            {domainCap + "Mapper.java",                "Mapper 인터페이스"},
            {domainCap + "Mapper.xml",                 "Mapper XML"},
            {domainCap + "Service.java",               "Service 인터페이스"},
            {"Egov" + domainCap + "ServiceImpl.java",  "ServiceImpl"},
            {"Egov" + domainCap + "Controller.java",   "Controller"},
        };
        for (String[] target : targets) {
            result.put(target[1] + " (" + target[0] + ")", fileIndex.containsKey(target[0]));
        }
        putViewStatus(result, fileIndex, "목록 화면", "Egov" + domainCap + "List");
        putViewStatus(result, fileIndex, "상세 화면", "Egov" + domainCap + "Detail");
        putViewStatus(result, fileIndex, "등록 화면", "Egov" + domainCap + "Regist");
        putViewStatus(result, fileIndex, "수정 화면", "Egov" + domainCap + "Updt");
        return result;
    }

    private void putViewStatus(Map<String, Boolean> result, Map<String, Path> fileIndex,
                               String label, String baseName) {
        String jspFile = baseName + ".jsp";
        String htmlFile = baseName + ".html";
        result.put(label + " (" + jspFile + " 또는 " + htmlFile + ")",
                   fileIndex.containsKey(jspFile) || fileIndex.containsKey(htmlFile));
    }

    private Map<String, String> checkStandardCompliance(Map<String, Path> fileIndex,
                                                         String domain, String domainCap) {
        Map<String, String> result = new LinkedHashMap<>();
        String[] javaTargets = {
            domainCap + "VO.java",
            domainCap + "Mapper.java",
            domainCap + "Service.java",
            "Egov" + domainCap + "ServiceImpl.java",
            "Egov" + domainCap + "Controller.java",
        };
        for (String fileName : javaTargets) {
            Path filePath = fileIndex.get(fileName);
            if (filePath != null) {
                try {
                    String validResult = codeValidatorService.validateFile(filePath.toString());
                    boolean hasFailure = validResult.contains("❌");
                    result.put(fileName, hasFailure ? "⚠️  " + extractFailures(validResult) : "✅ 통과");
                } catch (Exception e) {
                    result.put(fileName, "❓ 검사 실패");
                }
            }
        }
        return result;
    }

    private String extractFailures(String validResult) {
        StringBuilder sb = new StringBuilder();
        validResult.lines()
            .filter(l -> l.contains("❌"))
            .forEach(l -> sb.append(l.strip()).append(" / "));
        String result = sb.toString();
        return result.endsWith(" / ") ? result.substring(0, result.length() - 3) : result;
    }

    private boolean checkMenuRegistration(String domain, String domainCap) {
        try {
            String menuTable = programMetadataQueryService.firstExistingMenuTable();
            String programTable = programMetadataQueryService.firstExistingProgramTable();
            String like = "%" + domain.toLowerCase() + "%";
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + menuTable + " m " +
                "JOIN " + programTable + " p ON m.PROGRM_FILE_NM = p.PROGRM_FILE_NM " +
                "WHERE LOWER(p.URL) LIKE ?",
                Integer.class, like
            );
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("메뉴 등록 확인 실패: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkAuthRegistration(String domain, String domainCap) {
        try {
            String programTable = programMetadataQueryService.firstExistingProgramTable();
            String like = "%" + domain.toLowerCase() + "%";
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + programTable + " WHERE LOWER(URL) LIKE ?",
                Integer.class, like
            );
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("권한 등록 확인 실패: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkTestCode(Map<String, Path> fileIndex, String domainCap) {
        return fileIndex.containsKey(domainCap + "ControllerTest.java") ||
               fileIndex.containsKey(domainCap + "ServiceTest.java") ||
               fileIndex.containsKey(domainCap + "MapperTest.java");
    }

    private String buildReport(String domain, String domainCap,
                                Map<String, Boolean> fileStatus,
                                Map<String, String> validationStatus,
                                boolean menuRegistered, boolean authRegistered,
                                boolean testExists) {
        int totalFiles    = fileStatus.size();
        int existingFiles = (int) fileStatus.values().stream().filter(v -> v).count();

        // 완성도 점수 계산 (파일 10점 + 메뉴 2점 + 권한 2점 + 테스트 1점 = 15점 만점)
        int score = existingFiles;
        if (menuRegistered)  score += 2;
        if (authRegistered)  score += 2;
        if (testExists)      score += 1;
        int maxScore = totalFiles + 5;
        int percent  = (int) ((score / (double) maxScore) * 100);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== %s 도메인 완성도: %d%% (%d/%d) ===\n\n",
            domainCap, percent, score, maxScore));

        // 파일 존재 여부
        sb.append(String.format("[파일 존재] %d/%d\n", existingFiles, totalFiles));
        fileStatus.forEach((label, exists) ->
            sb.append(String.format("  %s %s\n", exists ? "✅" : "❌", label))
        );
        sb.append("\n");

        // 표준 준수 검사
        if (!validationStatus.isEmpty()) {
            sb.append("[표준 준수]\n");
            validationStatus.forEach((file, result) ->
                sb.append(String.format("  %s — %s\n", file, result))
            );
            sb.append("\n");
        }

        // 메뉴·권한 등록
        sb.append("[메뉴·권한 등록]\n");
        sb.append("  ").append(menuRegistered ? "✅" : "❌").append(" LETTNMENUINFO 메뉴 등록\n");
        sb.append("  ").append(authRegistered ? "✅" : "❌").append(" LETTNPROGRMLIST 프로그램 권한 등록\n\n");

        // 테스트 코드
        sb.append("[테스트 코드]\n");
        sb.append("  ").append(testExists ? "✅ 테스트 코드 존재" : "❌ 테스트 코드 없음").append("\n\n");

        // 다음 권장 작업
        List<String> nextTasks = buildNextTasks(fileStatus, menuRegistered, authRegistered, testExists, domainCap);
        if (!nextTasks.isEmpty()) {
            sb.append("[다음 권장 작업]\n");
            for (int i = 0; i < nextTasks.size(); i++) {
                sb.append(String.format("  %d. %s\n", i + 1, nextTasks.get(i)));
            }
        } else {
            sb.append("✅ 모든 항목이 완성되었습니다!\n");
        }

        return sb.toString();
    }

    private List<String> buildNextTasks(Map<String, Boolean> fileStatus,
                                         boolean menuRegistered, boolean authRegistered,
                                         boolean testExists, String domainCap) {
        List<String> tasks = new ArrayList<>();

        Map<String, String> fileToTemplate = new LinkedHashMap<>();
        fileToTemplate.put("VO",             "getCodeTemplate(\"vo\")");
        fileToTemplate.put("Mapper 인터페이스", "getCodeTemplate(\"mapper\")");
        fileToTemplate.put("Mapper XML",     "getCodeTemplate(\"mapperXml\")");
        fileToTemplate.put("Service 인터페이스", "getCodeTemplate(\"service\")");
        fileToTemplate.put("ServiceImpl",    "getCodeTemplate(\"serviceImpl\")");
        fileToTemplate.put("Controller",     "getCodeTemplate(\"controller\")");
        fileToTemplate.put("목록 화면",      "getCodeTemplate(\"jspList\") 또는 getCodeTemplate(\"thymeleafList\")");
        fileToTemplate.put("상세 화면",      "getCodeTemplate(\"jspDetail\") 또는 getCodeTemplate(\"thymeleafDetail\")");
        fileToTemplate.put("등록 화면",      "getCodeTemplate(\"jspRegist\") 또는 getCodeTemplate(\"thymeleafRegist\")");
        fileToTemplate.put("수정 화면",      "getCodeTemplate(\"jspUpdt\") 또는 getCodeTemplate(\"thymeleafUpdt\")");

        fileStatus.forEach((label, exists) -> {
            if (!exists) {
                String layerKey = label.contains("(") ? label.substring(0, label.indexOf("(")).trim() : label;
                String tool = fileToTemplate.getOrDefault(layerKey, "getCodeTemplate(...)");
                tasks.add(tool + " → " + label);
            }
        });

        if (!menuRegistered) tasks.add("generateMenuInsertSql(...) → 메뉴 등록");
        if (!authRegistered) tasks.add("generateAuthInsertSql(...) → 권한 등록");
        if (!testExists)     tasks.add("getTestTemplate(\"controllerTest\") → 테스트 코드 생성");

        return tasks;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
