package com.krdevops.springai.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.service.CodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class CodeSaverTool {

    private final CodeService codeService;
    private final ObjectMapper objectMapper;

    @Tool(description = """
            Claude가 생성한 eGovFrame 소스 코드를 파일로 저장합니다.
            filePath: 저장할 절대경로 (예: /Users/jeongdaeseob/Desktop/egov-gen/EmployerController.java)
            code: 저장할 소스 코드 전체 내용
            디렉터리가 없으면 자동 생성합니다.
            eGovFrame 5.x CRUD 생성 시 VO, Mapper, Service, ServiceImpl, Controller, MapperXML 저장에 사용합니다.
            """)
    public String saveGeneratedCode(String filePath, String code) {
        return codeService.saveGeneratedCode(filePath, code);
    }

    @Tool(description = """
            eGovFrame CRUD 소스를 저장할 기본 경로를 확인합니다.
            baseDir: 확인할 디렉터리 경로
            해당 경로의 존재 여부와 기존 파일 목록을 반환합니다.
            """)
    public String checkOutputDirectory(String baseDir) {
        return codeService.checkOutputDirectory(baseDir);
    }

    @Tool(description = """
            [DEPRECATED] 이 Tool은 더 이상 사용되지 않습니다.

            text-block {{플레이스홀더}} 방식은 FreeMarker 템플릿으로 전환되었으며,
            generateSource()를 호출하면 deprecation 안내 메시지만 반환됩니다.

            eGovFrame CRUD 소스 생성은 buildFullCrudPrompt(llmProvider="auto")를 사용하세요.
            auto 모드는 FreeMarker 템플릿으로 viewType별 파일을 한 번에 생성·저장합니다 (JSP: 11개, Thymeleaf: 16개).
            """)
    public String generateSource(String layer, String valuesJson) {
        Map<String, String> values;
        try {
            values = objectMapper.readValue(valuesJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return "valuesJson 파싱 실패: " + e.getMessage()
                + "\n올바른 JSON 형식으로 전달하세요. 예: {\"PACKAGE\":\"...\",\"DOMAIN\":\"...\"}";
        }
        return codeService.generateSource(layer, values);
    }
}
