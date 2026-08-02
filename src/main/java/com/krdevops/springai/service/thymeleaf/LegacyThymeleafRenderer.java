package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.BoundThymeleafView;
import com.krdevops.springai.model.thymeleaf.ThymeleafFieldBinding;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * R6-057: HTML Skeleton 렌더링 서비스.
 *
 * <p>{@link BoundThymeleafView}(구조 + 바인딩)을 FreeMarker 템플릿으로 렌더링하여
 * 완성된 Thymeleaf HTML을 생성한다.
 *
 * <p>처리 흐름:
 * <ul>
 *   <li>BoundThymeleafView에서 skeleton + contract 추출
 *   <li>screenRole에 따라 적절한 .ftl 템플릿 선택
 *   <li>contract의 바인딩 정보를 FreeMarker 데이터 모델로 구성
 *   <li>템플릿 렌더링하여 HTML 문자열 반환
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LegacyThymeleafRenderer {

    private final Configuration boardFreemarkerConfiguration;

    private static final Map<String, String> TEMPLATE_NAME_BY_ROLE = Map.of(
            "LIST", "legacy-thymeleaf/list.html.ftl",
            "FORM", "legacy-thymeleaf/form.html.ftl",
            "DETAIL", "legacy-thymeleaf/detail.html.ftl"
    );

    /**
     * {@link BoundThymeleafView}를 HTML 문자열로 렌더링한다.
     *
     * @param view 렌더링 대상 BoundThymeleafView (구조 + 바인딩)
     * @return 렌더링된 Thymeleaf HTML 문자열
     * @throws RuntimeException FreeMarker 템플릿 로드/렌더링 실패 시
     */
    public String render(BoundThymeleafView view) {
        String screenRole = view.skeleton().screenRole().toString();
        String templateName = TEMPLATE_NAME_BY_ROLE.get(screenRole);

        if (templateName == null) {
            throw new RuntimeException("UNSUPPORTED_SCREEN_ROLE: " + screenRole);
        }

        log.info("Rendering BoundThymeleafView: screenId={}, role={}, template={}",
                view.skeleton().screenId(), screenRole, templateName);

        // FreeMarker 데이터 모델 구성
        Map<String, Object> dataModel = buildDataModel(view);

        // 템플릿 로드 및 렌더링
        try {
            Template template = boardFreemarkerConfiguration.getTemplate(templateName);
            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            String htmlContent = writer.toString();

            log.debug("Rendered HTML: screenId={}, length={} bytes",
                    view.skeleton().screenId(), htmlContent.length());
            return htmlContent;
        } catch (IOException e) {
            throw new RuntimeException(
                    "TEMPLATE_LOAD_FAILED: " + templateName + " - " + e.getMessage(), e);
        } catch (TemplateException e) {
            throw new RuntimeException(
                    "TEMPLATE_RENDER_FAILED: " + templateName + " - " + e.getMessage(), e);
        }
    }

    /**
     * FreeMarker 데이터 모델을 구성한다.
     *
     * <p>BoundThymeleafView의 skeleton과 contract 정보를 FreeMarker 템플릿에
     * 전달할 수 있는 형태로 변환한다.
     */
    private Map<String, Object> buildDataModel(BoundThymeleafView view) {
        Map<String, Object> model = new HashMap<>();

        // Skeleton 정보
        model.put("pageTitle", view.skeleton().pageTitle());
        model.put("layoutView", view.skeleton().layoutFragmentRef());
        model.put("screenId", view.skeleton().screenId());

        // Contract 정보 (바인딩)
        // displayFields: 목록 화면에서 표시할 필드들 (ThymeleafFieldBinding 객체)
        // formFields: 등록/수정 폼에서 사용할 필드들 (ThymeleafFieldBinding 객체)
        // 템플릿이 displayFields에서 fieldName()을 호출하므로 fields를 사용해야 함
        model.put("displayFields", view.contract().fields());
        model.put("formFields", view.contract().fields());
        model.put("primaryDisplayAttributeName", view.contract().primaryDisplayAttributeName());
        model.put("route", view.contract().route());

        log.debug("Data model prepared with {} keys", model.size());
        return model;
    }
}
