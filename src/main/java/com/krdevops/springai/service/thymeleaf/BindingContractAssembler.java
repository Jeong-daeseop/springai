package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.thymeleaf.BindingContractStatus;
import com.krdevops.springai.model.thymeleaf.ControllerEvidence;
import com.krdevops.springai.model.thymeleaf.ControllerMethodEvidence;
import com.krdevops.springai.model.thymeleaf.JspDisplayFieldEvidence;
import com.krdevops.springai.model.thymeleaf.JspEvidence;
import com.krdevops.springai.model.thymeleaf.JspFormEvidence;
import com.krdevops.springai.model.thymeleaf.JspFormFieldEvidence;
import com.krdevops.springai.model.thymeleaf.JspModelReferenceEvidence;
import com.krdevops.springai.model.thymeleaf.LegacyScreenAnalysis;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafFieldBinding;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.model.thymeleaf.ThymeleafRouteBinding;
import com.krdevops.springai.model.thymeleaf.VoEvidence;
import com.krdevops.springai.model.thymeleaf.VoFieldEvidence;
import com.krdevops.springai.service.contract.GenerationIssueFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * WP6/ARCH-0607: {@link LegacyScreenAnalysis}(JSP+Controller+VO 증거)를
 * {@link ThymeleafBindingContract}로 조립한다. Controller·VO가 JSP hint보다 항상 우선하며, FATAL
 * 판정 시 계약 자체를 만들지 않고 {@link ThymeleafGenerationStageResult#failure}를 반환한다
 * ("FATAL 이후 후속 단계가 실행되지 않음").
 *
 * <p>옛 {@code LegacyBindingContractAssembler}(162bb3c에서 삭제, ARCH-WP6 스코프 컷 메모 참고)와
 * 동일한 판정 로직을 재구현한다.
 *
 * <p>{@code DISPLAY_FIELD_WITHOUT_VO_FIELD}(표시 필드가 VO에 없어 조용히 누락되는 경우)와
 * {@code MULTIPLE_DISPLAY_ROOTS_AMBIGUOUS}(예: 게시판 상세의 본문과 답글 목록처럼 표시 대상 root가
 * 여러 개라 {@code primaryDisplayAttributeName} 선택이 모호한 경우)는 과거 WARNING으로만 기록되고
 * 계약이 그대로 {@code RESOLVED}로 통과해 원본보다 적거나 잘못된 화면이 "성공"으로 렌더링될 위험이
 * 있었다. 2026-08-04부로 이 두 이슈도 {@link BindingContractStatus#REVIEW_REQUIRED} 판정에 포함시켜
 * {@code BindingComposer}가 렌더링을 BLOCK하도록(ARCH-0621) 만든다.
 */
@Service
public class BindingContractAssembler {

    private static final String STAGE = "BINDING_CONTRACT";

    /** 타입 정보 없이 Model attribute 이름만으로는 VO 인스턴스인지 알 수 없는 잘 알려진 인프라 속성. */
    private static final Set<String> NON_VO_MODEL_ATTRIBUTE_NAMES = Set.of(
            "paginationInfo", "message", "currentMenuId", "currentPageSuffix", "menuContextUrl");

    private final GenerationIssueFactory issueFactory;

    public BindingContractAssembler(GenerationIssueFactory issueFactory) {
        this.issueFactory = issueFactory;
    }

    public ThymeleafGenerationStageResult<ThymeleafBindingContract> assemble(LegacyScreenAnalysis analysis) {
        JspEvidence jsp = analysis.jsp();
        ControllerEvidence controller = analysis.controller();
        VoEvidence vo = analysis.vo();
        LegacyScreenRole role = analysis.screenRole();

        List<GenerationIssue> issues = new ArrayList<>();

        String jspBaseName = baseName(jsp.jspPath());
        Optional<ControllerMethodEvidence> viewMethod = controller.methods().stream()
                .filter(method -> jspBaseName.equals(method.viewBaseName()))
                .findFirst();
        if (viewMethod.isEmpty()) {
            issues.add(issueFactory.warning(
                    "JSP_VIEW_NOT_LINKED_TO_CONTROLLER", STAGE,
                    "JSP 파일명(" + jspBaseName + ")과 일치하는 반환 뷰를 가진 Controller 메서드를 찾지 못했습니다.",
                    jsp.jspPath(), null));
        }

        Optional<RouteResolution> resolution = role == LegacyScreenRole.FORM
                ? resolveFormRoute(jsp, controller, viewMethod, issues)
                : resolveListOrDetailRoute(jsp, viewMethod);
        if (resolution.isEmpty()) {
            if (issues.stream().noneMatch(this::isFatal)) {
                issues.add(issueFactory.issue(
                        "SCREEN_ROUTE_NOT_RESOLVED", GenerationIssue.Severity.FATAL, STAGE, jsp.jspPath(),
                        "화면의 기준 route를 확인할 Controller 메서드를 찾지 못했습니다.", null));
            }
            return ThymeleafGenerationStageResult.failure(issues);
        }

        validateSecondaryFormActions(jsp, controller, resolution.get().form(), issues);
        List<ThymeleafFieldBinding> fields = buildFieldBindings(jsp, vo, issues);
        if (issues.stream().anyMatch(this::isFatal)) {
            return ThymeleafGenerationStageResult.failure(issues);
        }

        DisplayFieldResolution displayFields = resolveDisplayFields(jsp, vo, controller, issues);

        ModelAttributeResolution modelAttributes = resolveModelAttributes(jsp, controller, issues);

        boolean voHasNoFields = vo.fields().isEmpty();
        if (voHasNoFields) {
            issues.add(issueFactory.error(
                    "VO_HAS_NO_FIELDS", STAGE, "VO에 필드가 없습니다.", vo.voPath()));
        }

        boolean reviewRequired = viewMethod.isEmpty() || voHasNoFields
                || issues.stream().anyMatch(issue -> "JSP_FORM_ACTION_UNRESOLVED".equals(issue.code())
                        || "DISPLAY_FIELD_WITHOUT_VO_FIELD".equals(issue.code())
                        || "MULTIPLE_DISPLAY_ROOTS_AMBIGUOUS".equals(issue.code()));

        ControllerMethodEvidence method = resolution.get().method();
        ThymeleafRouteBinding route = new ThymeleafRouteBinding(
                method.route(), method.httpMethod(), method.methodName(),
                method.modelAttributeParamName(), method.modelAttributeType(),
                method.validated(), method.redirect(), method.securityEvidence());

        ThymeleafBindingContract contract = new ThymeleafBindingContract(
                analysis.screenId(), role, route, fields,
                displayFields.fieldNames(), displayFields.primaryDisplayAttributeName(),
                modelAttributes.resolved(), modelAttributes.unresolved(),
                reviewRequired ? BindingContractStatus.REVIEW_REQUIRED : BindingContractStatus.RESOLVED,
                issues, analysis.sourceRevision(), Instant.now());

        return ThymeleafGenerationStageResult.success(contract, issues);
    }

    private Optional<RouteResolution> resolveFormRoute(
            JspEvidence jsp,
            ControllerEvidence controller,
            Optional<ControllerMethodEvidence> viewMethod,
            List<GenerationIssue> issues
    ) {
        Optional<JspFormEvidence> submitForm = jsp.forms().stream()
                .filter(form -> form.modelAttribute() != null)
                .findFirst()
                .or(() -> jsp.forms().stream().filter(form -> "POST".equals(form.httpMethod())).findFirst());

        if (submitForm.isEmpty()) {
            return viewMethod.map(method -> new RouteResolution(method, null));
        }

        JspFormEvidence form = submitForm.get();
        if (form.resolvedRoute() == null) {
            issues.add(issueFactory.warning(
                    "JSP_FORM_ACTION_UNRESOLVED", STAGE,
                    "폼 action에서 route를 확인할 수 없습니다.", form.sourceLocation(), null));
            return viewMethod.map(method -> new RouteResolution(method, null));
        }

        Optional<ControllerMethodEvidence> matched = findMethod(controller, form.resolvedRoute(), form.httpMethod());
        if (matched.isEmpty()) {
            issues.add(issueFactory.issue(
                    "JSP_FORM_ACTION_NOT_BOUND", GenerationIssue.Severity.FATAL, STAGE, form.sourceLocation(),
                    "폼 action(" + form.resolvedRoute() + " " + form.httpMethod()
                            + ")과 일치하는 Controller 메서드가 없습니다.", null));
            return Optional.empty();
        }
        return Optional.of(new RouteResolution(matched.get(), form));
    }

    private Optional<RouteResolution> resolveListOrDetailRoute(
            JspEvidence jsp, Optional<ControllerMethodEvidence> viewMethod) {
        return viewMethod.map(method -> {
            Optional<JspFormEvidence> selfSubmitForm = jsp.forms().stream()
                    .filter(form -> method.route().equals(form.resolvedRoute())
                            && method.httpMethod().equals(form.httpMethod()))
                    .findFirst();
            return new RouteResolution(method, selfSubmitForm.orElse(null));
        });
    }

    private void validateSecondaryFormActions(
            JspEvidence jsp, ControllerEvidence controller, JspFormEvidence primaryForm, List<GenerationIssue> issues) {
        for (JspFormEvidence form : jsp.forms()) {
            if (form == primaryForm) {
                continue;
            }
            if (form.resolvedRoute() == null) {
                issues.add(issueFactory.warning(
                        "JSP_FORM_ACTION_UNRESOLVED", STAGE,
                        "폼 action에서 route를 확인할 수 없습니다.", form.sourceLocation(), null));
                continue;
            }
            boolean matched = controller.methods().stream().anyMatch(method ->
                    form.resolvedRoute().equals(method.route()) && form.httpMethod().equals(method.httpMethod()));
            if (!matched) {
                issues.add(issueFactory.issue(
                        "JSP_FORM_ACTION_NOT_BOUND", GenerationIssue.Severity.FATAL, STAGE, form.sourceLocation(),
                        "폼 action(" + form.resolvedRoute() + " " + form.httpMethod()
                                + ")과 일치하는 Controller 메서드가 없습니다.", null));
            }
        }
    }

    private List<ThymeleafFieldBinding> buildFieldBindings(
            JspEvidence jsp, VoEvidence vo, List<GenerationIssue> issues) {
        Map<String, JspFormFieldEvidence> jspFieldsByName = new LinkedHashMap<>();
        for (JspFormEvidence form : jsp.forms()) {
            for (JspFormFieldEvidence field : form.fields()) {
                jspFieldsByName.putIfAbsent(field.fieldName(), field);
            }
        }

        for (Map.Entry<String, JspFormFieldEvidence> entry : jspFieldsByName.entrySet()) {
            Optional<VoFieldEvidence> voField = vo.field(entry.getKey());
            if (voField.isEmpty()) {
                issues.add(issueFactory.issue(
                        "FORM_FIELD_WITHOUT_VO_FIELD", GenerationIssue.Severity.FATAL, STAGE,
                        entry.getValue().sourceLocation(),
                        "JSP 입력 필드 '" + entry.getKey() + "'에 대응하는 VO 필드가 없습니다.", null));
            } else if (!voField.get().writable()) {
                issues.add(issueFactory.issue(
                        "WRITE_BINDING_TO_READONLY_FIELD", GenerationIssue.Severity.FATAL, STAGE,
                        entry.getValue().sourceLocation(),
                        "VO 필드 '" + entry.getKey() + "'는 setter가 없어 입력 바인딩할 수 없습니다.", null));
            }
        }

        List<ThymeleafFieldBinding> bindings = new ArrayList<>();
        for (VoFieldEvidence field : vo.fields()) {
            JspFormFieldEvidence jspField = jspFieldsByName.get(field.fieldName());
            bindings.add(new ThymeleafFieldBinding(
                    field.fieldName(), field.javaType(), field.writable(), field.required(),
                    field.validationAnnotations(),
                    jspField != null ? jspField.tagName() : null,
                    jspField != null ? "CONTROLLER_VO" : "VO_ONLY",
                    jspField != null ? 1.0 : 0.6));
        }
        return bindings;
    }

    /**
     * WARNING 검증(DISPLAY_FIELD_WITHOUT_VO_FIELD)과 함께, Composer가 LIST th:each/DETAIL 표시에
     * 쓸 {@code displayFieldNames}(JSP 등장 순서)와 {@code primaryDisplayAttributeName}(LIST는
     * forEach items 속성, DETAIL은 가장 많이 등장한 root)을 계산한다.
     */
    private DisplayFieldResolution resolveDisplayFields(
            JspEvidence jsp, VoEvidence vo, ControllerEvidence controller, List<GenerationIssue> issues) {
        Set<String> loopVariables = new LinkedHashSet<>();
        Map<String, String> loopVariableToItemsAttribute = new LinkedHashMap<>();
        jsp.forEachBindings().forEach(binding -> {
            loopVariables.add(binding.loopVariable());
            loopVariableToItemsAttribute.putIfAbsent(binding.loopVariable(), binding.itemsAttributeName());
        });

        Set<String> controllerModelAttributes = new LinkedHashSet<>();
        controller.methods().forEach(method -> controllerModelAttributes.addAll(method.modelAttributesAdded()));

        List<String> fieldNames = new ArrayList<>();
        Map<String, Integer> rootFrequency = new LinkedHashMap<>();
        String itemsAttributeFromLoop = null;

        for (JspDisplayFieldEvidence display : jsp.displayFields()) {
            String root = display.rootAttributeName();
            boolean candidateVoRoot = loopVariables.contains(root)
                    || (controllerModelAttributes.contains(root) && !NON_VO_MODEL_ATTRIBUTE_NAMES.contains(root));
            if (!candidateVoRoot) {
                continue;
            }
            rootFrequency.merge(root, 1, Integer::sum);
            if (itemsAttributeFromLoop == null && loopVariableToItemsAttribute.containsKey(root)) {
                itemsAttributeFromLoop = loopVariableToItemsAttribute.get(root);
            }
            if (vo.field(display.fieldName()).isEmpty()) {
                issues.add(issueFactory.warning(
                        "DISPLAY_FIELD_WITHOUT_VO_FIELD", STAGE,
                        "표시 필드 '" + root + "." + display.fieldName() + "'에 대응하는 VO 필드가 없습니다.",
                        display.sourceLocation(), null));
                continue;
            }
            if (!fieldNames.contains(display.fieldName())) {
                fieldNames.add(display.fieldName());
            }
        }

        if (rootFrequency.size() > 1) {
            issues.add(issueFactory.warning(
                    "MULTIPLE_DISPLAY_ROOTS_AMBIGUOUS", STAGE,
                    "표시 대상 root가 여러 개(" + rootFrequency.keySet()
                            + ") 발견되어 primaryDisplayAttributeName 선택이 모호합니다. "
                            + "예: 게시판 상세의 본문(post)과 답글 목록(reply)이 한 화면에 같이 있는 경우.",
                    null, "화면을 분리하거나 승인된 ScreenSpecification으로 표시 대상을 명시하세요."));
        }

        String primaryDisplayAttributeName = itemsAttributeFromLoop != null
                ? itemsAttributeFromLoop
                : rootFrequency.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);

        return new DisplayFieldResolution(List.copyOf(fieldNames), primaryDisplayAttributeName);
    }

    private ModelAttributeResolution resolveModelAttributes(
            JspEvidence jsp, ControllerEvidence controller, List<GenerationIssue> issues) {
        Set<String> known = new LinkedHashSet<>();
        controller.methods().forEach(method -> {
            known.addAll(method.modelAttributesAdded());
            if (method.modelAttributeParamName() != null) {
                known.add(method.modelAttributeParamName());
            }
        });

        List<String> resolved = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (JspModelReferenceEvidence reference : jsp.modelReferences()) {
            if (known.contains(reference.attributeName())) {
                resolved.add(reference.attributeName());
            } else {
                unresolved.add(reference.attributeName());
                issues.add(issueFactory.warning(
                        "MODEL_ATTRIBUTE_UNRESOLVED", STAGE,
                        "JSP가 참조하는 Model attribute '" + reference.attributeName()
                                + "'를 Controller에서 찾지 못했습니다(리다이렉트 경유 가능성 포함).",
                        reference.sourceLocation(), null));
            }
        }
        return new ModelAttributeResolution(List.copyOf(resolved), List.copyOf(unresolved));
    }

    private Optional<ControllerMethodEvidence> findMethod(
            ControllerEvidence controller, String route, String httpMethod) {
        return controller.methods().stream()
                .filter(method -> route.equals(method.route()) && httpMethod.equalsIgnoreCase(method.httpMethod()))
                .findFirst();
    }

    private boolean isFatal(GenerationIssue issue) {
        return issue.severity() == GenerationIssue.Severity.FATAL;
    }

    private String baseName(String path) {
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private record RouteResolution(ControllerMethodEvidence method, JspFormEvidence form) {
    }

    private record ModelAttributeResolution(List<String> resolved, List<String> unresolved) {
    }

    private record DisplayFieldResolution(List<String> fieldNames, String primaryDisplayAttributeName) {
    }
}
