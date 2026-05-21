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
            eGovFrame 소스를 서버에서 직접 생성합니다. (LLM 치환 불필요 — 100% 일관성 보장)

            [동작 방식]
            getCodeTemplate(layer)로 템플릿을 가져와 서버에서 직접 String.replace()로 플레이스홀더를 치환합니다.
            LLM이 소스를 생성하거나 수정하지 않으므로 동일 입력 → 항상 동일 소스가 보장됩니다.

            [layer] 생성할 레이어:
              vo, controller, service, serviceImpl, mapper, mapperXml,
              jspList, jspDetail, jspRegist, jspUpdt

            [valuesJson] buildFullCrudPrompt()의 [플레이스홀더 치환 규칙] 섹션을 JSON으로 전달:
              {
                "PACKAGE":        "egovframework.let.emp",
                "DOMAIN":         "Employer",
                "DOMAIN_LC":      "employer",
                "DOMAIN_KR":      "직원",
                "TABLE_NAME":     "COMTNEMPLYRINFO",
                "PK_FIELD":       "emplyrId",
                "PK_COLUMN":      "EMPLYR_ID",
                "PK_TYPE":        "String",
                "URL_PREFIX":     "/emp/employer",
                "DATE":           "2026-05-20",
                "VO_FIELDS":      "    private String emplyrId;\\n    private String userNm;",
                "MAPPER_COLUMNS": "EMPLYR_ID, USER_NM",
                "INSERT_COLUMNS": "EMPLYR_ID, USER_NM",
                "INSERT_VALUES":  "#{emplyrId}, #{userNm}",
                "UPDATE_SET":     "USER_NM = #{userNm}",
                "RESULT_MAP_FIELDS": "...",
                "JSP_LIST_TH":    "...",
                "JSP_LIST_TD":    "...",
                "JSP_DETAIL_ROWS": "...",
                "JSP_FORM_INPUTS": "..."
              }

            [사용 순서]
            1. buildFullCrudPrompt()로 플레이스홀더 값을 확인합니다.
            2. generateSource(layer, valuesJson)로 소스를 생성합니다.
            3. saveGeneratedCode(filePath, code)로 파일을 저장합니다.
            4. 모든 레이어 생성 후 validateGeneratedCodeDirectory()로 검증합니다.

            getCodeTemplate() + LLM 치환 방식 대신 이 Tool을 우선 사용하세요.
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
