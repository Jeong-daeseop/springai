package com.krdevops.springai.service;

import com.krdevops.springai.exception.CrudTemplateRenderException;
import com.krdevops.springai.model.crud.CrudLayoutMode;
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
        LAYER_TEMPLATE_MAP.put("jspUpdt",           "masterdetail/jsp-updt.jsp.ftl");
        LAYER_TEMPLATE_MAP.put("layoutHtml",        "masterdetail/layout/default.html.ftl");
        LAYER_TEMPLATE_MAP.put("layoutGnbHtml",     "masterdetail/layout/gnb.html.ftl");
        LAYER_TEMPLATE_MAP.put("layoutLnbHtml",     "masterdetail/layout/lnb.html.ftl");
        LAYER_TEMPLATE_MAP.put("layoutBreadcrumbHtml", "masterdetail/layout/breadcrumb.html.ftl");
        LAYER_TEMPLATE_MAP.put("layoutFooterHtml",  "masterdetail/layout/footer.html.ftl");
        LAYER_TEMPLATE_MAP.put("thymeleafList",     "masterdetail/thymeleaf-list.html.ftl");
        LAYER_TEMPLATE_MAP.put("thymeleafDetail",   "masterdetail/thymeleaf-detail.html.ftl");
        LAYER_TEMPLATE_MAP.put("thymeleafRegist",   "masterdetail/thymeleaf-regist.html.ftl");
        LAYER_TEMPLATE_MAP.put("thymeleafUpdt",     "masterdetail/thymeleaf-updt.html.ftl");
    }

    /** layoutMode=none 전용 — layout 참조 없는 독립 화면 템플릿 매핑 */
    private static final Map<String, String> STANDALONE_TEMPLATE_MAP;

    static {
        STANDALONE_TEMPLATE_MAP = new HashMap<>();
        STANDALONE_TEMPLATE_MAP.put("thymeleafList",   "masterdetail/thymeleaf-list-standalone.html.ftl");
        STANDALONE_TEMPLATE_MAP.put("thymeleafDetail", "masterdetail/thymeleaf-detail-standalone.html.ftl");
        STANDALONE_TEMPLATE_MAP.put("thymeleafRegist", "masterdetail/thymeleaf-regist-standalone.html.ftl");
        STANDALONE_TEMPLATE_MAP.put("thymeleafUpdt",   "masterdetail/thymeleaf-updt-standalone.html.ftl");
    }

    private final Configuration freemarkerConfig;

    public MasterDetailTemplateRenderer(
            @Qualifier("boardFreemarkerConfiguration") Configuration freemarkerConfig) {
        this.freemarkerConfig = freemarkerConfig;
    }

    public String renderByLayerKey(String layerKey, MasterDetailTemplateModel model) {
        return renderByLayerKey(layerKey, model,
                ThymeleafLayoutValidator.DEFAULT_LAYOUT_VIEW,
                ThymeleafLayoutValidator.DEFAULT_BREADCRUMB_VIEW,
                ThymeleafLayoutValidator.DEFAULT_LAYOUT_BASE_PATH);
    }

    public String renderByLayerKey(
            String layerKey,
            MasterDetailTemplateModel model,
            String layoutView,
            String breadcrumbView,
            String layoutBasePath) {
        String templateName = LAYER_TEMPLATE_MAP.get(layerKey);
        if (templateName == null) {
            throw new IllegalArgumentException(
                    "지원하지 않는 master-detail layerKey: " + layerKey
                    + " (지원 목록: " + LAYER_TEMPLATE_MAP.keySet() + ")");
        }
        return render(templateName, toDataModel(layerKey, model, layoutView, breadcrumbView, layoutBasePath));
    }

    /**
     * layoutMode를 인지하는 화면 렌더링. NONE이면 layout 참조가 없는 독립 화면 템플릿을 사용한다.
     */
    public String renderByLayerKey(
            String layerKey,
            MasterDetailTemplateModel model,
            String layoutView,
            String breadcrumbView,
            String layoutBasePath,
            CrudLayoutMode layoutMode) {
        if (layoutMode != CrudLayoutMode.NONE) {
            return renderByLayerKey(layerKey, model, layoutView, breadcrumbView, layoutBasePath);
        }
        String templateName = STANDALONE_TEMPLATE_MAP.get(layerKey);
        if (templateName == null) {
            throw new IllegalArgumentException(
                    "standalone 템플릿이 없는 master-detail layerKey: " + layerKey
                    + " (지원 목록: " + STANDALONE_TEMPLATE_MAP.keySet() + ")");
        }
        return render(templateName, toDataModel(layerKey, model));
    }

    private String render(String templateName, Map<String, Object> dataModel) {
        try {
            Template template = freemarkerConfig.getTemplate(templateName);
            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            return writer.toString();
        } catch (IOException e) {
            throw new CrudTemplateRenderException("템플릿 로딩 실패: " + templateName, e);
        } catch (TemplateException e) {
            throw new CrudTemplateRenderException("템플릿 렌더링 실패: " + templateName, e);
        }
    }

    private Map<String, Object> toDataModel(String layerKey, MasterDetailTemplateModel model) {
        return toDataModel(layerKey, model,
                ThymeleafLayoutValidator.DEFAULT_LAYOUT_VIEW,
                ThymeleafLayoutValidator.DEFAULT_BREADCRUMB_VIEW,
                ThymeleafLayoutValidator.DEFAULT_LAYOUT_BASE_PATH);
    }

    private Map<String, Object> toDataModel(
            String layerKey,
            MasterDetailTemplateModel model,
            String layoutView,
            String breadcrumbView,
            String layoutBasePath) {
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
        data.put("queryContract",     current.queryContract());
        data.put("fkColumn",          model.fkColumn());
        data.put("fkField",           model.fkField());
        data.put("layoutView",        layoutView);
        data.put("breadcrumbView",    breadcrumbView);
        data.put("layoutBasePath",    layoutBasePath);
        return data;
    }
}
