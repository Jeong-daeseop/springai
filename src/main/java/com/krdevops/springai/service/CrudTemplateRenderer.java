package com.krdevops.springai.service;

import com.krdevops.springai.exception.CrudTemplateRenderException;
import com.krdevops.springai.model.crud.CrudLayoutMode;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.policy.SensitiveFieldPolicy;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * FreeMarker 템플릿 렌더러.
 * layerKey → 템플릿 파일명 매핑 후 CrudTemplateModel을 바인딩하여 소스 문자열을 반환한다.
 */
@Service
public class CrudTemplateRenderer {

    /** layerKey → 템플릿 파일명 매핑 */
    private static final Map<String, String> LAYER_TEMPLATE_MAP;

    static {
        LAYER_TEMPLATE_MAP = new HashMap<>();
        LAYER_TEMPLATE_MAP.put("vo",                "vo.java.ftl");
        LAYER_TEMPLATE_MAP.put("mapper",            "mapper.java.ftl");
        LAYER_TEMPLATE_MAP.put("mapperXml",         "mapper.xml.ftl");
        LAYER_TEMPLATE_MAP.put("service",           "service.java.ftl");
        LAYER_TEMPLATE_MAP.put("serviceImpl",       "service-impl.java.ftl");
        LAYER_TEMPLATE_MAP.put("controller",        "controller.java.ftl");
        LAYER_TEMPLATE_MAP.put("controlleradvice",  "controller-advice.java.ftl");
        LAYER_TEMPLATE_MAP.put("jspList",           "jsp-list.jsp.ftl");
        LAYER_TEMPLATE_MAP.put("jspDetail",         "jsp-detail.jsp.ftl");
        LAYER_TEMPLATE_MAP.put("jspRegist",         "jsp-regist.jsp.ftl");
        LAYER_TEMPLATE_MAP.put("jspUpdt",           "jsp-updt.jsp.ftl");
        LAYER_TEMPLATE_MAP.put("layoutHtml",        "layout/default.html.ftl");
        LAYER_TEMPLATE_MAP.put("layoutGnbHtml",     "layout/gnb.html.ftl");
        LAYER_TEMPLATE_MAP.put("layoutLnbHtml",     "layout/lnb.html.ftl");
        LAYER_TEMPLATE_MAP.put("layoutBreadcrumbHtml", "layout/breadcrumb.html.ftl");
        LAYER_TEMPLATE_MAP.put("layoutFooterHtml",  "layout/footer.html.ftl");
        LAYER_TEMPLATE_MAP.put("layoutGnbMenuVo",          "layout/gnb-menu-vo.java.ftl");
        LAYER_TEMPLATE_MAP.put("layoutGnbMenuMapper",      "layout/gnb-menu-mapper.java.ftl");
        LAYER_TEMPLATE_MAP.put("layoutGnbMenuMapperXml",   "layout/gnb-menu-mapper.xml.ftl");
        LAYER_TEMPLATE_MAP.put("layoutGnbMenuInterceptor", "layout/gnb-menu-interceptor.java.ftl");
        LAYER_TEMPLATE_MAP.put("thymeleafList",     "thymeleaf-list.html.ftl");
        LAYER_TEMPLATE_MAP.put("thymeleafDetail",   "thymeleaf-detail.html.ftl");
        LAYER_TEMPLATE_MAP.put("thymeleafRegist",   "thymeleaf-regist.html.ftl");
        LAYER_TEMPLATE_MAP.put("thymeleafUpdt",     "thymeleaf-updt.html.ftl");
    }

    /** layoutMode=none 전용 — layout 참조 없는 독립 화면 템플릿 매핑 */
    private static final Map<String, String> STANDALONE_TEMPLATE_MAP;

    static {
        STANDALONE_TEMPLATE_MAP = new HashMap<>();
        STANDALONE_TEMPLATE_MAP.put("thymeleafList",   "thymeleaf-list-standalone.html.ftl");
        STANDALONE_TEMPLATE_MAP.put("thymeleafDetail", "thymeleaf-detail-standalone.html.ftl");
        STANDALONE_TEMPLATE_MAP.put("thymeleafRegist", "thymeleaf-regist-standalone.html.ftl");
        STANDALONE_TEMPLATE_MAP.put("thymeleafUpdt",   "thymeleaf-updt-standalone.html.ftl");
    }

    private final Configuration freemarkerConfig;

    public CrudTemplateRenderer(Configuration freemarkerConfig) {
        this.freemarkerConfig = freemarkerConfig;
    }

    /**
     * 템플릿 파일명으로 직접 렌더링한다.
     *
     * @param templateName 템플릿 파일명 (예: "vo.java.ftl")
     * @param model        렌더링 컨텍스트
     * @return 렌더링된 소스 문자열
     * @throws CrudTemplateRenderException 템플릿 로딩 또는 렌더링 실패 시
     */
    public String render(String templateName, CrudTemplateModel model) {
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
     * @param layerKey 레이어 키 (예: "vo", "controller", "jspList")
     * @param model    렌더링 컨텍스트
     * @return 렌더링된 소스 문자열
     * @throws IllegalArgumentException    지원하지 않는 layerKey 입력 시
     * @throws CrudTemplateRenderException 템플릿 로딩 또는 렌더링 실패 시
     */
    public String renderByLayerKey(String layerKey, CrudTemplateModel model) {
        return renderByLayerKey(layerKey, model,
                ThymeleafLayoutValidator.DEFAULT_LAYOUT_VIEW,
                ThymeleafLayoutValidator.DEFAULT_BREADCRUMB_VIEW,
                ThymeleafLayoutValidator.DEFAULT_LAYOUT_BASE_PATH);
    }

    public String renderByLayerKey(
            String layerKey,
            CrudTemplateModel model,
            String layoutView,
            String breadcrumbView,
            String layoutBasePath) {
        String templateName = LAYER_TEMPLATE_MAP.get(layerKey);
        if (templateName == null) {
            throw new IllegalArgumentException(
                    "지원하지 않는 layerKey: " + layerKey
                    + " (지원 목록: " + LAYER_TEMPLATE_MAP.keySet() + ")");
        }
        return render(templateName, toDataModel(model, layoutView, breadcrumbView, layoutBasePath));
    }

    /**
     * layoutMode를 인지하는 화면 렌더링. NONE이면 layout 참조가 없는 독립 화면 템플릿을 사용한다.
     */
    public String renderByLayerKey(
            String layerKey,
            CrudTemplateModel model,
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
                    "standalone 템플릿이 없는 layerKey: " + layerKey
                    + " (지원 목록: " + STANDALONE_TEMPLATE_MAP.keySet() + ")");
        }
        return render(templateName, toDataModel(model));
    }

    public String renderLayoutByLayerKey(String layerKey, String layoutBasePath) {
        String templateName = LAYER_TEMPLATE_MAP.get(layerKey);
        if (templateName == null) {
            throw new IllegalArgumentException(
                    "지원하지 않는 layerKey: " + layerKey
                    + " (지원 목록: " + LAYER_TEMPLATE_MAP.keySet() + ")");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("layoutBasePath", layoutBasePath);
        data.put("layoutView", layoutBasePath + "/default");
        data.put("breadcrumbView", layoutBasePath + "/breadcrumb");
        return render(templateName, data);
    }

    public String renderGnbMenuComponent(String layerKey, String packageName) {
        return renderGnbMenuComponent(layerKey, packageName, "LETTNMENUINFO", "LETTNPROGRMLIST");
    }

    /**
     * GNB 동적 메뉴 컴포넌트(VO/Mapper/MapperXml/Interceptor) 전용 렌더링.
     * layoutBasePath 개념이 없는 프로젝트 전역 산출물이라 packageName과 메뉴 테이블명만 필요하다.
     */
    public String renderGnbMenuComponent(
            String layerKey,
            String packageName,
            String menuTableName,
            String programTableName) {
        String templateName = LAYER_TEMPLATE_MAP.get(layerKey);
        if (templateName == null) {
            throw new IllegalArgumentException(
                    "지원하지 않는 layerKey: " + layerKey
                    + " (지원 목록: " + LAYER_TEMPLATE_MAP.keySet() + ")");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("packageName", packageName);
        data.put("menuTableName", menuTableName);
        data.put("programTableName", programTableName);
        data.put("date", java.time.LocalDate.now().toString());
        return render(templateName, data);
    }

    private String render(String templateName, Map<String, Object> dataModel) {
        try {
            Template template = freemarkerConfig.getTemplate(templateName);
            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
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
     * CrudTemplateModel record → FreeMarker 데이터 모델(Map) 변환.
     * FreeMarker 2.3.33이 Java record accessor를 지원하지만,
     * BeansWrapper 설정에 따라 불안정할 수 있으므로 명시적 Map으로 전달한다.
     */
    Map<String, Object> toDataModel(CrudTemplateModel model) {
        return toDataModel(model,
                ThymeleafLayoutValidator.DEFAULT_LAYOUT_VIEW,
                ThymeleafLayoutValidator.DEFAULT_BREADCRUMB_VIEW,
                ThymeleafLayoutValidator.DEFAULT_LAYOUT_BASE_PATH);
    }

    private Map<String, Object> toDataModel(
            CrudTemplateModel model,
            String layoutView,
            String breadcrumbView,
            String layoutBasePath) {
        Map<String, Object> data = new HashMap<>();
        data.put("packageName",       model.packageName());
        data.put("domain",            model.domain());
        data.put("domainLc",          model.domainLc());
        data.put("domainKr",          model.domainKr());
        data.put("tableName",         model.tableName());
        data.put("urlPrefix",         model.urlPrefix());
        data.put("route",             model.route());
        data.put("date",              model.date());
        data.put("egovVersion",       model.egovVersion());
        data.put("jakartaValidation", model.jakartaValidation());
        data.put("pk",                model.pk());
        data.put("pkFields",          model.pkFields());
        data.put("fields",            model.fields());
        data.put("listFields",        model.listFields());
        data.put("detailFields",      model.detailFields());
        data.put("effectiveDetailFields", model.detailFields() == null
                ? SensitiveFieldPolicy.filterDisplayFields(model.fields())
                : model.detailFields());
        data.put("layoutDensity",     model.layoutDensity());
        data.put("formColumnLayout",  model.formColumnLayout());
        data.put("actionPlacement",   model.actionPlacement());
        data.put("searchPanelPlacement", model.searchPanelPlacement());
        data.put("designComponents",  model.designComponents());
        data.put("nonPkFields",       model.nonPkFields());
        data.put("formFields",        model.formFields());
        data.put("queryContract",     model.queryContract());
        data.put("layoutView",        layoutView);
        data.put("breadcrumbView",    breadcrumbView);
        data.put("layoutBasePath",    layoutBasePath);
        return data;
    }
}
