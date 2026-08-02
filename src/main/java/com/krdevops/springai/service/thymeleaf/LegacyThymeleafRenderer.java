package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.BoundThymeleafView;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafFieldBinding;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * I-4C 3단계: {@link BoundThymeleafView}를 실제 Thymeleaf HTML 문자열로 렌더링한다.
 * 템플릿에 넘기는 값은 전부 {@code contract.fields()}/{@code route()}/{@code displayFieldNames()}
 * 에서만 오므로("Controller·VO가 JSP hint보다 우선") 생성된 {@code th:field}/{@code th:text}는
 * 항상 Binding Contract까지 되짚을 수 있다. 기존 {@code CrudTemplateRenderer}(DB 스키마 기반 생성)는
 * 건드리지 않는다 — 별도 FreeMarker {@code Configuration}(templates 루트)과 별도 템플릿
 * 디렉터리({@code templates/legacy-thymeleaf/})를 쓴다.
 */
@Service
public class LegacyThymeleafRenderer {

    private static final Map<LegacyScreenRole, String> TEMPLATE_BY_ROLE = buildTemplateMap();

    private static Map<LegacyScreenRole, String> buildTemplateMap() {
        Map<LegacyScreenRole, String> map = new EnumMap<>(LegacyScreenRole.class);
        map.put(LegacyScreenRole.LIST, "legacy-thymeleaf/list.html.ftl");
        map.put(LegacyScreenRole.FORM, "legacy-thymeleaf/form.html.ftl");
        map.put(LegacyScreenRole.DETAIL, "legacy-thymeleaf/detail.html.ftl");
        return map;
    }

    private final Configuration freemarkerConfig;

    public LegacyThymeleafRenderer(@Qualifier("boardFreemarkerConfiguration") Configuration freemarkerConfig) {
        this.freemarkerConfig = freemarkerConfig;
    }

    public String render(BoundThymeleafView view) {
        LegacyScreenRole role = view.skeleton().screenRole();
        String templateName = TEMPLATE_BY_ROLE.get(role);
        if (templateName == null) {
            throw new IllegalArgumentException("UNSUPPORTED_SCREEN_ROLE: " + role);
        }
        try {
            Template template = freemarkerConfig.getTemplate(templateName);
            StringWriter writer = new StringWriter();
            template.process(toDataModel(view), writer);
            return writer.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("템플릿 로딩 실패: " + templateName, exception);
        } catch (TemplateException exception) {
            throw new IllegalStateException("템플릿 렌더링 실패: " + templateName, exception);
        }
    }

    private Map<String, Object> toDataModel(BoundThymeleafView view) {
        ThymeleafBindingContract contract = view.contract();
        Map<String, Object> data = new HashMap<>();
        data.put("pageTitle", view.skeleton().pageTitle());
        data.put("layoutView", view.skeleton().layoutFragmentRef());
        data.put("route", contract.route());
        data.put("formFields", contract.fields().stream()
                .filter(field -> field.boundJspTag() != null)
                .toList());
        data.put("displayFields", resolveDisplayFieldBindings(contract));
        if (contract.primaryDisplayAttributeName() != null) {
            data.put("primaryDisplayAttributeName", contract.primaryDisplayAttributeName());
        }
        return data;
    }

    private List<ThymeleafFieldBinding> resolveDisplayFieldBindings(ThymeleafBindingContract contract) {
        Map<String, ThymeleafFieldBinding> byName = new LinkedHashMap<>();
        contract.fields().forEach(field -> byName.putIfAbsent(field.fieldName(), field));
        return contract.displayFieldNames().stream()
                .map(byName::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
