package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.thymeleaf.BindingContractStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.service.contract.GenerationIssueFactory;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WP6/ARCH-0610~0613/0621: {@link ThymeleafBindingContract}을 LIST/FORM/DETAIL FreeMarker
 * 템플릿({@code templates/legacy-thymeleaf/*.html.ftl})에 결합해 {@code th:field}/{@code th:text}/
 * {@code th:each}/{@code th:if}, form action/method/CSRF, validation error가 채워진 Thymeleaf
 * HTML을 만든다. 모든 동적 값은 {@code contract.fields()}/{@code route()}에서 그대로 나오므로
 * provenance(각 {@link com.krdevops.springai.model.thymeleaf.ThymeleafFieldBinding#provenance()})를
 * 항상 되짚을 수 있다.
 *
 * <p>ARCH-0621: {@code contract.status()}가 {@link BindingContractStatus#REVIEW_REQUIRED}면
 * 렌더링을 시도하지 않고 즉시 실패를 반환한다 — 미해결 binding으로 만든 HTML이
 * {@code ThymeleafProjectWorkflowService.preview()}까지 흘러가는 것을 막기 위함이다.
 *
 * <p>옛 {@code LegacyThymeleafViewComposer}+{@code LegacyThymeleafRenderer}(162bb3c에서 삭제)를
 * 하나로 합쳐 재구현한다. {@code ScreenHtmlSkeletonGenerator}(ARCH-0609, `ScreenSpecification`
 * 기반)는 이 pass에서 쓰지 않는다 — ARCH-WP6 스코프 컷 메모 참고.
 */
@Slf4j
@Service
public class BindingComposer {

    private static final String STAGE = "BINDING_COMPOSE";

    private static final Map<String, String> TEMPLATE_NAME_BY_ROLE = Map.of(
            "LIST", "legacy-thymeleaf/list.html.ftl",
            "FORM", "legacy-thymeleaf/form.html.ftl",
            "DETAIL", "legacy-thymeleaf/detail.html.ftl"
    );

    private final Configuration boardFreemarkerConfiguration;
    private final GenerationIssueFactory issueFactory;

    public BindingComposer(Configuration boardFreemarkerConfiguration, GenerationIssueFactory issueFactory) {
        this.boardFreemarkerConfiguration = boardFreemarkerConfiguration;
        this.issueFactory = issueFactory;
    }

    /**
     * @param contract 조립된 Binding 계약
     * @param pageTitle 화면 제목
     * @param layoutView {@code layout:decorate}가 가리킬 공통 레이아웃 fragment 경로
     */
    public ThymeleafGenerationStageResult<String> compose(
            ThymeleafBindingContract contract, String pageTitle, String layoutView) {
        if (contract.status() == BindingContractStatus.REVIEW_REQUIRED) {
            GenerationIssue blocked = issueFactory.issue(
                    "BINDING_REVIEW_REQUIRED_BLOCKS_COMPOSE", GenerationIssue.Severity.FATAL, STAGE,
                    contract.screenId(),
                    "Binding Contract가 REVIEW_REQUIRED 상태라 Thymeleaf HTML을 생성하지 않습니다. "
                            + "미해결 이슈: " + contract.issues(), null);
            return ThymeleafGenerationStageResult.failure(List.of(blocked));
        }

        String screenRole = contract.screenRole().toString();
        String templateName = TEMPLATE_NAME_BY_ROLE.get(screenRole);
        if (templateName == null) {
            GenerationIssue unsupported = issueFactory.issue(
                    "UNSUPPORTED_SCREEN_ROLE", GenerationIssue.Severity.FATAL, STAGE, contract.screenId(),
                    "지원하지 않는 screenRole입니다: " + screenRole, null);
            return ThymeleafGenerationStageResult.failure(List.of(unsupported));
        }

        Map<String, Object> dataModel = buildDataModel(contract, pageTitle, layoutView);
        try {
            Template template = boardFreemarkerConfiguration.getTemplate(templateName);
            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            String html = writer.toString();
            log.debug("BindingComposer rendered: screenId={}, role={}, length={}",
                    contract.screenId(), screenRole, html.length());
            return ThymeleafGenerationStageResult.success(html, contract.issues());
        } catch (IOException exception) {
            GenerationIssue issue = issueFactory.issue(
                    "TEMPLATE_LOAD_FAILED", GenerationIssue.Severity.FATAL, STAGE, templateName,
                    exception.getMessage(), null);
            return ThymeleafGenerationStageResult.failure(List.of(issue));
        } catch (TemplateException exception) {
            GenerationIssue issue = issueFactory.issue(
                    "TEMPLATE_RENDER_FAILED", GenerationIssue.Severity.FATAL, STAGE, templateName,
                    exception.getMessage(), null);
            return ThymeleafGenerationStageResult.failure(List.of(issue));
        }
    }

    private Map<String, Object> buildDataModel(ThymeleafBindingContract contract, String pageTitle, String layoutView) {
        Map<String, Object> model = new HashMap<>();
        model.put("pageTitle", pageTitle);
        model.put("layoutView", layoutView);
        model.put("screenId", contract.screenId());
        // LIST/DETAIL은 Assembler가 고른 표시 필드만(ARCH-0622로 발견: 전체 VO 필드를 그대로 쓰면
        // pageIndex 등 표시 대상이 아닌 필드까지 th:text 대상이 되어 실제 렌더 시 존재하지 않는
        // 속성을 참조하게 된다), FORM은 VO 전체 바인딩 가능 필드를 그대로 쓴다.
        model.put("displayFields", resolveDisplayFieldBindings(contract));
        model.put("formFields", contract.fields());
        model.put("primaryDisplayAttributeName", contract.primaryDisplayAttributeName());
        model.put("route", contract.route());
        return model;
    }

    private List<com.krdevops.springai.model.thymeleaf.ThymeleafFieldBinding> resolveDisplayFieldBindings(
            ThymeleafBindingContract contract) {
        Map<String, com.krdevops.springai.model.thymeleaf.ThymeleafFieldBinding> byName = new java.util.LinkedHashMap<>();
        contract.fields().forEach(field -> byName.put(field.fieldName(), field));
        return contract.displayFieldNames().stream()
                .map(byName::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
