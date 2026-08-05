package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
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
 *
 * <p>ARCH-0608(2차 pass): 승인된 {@link ScreenSpecification}이 함께 전달되면 그 표시 밀도
 * ({@code layoutDensity}, LIST) / 입력칸 배치({@code formColumnLayout}, FORM) / 등록 버튼 위치
 * ({@code actionPlacement}, LIST)만 결합한다 —
 * {@code CrudTableDensityCssProcessor}/{@code CrudFormColumnCssProcessor}가 CRUD 생성 파이프라인에서
 * 쓰는 {@code egov-density-*}/{@code egov-layout-two-col} 클래스 계약을 그대로 재사용한다. route,
 * field, 검증, 보안 계약은 {@link ThymeleafBindingContract}(Legacy 증거)에서만 나오며 이 결합으로
 * 절대 바뀌지 않는다 — 우선순위: 업무 Binding/보안 계약 > 승인 ScreenSpecification > 안전한 기본값
 * (구현계획서 §12). {@code status()}가 {@link ScreenSpecStatus#APPROVED}가 아니면 무시하고 기본값
 * ({@link LayoutDensity#STANDARD}/{@link FormColumnLayout#SINGLE_COLUMN}/
 * {@link ActionPlacement#TOP_RIGHT})을 쓴다. DESIGN.md 결합(§12 우선순위의 그 다음 단계)은 대응하는
 * 카테고리 키 계약이 아직 없어 이번 pass 범위 밖이다.
 *
 * <p>{@code SearchPanelPlacement.NONE}과 DETAIL 화면은 의도적으로 결합하지 않는다.
 * {@code SearchPanelPlacement}는 CRUD 생성 파이프라인에서 검색 패널의 "유무"(존재 자체)를 결정하는
 * 값인데, WP6은 legacy JSP evidence를 보존하는 파이프라인이라 그 evidence가 검색 폼의 실제 존재
 * 여부를 아직 계약에 담지 않는다(list.html.ftl의 검색 폼은 현재 항상 렌더링됨) — 디자인 입력만으로
 * 이 폼을 조용히 지우면 evidence 기반 기능을 삭제하는 것과 같아 우선순위 규칙("디자인 입력은 route,
 * method, field, validation, 권한, CSRF를 변경할 수 없다")의 취지를 벗어난다. DETAIL은
 * {@code thymeleaf-detail-body.html.ftl}(CRUD 파이프라인)에도 대응 클래스 계약이 없어 참고할 기존
 * 패턴 자체가 없다.
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
        return compose(contract, pageTitle, layoutView, null, null);
    }

    /**
     * @param contract 조립된 Binding 계약
     * @param pageTitle 화면 제목
     * @param layoutView {@code layout:decorate}가 가리킬 공통 레이아웃 fragment 경로
     * @param approvedScreenSpecification 승인된 ScreenSpecification(ARCH-0608). {@code null}이거나
     *                                     {@link ScreenSpecStatus#APPROVED}가 아니면 무시한다.
     */
    public ThymeleafGenerationStageResult<String> compose(
            ThymeleafBindingContract contract, String pageTitle, String layoutView,
            ScreenSpecification approvedScreenSpecification) {
        return compose(contract, pageTitle, layoutView, approvedScreenSpecification, null);
    }

    /**
     * @param contract 조립된 Binding 계약
     * @param pageTitle 화면 제목
     * @param layoutView {@code layout:decorate}가 가리킬 공통 레이아웃 fragment 경로
     * @param approvedScreenSpecification 승인된 ScreenSpecification(ARCH-0608). {@code null}이거나
     *                                     {@link ScreenSpecStatus#APPROVED}가 아니면 무시한다.
     * @param registRoute LIST 화면과 짝을 이루는 등록(FORM) 화면의 route. WP6는 화면별 evidence를
     *                     독립적으로 조립하므로 이 값은 항상 호출자가 실제로 확인한 route만 전달해야
     *                     한다 — {@code null}이면(기본값) 등록 링크 자체를 렌더링하지 않는다(깨진
     *                     링크를 만들지 않기 위한 안전장치). {@code actionPlacement}(위치)만 승인
     *                     ScreenSpecification을 따르고, route 존재 여부는 이 값에만 의존한다.
     */
    public ThymeleafGenerationStageResult<String> compose(
            ThymeleafBindingContract contract, String pageTitle, String layoutView,
            ScreenSpecification approvedScreenSpecification, String registRoute) {
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

        Map<String, Object> dataModel = buildDataModel(
                contract, pageTitle, layoutView, approvedScreenSpecification, registRoute);
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

    private Map<String, Object> buildDataModel(
            ThymeleafBindingContract contract, String pageTitle, String layoutView,
            ScreenSpecification approvedScreenSpecification, String registRoute) {
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
        // WP6 3차 pass: 부 root(예: 첨부파일 목록, master-detail 하위 표)는 주 root와 다른 VO에서
        // 검증되므로 ThymeleafFieldBinding(주 VO 기준 provenance)이 아니라 필드명 문자열 그대로 쓴다.
        model.put("secondaryDisplayFields", contract.secondaryDisplayFieldNames());
        model.put("secondaryDisplayAttributeName", contract.secondaryDisplayAttributeName());
        model.put("route", contract.route());
        model.put("layoutDensity", resolveLayoutDensity(approvedScreenSpecification).name());
        model.put("formColumnLayout", resolveFormColumnLayout(approvedScreenSpecification).name());
        model.put("actionPlacement", resolveActionPlacement(approvedScreenSpecification).name());
        model.put("registRoute", registRoute);
        return model;
    }

    private boolean isApproved(ScreenSpecification spec) {
        return spec != null && spec.status() == ScreenSpecStatus.APPROVED;
    }

    private LayoutDensity resolveLayoutDensity(ScreenSpecification approvedScreenSpecification) {
        return isApproved(approvedScreenSpecification)
                ? approvedScreenSpecification.layoutDensity() : LayoutDensity.STANDARD;
    }

    private FormColumnLayout resolveFormColumnLayout(ScreenSpecification approvedScreenSpecification) {
        return isApproved(approvedScreenSpecification)
                ? approvedScreenSpecification.formColumnLayout() : FormColumnLayout.SINGLE_COLUMN;
    }

    private ActionPlacement resolveActionPlacement(ScreenSpecification approvedScreenSpecification) {
        return isApproved(approvedScreenSpecification)
                ? approvedScreenSpecification.actionPlacement() : ActionPlacement.TOP_RIGHT;
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
