package com.krdevops.springai.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * eGovFrame 5.x CRUD FreeMarker 템플릿(.ftl) 원문 제공 Tool.
 * 렌더링 전 .ftl 파일 내용을 반환하여 디버깅·확인 용도로 사용합니다.
 * 실제 소스 생성은 buildFullCrudPrompt(llmProvider="auto") 를 사용하세요.
 */
@Component
public class CodeTemplateTool {

    private static final String TEMPLATE_DIR = "templates/crud/";

    /** layerKey(소문자) → .ftl 파일명 매핑 */
    private static final Map<String, String> LAYER_TO_FTL = Map.ofEntries(
        Map.entry("vo",               "vo.java.ftl"),
        Map.entry("controller",       "controller.java.ftl"),
        Map.entry("service",          "service.java.ftl"),
        Map.entry("serviceimpl",      "service-impl.java.ftl"),
        Map.entry("mapper",           "mapper.java.ftl"),
        Map.entry("mapperxml",        "mapper.xml.ftl"),
        Map.entry("jsplist",          "jsp-list.jsp.ftl"),
        Map.entry("jspdetail",        "jsp-detail.jsp.ftl"),
        Map.entry("jspregist",        "jsp-regist.jsp.ftl"),
        Map.entry("jspupdt",          "jsp-updt.jsp.ftl"),
        Map.entry("controlleradvice", "controller-advice.java.ftl")
    );

    @Tool(description = """
            eGovFrame 5.x CRUD FreeMarker 템플릿(.ftl) 원문을 반환합니다.
            디버깅·확인 용도로 사용하며, 실제 소스 생성은 buildFullCrudPrompt(llmProvider="auto")를 사용하세요.
            layer 파라미터:
              vo, controller, service, serviceimpl, mapper, mapperxml,
              jsplist, jspdetail, jspregist, jspupdt, controlleradvice
            """)
    public String getCodeTemplate(String layer) {
        String ftlFile = LAYER_TO_FTL.get(layer.toLowerCase());
        if (ftlFile == null) {
            return "지원하지 않는 레이어입니다. 사용 가능: " + String.join(", ", LAYER_TO_FTL.keySet());
        }
        try {
            return new ClassPathResource(TEMPLATE_DIR + ftlFile)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "템플릿 파일을 읽을 수 없습니다: " + ftlFile + " (" + e.getMessage() + ")";
        }
    }
}
