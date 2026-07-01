package kr.go.egov.mcp.tool;

import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * SpringAI MCP CrudPromptBuilderTool
 *
 * FreeMarker(.ftl) 템플릿 + 변수 JSON → Thymeleaf HTML 파일 생성
 *
 * egov-boot-web 적용 경로:
 *   src/main/java/kr/go/egov/mcp/tool/CrudPromptBuilderTool.java
 *
 * build.gradle 의존성:
 *   implementation 'org.springframework.ai:spring-ai-mcp-server-webmvc-spring-boot-starter'
 *   implementation 'org.springframework.boot:spring-boot-starter-freemarker'
 *   compileOnly 'org.projectlombok:lombok'
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrudPromptBuilderTool {

    private final Configuration freemarkerConfig;

    /* ================================================================
       ① 게시판 목록 (board/thymeleaf-list.html.ftl)
       ================================================================ */
    @Tool(description = "FTC 스타일 게시판 목록 Thymeleaf 템플릿을 생성합니다. "
                      + "classLabel(한글명), classVar(VO변수명), mappingUrl(URL경로), "
                      + "pkName(PK필드명), listFields(목록컬럼JSON)을 입력받습니다.")
    public String generateBoardList(
            @ToolParam(description = "화면 한글명. 예: 공지·공고") String classLabel,
            @ToolParam(description = "VO 변수명 (camelCase). 예: board") String classVar,
            @ToolParam(description = "URL 접두어. 예: /board") String mappingUrl,
            @ToolParam(description = "PK 필드명. 예: boardNo") String pkName,
            @ToolParam(description = "목록 컬럼 JSON 배열. 예: [{\"name\":\"nttSj\",\"label\":\"제목\"}]")
                String listFieldsJson,
            @ToolParam(description = "검색 필드 JSON 배열 (선택). 예: [{\"name\":\"nttSj\",\"label\":\"제목\"}]")
                String searchFieldsJson,
            @ToolParam(description = "첨부파일 여부 Y/N (선택, 기본 N)") String hasFileYn
    ) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("classLabel",    classLabel);
        vars.put("classVar",      classVar);
        vars.put("mappingUrl",    mappingUrl);
        vars.put("pkName",        pkName);
        vars.put("listFields",    parseJsonArray(listFieldsJson));
        vars.put("searchFields",  parseJsonArray(searchFieldsJson != null ? searchFieldsJson : "[]"));
        vars.put("hasFileYn",     hasFileYn != null ? hasFileYn : "N");
        return processTemplate("board/thymeleaf-list.html.ftl", vars);
    }

    /* ================================================================
       ② 게시판 상세 (board/thymeleaf-detail.html.ftl)
       ================================================================ */
    @Tool(description = "FTC 스타일 게시판 상세 Thymeleaf 템플릿을 생성합니다.")
    public String generateBoardDetail(
            @ToolParam(description = "화면 한글명. 예: 공지·공고") String classLabel,
            @ToolParam(description = "VO 변수명 (camelCase). 예: board") String classVar,
            @ToolParam(description = "URL 접두어. 예: /board") String mappingUrl,
            @ToolParam(description = "PK 필드명. 예: boardNo") String pkName,
            @ToolParam(description = "상세 필드 JSON 배열") String detailFieldsJson,
            @ToolParam(description = "첨부파일 여부 Y/N") String hasFileYn,
            @ToolParam(description = "본문(내용) 여부 Y/N") String hasContentYn
    ) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("classLabel",    classLabel);
        vars.put("classVar",      classVar);
        vars.put("mappingUrl",    mappingUrl);
        vars.put("pkName",        pkName);
        vars.put("detailFields",  parseJsonArray(detailFieldsJson));
        vars.put("hasFileYn",     hasFileYn != null ? hasFileYn : "N");
        vars.put("hasContentYn",  hasContentYn != null ? hasContentYn : "Y");
        return processTemplate("board/thymeleaf-detail.html.ftl", vars);
    }

    /* ================================================================
       ③ 게시판 등록/수정 (board/thymeleaf-regist.html.ftl)
       ================================================================ */
    @Tool(description = "FTC 스타일 게시판 등록/수정 Thymeleaf 템플릿을 생성합니다.")
    public String generateBoardRegist(
            @ToolParam(description = "화면 한글명. 예: 공지·공고") String classLabel,
            @ToolParam(description = "VO 변수명 (camelCase). 예: board") String classVar,
            @ToolParam(description = "URL 접두어. 예: /board") String mappingUrl,
            @ToolParam(description = "PK 필드명. 예: boardNo") String pkName,
            @ToolParam(description = "폼 필드 JSON 배열") String formFieldsJson,
            @ToolParam(description = "첨부파일 여부 Y/N") String hasFileYn,
            @ToolParam(description = "수정 모드 여부 true/false") boolean isUpdate
    ) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("classLabel",  classLabel);
        vars.put("classVar",    classVar);
        vars.put("mappingUrl",  mappingUrl);
        vars.put("pkName",      pkName);
        vars.put("formFields",  parseJsonArray(formFieldsJson));
        vars.put("hasFileYn",   hasFileYn != null ? hasFileYn : "N");
        vars.put("isUpdate",    isUpdate);
        return processTemplate("board/thymeleaf-regist.html.ftl", vars);
    }

    /* ================================================================
       ④ 마스터-디테일 목록 (masterdetail/thymeleaf-list.html.ftl)
       ================================================================ */
    @Tool(description = "FTC 스타일 마스터-디테일 목록 Thymeleaf 템플릿을 생성합니다.")
    public String generateMasterList(
            @ToolParam(description = "마스터 화면 한글명") String mClassLabel,
            @ToolParam(description = "마스터 VO 변수명") String mClassVar,
            @ToolParam(description = "마스터 URL 접두어. 예: /master") String mMappingUrl,
            @ToolParam(description = "마스터 PK 필드명") String mPkName,
            @ToolParam(description = "마스터 목록 필드 JSON 배열") String mListFieldsJson,
            @ToolParam(description = "마스터 검색 필드 JSON 배열 (선택)") String mSearchFieldsJson
    ) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("mClassLabel",   mClassLabel);
        vars.put("mClassVar",     mClassVar);
        vars.put("mMappingUrl",   mMappingUrl);
        vars.put("mPkName",       mPkName);
        vars.put("mListFields",   parseJsonArray(mListFieldsJson));
        vars.put("mSearchFields", parseJsonArray(mSearchFieldsJson != null ? mSearchFieldsJson : "[]"));
        return processTemplate("masterdetail/thymeleaf-list.html.ftl", vars);
    }

    /* ================================================================
       ⑤ 마스터-디테일 상세 (masterdetail/thymeleaf-detail.html.ftl)
       ================================================================ */
    @Tool(description = "FTC 스타일 마스터-디테일 상세(1:N) Thymeleaf 템플릿을 생성합니다.")
    public String generateMasterDetail(
            @ToolParam(description = "마스터 화면 한글명") String mClassLabel,
            @ToolParam(description = "마스터 VO 변수명") String mClassVar,
            @ToolParam(description = "마스터 URL 접두어") String mMappingUrl,
            @ToolParam(description = "마스터 PK 필드명") String mPkName,
            @ToolParam(description = "마스터 상세 필드 JSON 배열") String mDetailFieldsJson,
            @ToolParam(description = "디테일 화면 한글명") String dClassLabel,
            @ToolParam(description = "디테일 VO 변수명") String dClassVar,
            @ToolParam(description = "디테일 URL 접두어") String dMappingUrl,
            @ToolParam(description = "디테일 PK 필드명") String dPkName,
            @ToolParam(description = "디테일 목록 필드 JSON 배열") String dListFieldsJson
    ) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("mClassLabel",   mClassLabel);
        vars.put("mClassVar",     mClassVar);
        vars.put("mMappingUrl",   mMappingUrl);
        vars.put("mPkName",       mPkName);
        vars.put("mDetailFields", parseJsonArray(mDetailFieldsJson));
        vars.put("dClassLabel",   dClassLabel);
        vars.put("dClassVar",     dClassVar);
        vars.put("dMappingUrl",   dMappingUrl);
        vars.put("dPkName",       dPkName);
        vars.put("dListFields",   parseJsonArray(dListFieldsJson));
        return processTemplate("masterdetail/thymeleaf-detail.html.ftl", vars);
    }

    /* ================================================================
       ⑥ 마스터 등록/수정 (masterdetail/thymeleaf-regist.html.ftl)
       ================================================================ */
    @Tool(description = "FTC 스타일 마스터 등록/수정 Thymeleaf 템플릿을 생성합니다.")
    public String generateMasterRegist(
            @ToolParam(description = "마스터 화면 한글명") String mClassLabel,
            @ToolParam(description = "마스터 VO 변수명") String mClassVar,
            @ToolParam(description = "마스터 URL 접두어") String mMappingUrl,
            @ToolParam(description = "마스터 PK 필드명") String mPkName,
            @ToolParam(description = "폼 필드 JSON 배열") String mFormFieldsJson,
            @ToolParam(description = "수정 모드 여부 true/false") boolean isUpdate
    ) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("mClassLabel",  mClassLabel);
        vars.put("mClassVar",    mClassVar);
        vars.put("mMappingUrl",  mMappingUrl);
        vars.put("mPkName",      mPkName);
        vars.put("mFormFields",  parseJsonArray(mFormFieldsJson));
        vars.put("isUpdate",     isUpdate);
        return processTemplate("masterdetail/thymeleaf-regist.html.ftl", vars);
    }

    /* ================================================================
       공통: FreeMarker 템플릿 처리
       ================================================================ */
    private String processTemplate(String templateName, Map<String, Object> vars) {
        try {
            Template template = freemarkerConfig.getTemplate(templateName);
            StringWriter writer = new StringWriter();
            template.process(vars, writer);
            String result = writer.toString();
            log.info("[CrudPromptBuilderTool] 템플릿 생성 완료: {}", templateName);
            return result;
        } catch (Exception e) {
            log.error("[CrudPromptBuilderTool] 템플릿 처리 오류: {} - {}", templateName, e.getMessage());
            throw new RuntimeException("템플릿 생성 실패: " + templateName, e);
        }
    }

    /* ================================================================
       공통: JSON 배열 문자열 → List<Map> 파싱 (간이 파서)
       실제 운영에서는 ObjectMapper 또는 Gson 사용 권장
       ================================================================ */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJsonArray(String json) {
        try {
            // Spring Boot 환경에서는 ObjectMapper 주입 후 사용
            // return objectMapper.readValue(json, new TypeReference<List<Map<String,Object>>>(){});
            // 여기서는 간이 처리 — 실제 구현 시 교체 필요
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json,
                mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            log.warn("[CrudPromptBuilderTool] JSON 파싱 실패, 빈 목록 반환: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
