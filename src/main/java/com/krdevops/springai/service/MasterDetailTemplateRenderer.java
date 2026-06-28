package com.krdevops.springai.service;

import com.krdevops.springai.exception.CrudTemplateRenderException;
import com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel;
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
 * FreeMarker renderer for master-detail CRUD templates.
 */
@Service
public class MasterDetailTemplateRenderer {

    private static final Map<String, String> LAYER_TEMPLATE_MAP;

    static {
        LAYER_TEMPLATE_MAP = new HashMap<>();
        LAYER_TEMPLATE_MAP.put("masterVo",          "masterdetail/vo.java.ftl");
        LAYER_TEMPLATE_MAP.put("detailVo",          "masterdetail/vo.java.ftl");
        LAYER_TEMPLATE_MAP.put("masterMapper",      "masterdetail/mapper.java.ftl");
        LAYER_TEMPLATE_MAP.put("detailMapper",      "masterdetail/detail-mapper.java.ftl");
        LAYER_TEMPLATE_MAP.put("masterMapperXml",   "masterdetail/master-mapper.xml.ftl");
        LAYER_TEMPLATE_MAP.put("detailMapperXml",   "masterdetail/detail-mapper.xml.ftl");
        LAYER_TEMPLATE_MAP.put("service",           "masterdetail/service.java.ftl");
        LAYER_TEMPLATE_MAP.put("serviceImpl",       "masterdetail/service-impl.java.ftl");
        LAYER_TEMPLATE_MAP.put("controller",        "masterdetail/controller.java.ftl");
        LAYER_TEMPLATE_MAP.put("validationHandler", "masterdetail/validation-handler.java.ftl");
        LAYER_TEMPLATE_MAP.put("jspList",           "masterdetail/jsp-list.jsp.ftl");
        LAYER_TEMPLATE_MAP.put("jspDetail",         "masterdetail/jsp-detail.jsp.ftl");
        LAYER_TEMPLATE_MAP.put("jspRegist",         "masterdetail/jsp-regist.jsp.ftl");
        LAYER_TEMPLATE_MAP.put("layoutHtml",        "masterdetail/layout/default.html.ftl");
        LAYER_TEMPLATE_MAP.put("thymeleafList",     "masterdetail/thymeleaf-list.html.ftl");
        LAYER_TEMPLATE_MAP.put("thymeleafDetail",   "masterdetail/thymeleaf-detail.html.ftl");
        LAYER_TEMPLATE_MAP.put("thymeleafRegist",   "masterdetail/thymeleaf-regist.html.ftl");
    }

    private final Configuration freemarkerConfig;

    public MasterDetailTemplateRenderer(
            @Qualifier("boardFreemarkerConfiguration") Configuration freemarkerConfig) {
        this.freemarkerConfig = freemarkerConfig;
    }

    public String renderByLayerKey(String layerKey, MasterDetailTemplateModel model) {
        String templateName = LAYER_TEMPLATE_MAP.get(layerKey);
        if (templateName == null) {
            throw new IllegalArgumentException(
                    "지원하지 않는 master-detail layerKey: " + layerKey
                    + " (지원 목록: " + LAYER_TEMPLATE_MAP.keySet() + ")");
        }
        try {
            Template template = freemarkerConfig.getTemplate(templateName);
            StringWriter writer = new StringWriter();
            template.process(toDataModel(layerKey, model), writer);
            return writer.toString();
        } catch (IOException e) {
            throw new CrudTemplateRenderException("템플릿 로딩 실패: " + templateName, e);
        } catch (TemplateException e) {
            throw new CrudTemplateRenderException("템플릿 렌더링 실패: " + templateName, e);
        }
    }

    private Map<String, Object> toDataModel(String layerKey, MasterDetailTemplateModel model) {
        Map<String, Object> data = new HashMap<>();
        boolean detailLayer = layerKey.startsWith("detail");
        var current = detailLayer ? model.detail() : model.master();
        data.put("current",           current);
        data.put("master",            model.master());
        data.put("detail",            model.detail());
        data.put("packageName",       model.packageName());
        data.put("domain",            current.domain());
        data.put("domainLc",          current.domainLc());
        data.put("domainKr",          current.domainKr());
        data.put("tableName",         current.tableName());
        data.put("urlPrefix",         model.urlPrefix());
        data.put("date",              model.date());
        data.put("egovVersion",       model.egovVersion());
        data.put("jakartaValidation", model.jakartaValidation());
        data.put("pk",                current.pk());
        data.put("fields",            current.fields());
        data.put("listFields",        current.listFields());
        data.put("nonPkFields",       current.nonPkFields());
        data.put("fkColumn",          model.fkColumn());
        data.put("fkField",           model.fkField());
        return data;
    }
}
