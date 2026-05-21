package com.krdevops.springai.service;

import com.krdevops.springai.tools.CodeTemplateTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeService {

    private final CodeTemplateTool codeTemplateTool;

    /**
     * 서버에서 직접 플레이스홀더를 치환하여 소스를 생성합니다.
     * LLM 개입 없이 100% 결정적으로 동일한 소스를 생성합니다.
     *
     * @param layer  vo, controller, service, serviceImpl, mapper, mapperXml, jspList, jspDetail, jspRegist, jspUpdt
     * @param values buildFullCrudPrompt()가 계산한 플레이스홀더 Map
     * @return 치환 완료된 소스 코드
     */
    public String generateSource(String layer, Map<String, String> values) {
        String template = codeTemplateTool.getCodeTemplate(layer);
        if (template.startsWith("지원하지 않는")) {
            return template;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        // 미치환 플레이스홀더 감지
        if (Pattern.compile("\\{\\{\\w+\\}\\}").matcher(template).find()) {
            String remaining = template.lines()
                .filter(l -> l.contains("{{"))
                .collect(Collectors.joining("\n"));
            log.warn("미치환 플레이스홀더 감지 [layer={}]:\n{}", layer, remaining);
            return template + "\n\n/* ⚠ 미치환 플레이스홀더 발견 — values Map을 확인하세요:\n" + remaining + "\n*/";
        }
        return template;
    }

    public String saveGeneratedCode(String filePath, String code) {
        try {
            Path path = Paths.get(filePath);
            Files.createDirectories(path.getParent());
            Files.writeString(path, code);
            return "파일 저장 완료: " + filePath + " (" + code.length() + " chars)";
        } catch (IOException e) {
            return "파일 저장 실패: " + e.getMessage();
        }
    }

    public String checkOutputDirectory(String baseDir) {
        Path path = Paths.get(baseDir);
        if (!Files.exists(path)) {
            return "디렉터리가 존재하지 않습니다. saveGeneratedCode 호출 시 자동 생성됩니다: " + baseDir;
        }
        try {
            StringBuilder sb = new StringBuilder("디렉터리: " + baseDir + "\n");
            Files.walk(path, 3)
                .filter(Files::isRegularFile)
                .forEach(f -> sb.append("  ").append(path.relativize(f)).append("\n"));
            return sb.toString();
        } catch (IOException e) {
            return "디렉터리 확인 실패: " + e.getMessage();
        }
    }
}
