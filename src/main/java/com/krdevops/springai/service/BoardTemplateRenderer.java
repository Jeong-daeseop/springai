package com.krdevops.springai.service;

import com.krdevops.springai.exception.CrudTemplateRenderException;
import com.krdevops.springai.model.board.BoardTemplateModel;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * 게시판(BBS) FreeMarker 템플릿 렌더러.
 * layerKey → {@code board/*.ftl} 템플릿 파일명 매핑 후 {@link BoardTemplateModel} 을
 * 바인딩하여 소스 문자열을 반환한다.
 */
@Service
public class BoardTemplateRenderer {

    /** layerKey → 템플릿 파일명 매핑 */
    private static final Map<String, String> LAYER_TEMPLATE_MAP;

    static {
        LAYER_TEMPLATE_MAP = new HashMap<>();
        LAYER_TEMPLATE_MAP.put("vo",              "board/vo.java.ftl");
        LAYER_TEMPLATE_MAP.put("searchVo",        "board/search-vo.java.ftl");
        LAYER_TEMPLATE_MAP.put("mapper",          "board/mapper.java.ftl");
        LAYER_TEMPLATE_MAP.put("mapperXml",       "board/mapper.xml.ftl");
        LAYER_TEMPLATE_MAP.put("service",         "board/service.java.ftl");
        LAYER_TEMPLATE_MAP.put("serviceImpl",     "board/service-impl.java.ftl");
        LAYER_TEMPLATE_MAP.put("controller",      "board/controller.java.ftl");
        LAYER_TEMPLATE_MAP.put("validHandler",    "board/validation-handler.java.ftl");
        LAYER_TEMPLATE_MAP.put("jspList",         "board/jsp-list.jsp.ftl");
        LAYER_TEMPLATE_MAP.put("jspDetail",       "board/jsp-detail.jsp.ftl");
        LAYER_TEMPLATE_MAP.put("jspRegist",       "board/jsp-regist.jsp.ftl");
        LAYER_TEMPLATE_MAP.put("jspUpdt",         "board/jsp-updt.jsp.ftl");
        LAYER_TEMPLATE_MAP.put("layoutHtml",      "crud/layout/default.html.ftl");
        LAYER_TEMPLATE_MAP.put("thymeleafList",   "board/thymeleaf-list.html.ftl");
        LAYER_TEMPLATE_MAP.put("thymeleafDetail", "board/thymeleaf-detail.html.ftl");
        LAYER_TEMPLATE_MAP.put("thymeleafRegist", "board/thymeleaf-regist.html.ftl");
        LAYER_TEMPLATE_MAP.put("thymeleafUpdt",   "board/thymeleaf-updt.html.ftl");
    }

    private final Configuration freemarkerConfig;

    public BoardTemplateRenderer(
            @Qualifier("boardFreemarkerConfiguration") Configuration freemarkerConfig) {
        this.freemarkerConfig = freemarkerConfig;
    }

    /**
     * 템플릿 파일명으로 직접 렌더링한다.
     *
     * @throws CrudTemplateRenderException 템플릿 로딩 또는 렌더링 실패 시
     */
    public String render(String templateName, BoardTemplateModel model) {
        try {
            Template template = freemarkerConfig.getTemplate(templateName);
            StringWriter writer = new StringWriter();
            template.process(toDataModel(model), writer);
            return writer.toString();
        } catch (IOException e) {
            throw new CrudTemplateRenderException(
                    "템플릿 로딩 실패: " + templateName, e);
        } catch (TemplateException e) {
            throw new CrudTemplateRenderException(
                    "템플릿 렌더링 실패: " + templateName, e);
        }
    }

    /**
     * layerKey로 템플릿을 선택하여 렌더링한다.
     *
     * @throws IllegalArgumentException    지원하지 않는 layerKey 입력 시
     * @throws CrudTemplateRenderException 템플릿 로딩 또는 렌더링 실패 시
     */
    public String renderByLayerKey(String layerKey, BoardTemplateModel model) {
        String templateName = LAYER_TEMPLATE_MAP.get(layerKey);
        if (templateName == null) {
            throw new IllegalArgumentException(
                    "지원하지 않는 layerKey: " + layerKey
                    + " (지원 목록: " + LAYER_TEMPLATE_MAP.keySet() + ")");
        }
        return render(templateName, model);
    }

    /**
     * BoardTemplateModel record → FreeMarker 데이터 모델(Map) 변환.
     */
    private Map<String, Object> toDataModel(BoardTemplateModel model) {
        Map<String, Object> data = new HashMap<>();
        data.put("packageName",         model.packageName());
        data.put("domain",              model.domain());
        data.put("domainLc",            model.domainLc());
        data.put("domainKr",            model.domainKr());
        data.put("tableName",           model.tableName());
        data.put("masterTableName",     model.masterTableName());
        data.put("useTableName",        model.useTableName());
        data.put("urlPrefix",           model.urlPrefix());
        data.put("date",                model.date());
        data.put("egovVersion",         model.egovVersion());
        data.put("jakartaValidation",   model.jakartaValidation());
        data.put("bbsId",               model.bbsId());
        data.put("nttId",               model.nttId());
        data.put("hasFile",             model.hasFile());
        data.put("atchFileId",          model.atchFileId());
        data.put("fileDetailTableName", model.fileDetailTableName());
        data.put("fields",              model.fields());
        data.put("listFields",          model.listFields());
        data.put("insertFields",        model.insertFields());
        data.put("formFields",          model.formFields());
        data.put("searchFields",        model.searchFields());
        data.put("noticeAtExists",      model.noticeAtExists());
        return data;
    }
}
