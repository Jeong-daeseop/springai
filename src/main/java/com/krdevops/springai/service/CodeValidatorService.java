package com.krdevops.springai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class CodeValidatorService {

    /**
     * 단일 파일 검증
     */
    public String validateFile(String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return "파일이 존재하지 않습니다: " + filePath;
        }

        String content;
        try {
            content = Files.readString(path);
        } catch (IOException e) {
            return "파일 읽기 실패: " + e.getMessage();
        }

        String fileName = path.getFileName().toString();
        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        runChecks(fileName, content, passed, failed);

        return formatResult(fileName, passed, failed);
    }

    /**
     * 디렉터리 내 .java / .xml / .jsp 파일 일괄 검증
     */
    public String validateDirectory(String directoryPath) {
        Path dir = Paths.get(directoryPath);
        if (!Files.exists(dir)) {
            return "디렉터리가 존재하지 않습니다: " + directoryPath;
        }

        StringBuilder sb = new StringBuilder();
        int totalPass = 0, totalFail = 0, fileCount = 0;

        try (Stream<Path> paths = Files.walk(dir)) {
            List<Path> targets = paths
                .filter(p -> {
                    String name = p.getFileName().toString();
                    return name.endsWith(".java") || name.endsWith(".xml") || name.endsWith(".jsp");
                })
                .toList();

            for (Path path : targets) {
                try {
                    String content = Files.readString(path);
                    String fileName = path.getFileName().toString();
                    List<String> passed = new ArrayList<>();
                    List<String> failed = new ArrayList<>();
                    runChecks(fileName, content, passed, failed);

                    totalPass += passed.size();
                    totalFail += failed.size();
                    fileCount++;

                    if (!failed.isEmpty()) {
                        sb.append(formatResult(path.toString(), passed, failed)).append("\n\n");
                    }
                } catch (IOException e) {
                    sb.append("읽기 실패: ").append(path).append("\n");
                }
            }
        } catch (IOException e) {
            return "디렉터리 스캔 실패: " + e.getMessage();
        }

        String summary = String.format(
            "=== 검증 완료 ===\n파일: %d개 | 통과 항목: %d개 | 미준수 항목: %d개\n",
            fileCount, totalPass, totalFail
        );

        return totalFail == 0
            ? summary + "모든 파일이 eGovFrame 5.x 표준을 준수합니다."
            : summary + "\n[미준수 파일 상세]\n" + sb;
    }

    // -------------------------------------------------------------------------

    private void runChecks(String fileName, String content,
                           List<String> passed, List<String> failed) {
        if (fileName.endsWith("Controller.java")) {
            checkController(content, passed, failed);
        } else if (fileName.endsWith("ServiceImpl.java")) {
            checkServiceImpl(content, passed, failed);
        } else if (fileName.endsWith("Service.java") && !fileName.endsWith("ServiceImpl.java")) {
            checkServiceInterface(content, passed, failed);
        } else if (fileName.endsWith("Mapper.java")) {
            checkMapper(content, passed, failed);
        } else if (fileName.endsWith("VO.java")) {
            checkVO(content, passed, failed);
        } else if (fileName.endsWith("Mapper.xml")) {
            checkMapperXml(content, passed, failed);
        } else if (fileName.endsWith(".jsp")) {
            checkJsp(content, passed, failed);
            if (fileName.endsWith("List.jsp")) {
                check(content, "pageIndex", "페이지 인덱스 처리", passed, failed);
            }
        } else {
            passed.add("검증 대상 아님 (규칙 없음)");
        }
    }

    private void checkController(String content, List<String> passed, List<String> failed) {
        check(content, "@Controller",               "@Controller 선언",               passed, failed);
        check(content, "@RequestMapping",           "@RequestMapping 사용",            passed, failed);
        check(content, "@RequiredArgsConstructor",  "@RequiredArgsConstructor 사용",   passed, failed);
        check(content, "EgovPropertyService",       "EgovPropertyService 주입",        passed, failed);
        check(content, "PaginationInfo",            "PaginationInfo 페이징 처리",       passed, failed);
        check(content, "ModelMap",                  "ModelMap 사용",                   passed, failed);
        check(content, "forward:",                  "forward: 리다이렉트 패턴",         passed, failed);
        // 구조 검사
        checkPattern(content, "@Controller[\\s\\S]*?public class Egov\\w+Controller",
                     "Egov{Domain}Controller 명명 규칙",                               passed, failed);
        checkNoUnresolved(content, passed, failed);
    }

    private void checkServiceImpl(String content, List<String> passed, List<String> failed) {
        check(content, "@Service",                  "@Service 선언",                   passed, failed);
        check(content, "@RequiredArgsConstructor",  "@RequiredArgsConstructor 사용",   passed, failed);
        check(content, "@Transactional",            "@Transactional 적용 (CUD)",       passed, failed);
        check(content, "@Override",                 "@Override 메서드 재정의",          passed, failed);
        // 구조 검사
        checkPattern(content, "public class \\w+ServiceImpl extends EgovAbstractServiceImpl",
                     "EgovAbstractServiceImpl 상속 구조",                              passed, failed);
        checkPattern(content, "implements \\w+Service",
                     "{Domain}Service 인터페이스 구현",                               passed, failed);
        checkNoUnresolved(content, passed, failed);
    }

    private void checkServiceInterface(String content, List<String> passed, List<String> failed) {
        check(content, "interface",                 "interface 선언",                  passed, failed);
        check(content, "List<",                     "목록 조회 메서드 (List<>)",        passed, failed);
        check(content, "TotCnt",                    "전체 건수 조회 메서드 (TotCnt)",   passed, failed);
        check(content, "throws Exception",          "throws Exception 선언",           passed, failed);
        // 구조 검사
        checkPattern(content, "interface \\w+Service",
                     "{Domain}Service 명명 규칙",                                      passed, failed);
        checkNoUnresolved(content, passed, failed);
    }

    private void checkMapper(String content, List<String> passed, List<String> failed) {
        check(content, "@Mapper",                   "@Mapper 선언",                    passed, failed);
        check(content, "List<",                     "목록 조회 메서드 (List<>)",        passed, failed);
        check(content, "TotCnt",                    "전체 건수 조회 메서드 (TotCnt)",   passed, failed);
        // 구조 검사
        checkPattern(content, "interface \\w+Mapper\\s*\\{",
                     "MyBatis Mapper 인터페이스 구조",                                  passed, failed);
        checkNoUnresolved(content, passed, failed);
    }

    private void checkVO(String content, List<String> passed, List<String> failed) {
        check(content, "@Getter",                   "@Getter 선언",                    passed, failed);
        check(content, "@Setter",                   "@Setter 선언",                    passed, failed);
        check(content, "PaginationInfo",            "PaginationInfo 페이징 필드",       passed, failed);
        check(content, "searchKeyword",             "searchKeyword 검색 필드",          passed, failed);
        // 구조 검사
        checkPattern(content, "private (int|Integer) pageIndex",
                     "pageIndex int/Integer 타입",                                     passed, failed);
        checkPattern(content, "private String searchCondition",
                     "searchCondition String 타입",                                    passed, failed);
        checkPattern(content, "public class \\w+VO",
                     "{Domain}VO 명명 규칙",                                           passed, failed);
        checkNoUnresolved(content, passed, failed);
    }

    private void checkMapperXml(String content, List<String> passed, List<String> failed) {
        check(content, "<!DOCTYPE mapper",          "MyBatis Mapper DTD 선언",         passed, failed);
        check(content, "<mapper namespace=",        "namespace 설정",                  passed, failed);
        check(content, "<resultMap",                "resultMap 정의",                  passed, failed);
        check(content, "searchCondition",           "검색 조건 sql 블록",               passed, failed);
        check(content, "<include refid=",           "<include refid> 재사용",          passed, failed);
        check(content, "paginationInfo.firstRecordIndex", "페이징 LIMIT 처리",         passed, failed);
        // 구조 검사 — 5개 필수 쿼리 ID (도메인명 포함 패턴: selectXxxList, insertXxx 등)
        checkPattern(content, "id=\"select\\w+List\"",   "selectXxxList 쿼리 ID 존재",   passed, failed);
        checkPattern(content, "id=\"select\\w+TotCnt\"", "selectXxxTotCnt 쿼리 ID 존재", passed, failed);
        checkPattern(content, "id=\"insert\\w+\"",       "insertXxx 쿼리 ID 존재",       passed, failed);
        checkPattern(content, "id=\"update\\w+\"",       "updateXxx 쿼리 ID 존재",       passed, failed);
        checkPattern(content, "id=\"delete\\w+\"",       "deleteXxx 쿼리 ID 존재",       passed, failed);
        checkNoUnresolved(content, passed, failed);
    }

    private void checkJsp(String content, List<String> passed, List<String> failed) {
        check(content, "contentType=\"text/html; charset=UTF-8\"", "UTF-8 인코딩 선언", passed, failed);
        check(content, "taglib prefix=\"c\"",       "JSTL core 태그 선언",             passed, failed);
        check(content, "<c:url",                    "<c:url> URL 처리",                passed, failed);
        checkNoUnresolved(content, passed, failed);
    }

    private void check(String content, String keyword, String label,
                       List<String> passed, List<String> failed) {
        if (content.contains(keyword)) {
            passed.add("✅ " + label);
        } else {
            failed.add("❌ " + label);
        }
    }

    private void checkPattern(String content, String regex, String label,
                               List<String> passed, List<String> failed) {
        if (Pattern.compile(regex, Pattern.DOTALL).matcher(content).find()) {
            passed.add("✅ " + label);
        } else {
            failed.add("❌ " + label);
        }
    }

    /** 미치환 플레이스홀더 {{XXX}} 탐지 */
    private void checkNoUnresolved(String content, List<String> passed, List<String> failed) {
        if (Pattern.compile("\\{\\{\\w+\\}\\}").matcher(content).find()) {
            failed.add("❌ 미치환 플레이스홀더 존재 ({{...}} 패턴 발견)");
        } else {
            passed.add("✅ 미치환 플레이스홀더 없음");
        }
    }

    private String formatResult(String fileName, List<String> passed, List<String> failed) {
        StringBuilder sb = new StringBuilder();
        sb.append("[ ").append(fileName).append(" ]\n");
        sb.append("통과: ").append(passed.size()).append("개 / 미준수: ").append(failed.size()).append("개\n");
        failed.forEach(f -> sb.append("  ").append(f).append("\n"));
        passed.forEach(p -> sb.append("  ").append(p).append("\n"));
        return sb.toString().stripTrailing();
    }
}
