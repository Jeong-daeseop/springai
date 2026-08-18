package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.ControllerEvidence;
import com.krdevops.springai.model.thymeleaf.ControllerMethodEvidence;
import com.krdevops.springai.model.thymeleaf.JspEvidence;
import com.krdevops.springai.model.thymeleaf.JspFormEvidence;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.ScreenRoleSuggestion;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * R6-053: JSP·Controller 정적 분석 증거만으로 화면 유형(LIST/FORM/DETAIL)을 추정한다.
 *
 * <p>{@code screenRole}은 {@link com.krdevops.springai.model.thymeleaf.ThymeleafBindingPreviewRequest}
 * 에서 여전히 호출자가 명시하는 필수값이다 — 이 Resolver는 그 값을 자동으로 대체하는 게 아니라,
 * 소스 증거와 어긋나는지 사전 점검해 사람이 확인할 근거·confidence를 제공하는 용도로만 쓴다.
 * 두 신호(Controller 경로 명명 규칙, JSP 구조)가 일치하면 confidence가 높고, 하나만 판정 가능하면
 * 중간, 서로 어긋나거나 둘 다 판정 불가면 낮거나 0이다.
 */
@Component
public class LegacyScreenRoleResolver {

    private static final List<String> LIST_ROUTE_SUFFIXES = List.of("list", "search");
    private static final List<String> FORM_ROUTE_SUFFIXES = List.of(
            "regist", "insert", "create", "add", "updt", "update", "edit", "modify");
    private static final List<String> DETAIL_ROUTE_SUFFIXES = List.of("detail", "view", "select");

    public ScreenRoleSuggestion suggest(JspEvidence jsp, ControllerEvidence controller) {
        LegacyScreenRole routeRole = resolveFromRoute(jsp, controller);
        LegacyScreenRole jspRole = resolveFromJsp(jsp);

        if (routeRole != null && routeRole == jspRole) {
            return new ScreenRoleSuggestion(routeRole, 0.95,
                    "Controller 경로 명명 규칙과 JSP 구조(form/forEach/표시 필드) 판정이 모두 "
                            + routeRole + "를 가리킴");
        }
        if (routeRole != null && jspRole == null) {
            return new ScreenRoleSuggestion(routeRole, 0.8,
                    "Controller 경로 명명 규칙만으로 " + routeRole + " 판정(JSP 구조는 근거 부족)");
        }
        if (routeRole == null && jspRole != null) {
            return new ScreenRoleSuggestion(jspRole, 0.7,
                    "JSP 구조(form/forEach/표시 필드)만으로 " + jspRole + " 판정(Controller 경로는 근거 부족)");
        }
        if (routeRole != null && jspRole != null) {
            return new ScreenRoleSuggestion(routeRole, 0.5,
                    "Controller 경로는 " + routeRole + ", JSP 구조는 " + jspRole
                            + "를 가리켜 서로 어긋남 — 경로 명명 규칙 판정을 우선함");
        }
        return new ScreenRoleSuggestion(null, 0.0,
                "Controller 경로 명명 규칙과 JSP 구조 모두 화면 유형을 판정할 근거가 부족함");
    }

    /**
     * 하나의 Controller 클래스가 LIST/FORM/DETAIL 여러 화면의 GET 메서드를 함께 갖는 것이
     * eGovFrame CRUD 컨벤션상 일반적이므로, 단순히 "첫 GET 메서드"를 쓰면 다른 화면의 경로로
     * 오판한다. {@code jsp}의 파일명(확장자 제외)과 {@link ControllerMethodEvidence#viewBaseName()}
     * 이 일치하는 메서드만 신뢰하고, 그런 메서드가 없을 때만(fixture가 이 컨벤션을 따르지 않는
     * 경우) 첫 GET 메서드로 fallback한다.
     */
    private LegacyScreenRole resolveFromRoute(JspEvidence jsp, ControllerEvidence controller) {
        if (controller == null) {
            return null;
        }
        String expectedViewBaseName = jspBaseName(jsp);
        if (expectedViewBaseName != null) {
            for (ControllerMethodEvidence method : controller.methods()) {
                if (!"GET".equals(method.httpMethod()) || method.redirect()) {
                    continue;
                }
                if (expectedViewBaseName.equalsIgnoreCase(method.viewBaseName())) {
                    LegacyScreenRole role = fromRouteSuffix(method.route());
                    if (role != null) {
                        return role;
                    }
                }
            }
        }
        for (ControllerMethodEvidence method : controller.methods()) {
            if (!"GET".equals(method.httpMethod()) || method.redirect()) {
                continue;
            }
            LegacyScreenRole role = fromRouteSuffix(method.route());
            if (role != null) {
                return role;
            }
        }
        return fromRouteSuffix(controller.classLevelRequestMapping());
    }

    private String jspBaseName(JspEvidence jsp) {
        if (jsp == null || jsp.jspPath() == null) {
            return null;
        }
        String path = jsp.jspPath();
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String fileName = lastSlash < 0 ? path : path.substring(lastSlash + 1);
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private LegacyScreenRole fromRouteSuffix(String route) {
        if (route == null || route.isBlank()) {
            return null;
        }
        String normalized = route.toLowerCase(Locale.ROOT).replaceAll("/+$", "");
        int lastSlash = normalized.lastIndexOf('/');
        String lastSegment = lastSlash < 0 ? normalized : normalized.substring(lastSlash + 1);
        if (matchesAny(lastSegment, LIST_ROUTE_SUFFIXES)) {
            return LegacyScreenRole.LIST;
        }
        if (matchesAny(lastSegment, FORM_ROUTE_SUFFIXES)) {
            return LegacyScreenRole.FORM;
        }
        if (matchesAny(lastSegment, DETAIL_ROUTE_SUFFIXES)) {
            return LegacyScreenRole.DETAIL;
        }
        return null;
    }

    private boolean matchesAny(String segment, List<String> suffixes) {
        return suffixes.stream().anyMatch(segment::contains);
    }

    private LegacyScreenRole resolveFromJsp(JspEvidence jsp) {
        if (jsp == null) {
            return null;
        }
        boolean hasEditableFormFields = jsp.forms().stream().anyMatch(this::isDataEntryForm);
        if (hasEditableFormFields) {
            return LegacyScreenRole.FORM;
        }
        if (!jsp.forEachBindings().isEmpty()) {
            return LegacyScreenRole.LIST;
        }
        if (!jsp.displayFields().isEmpty()) {
            return LegacyScreenRole.DETAIL;
        }
        return null;
    }

    /**
     * LIST 화면에도 검색/필터용 `<form>`(대개 GET, {@code modelAttribute} 없음)이 흔히 있어,
     * 필드가 있다는 것만으로는 FORM 화면 신호로 보기에 오탐이 크다. 실제 데이터 입력 폼은
     * eGovFrame 컨벤션상 `<form:form modelAttribute="...">`(Entity VO 바인딩) 또는 POST 제출을
     * 쓰므로, 그 둘 중 하나라도 있어야만 진짜 FORM 신호로 본다.
     */
    private boolean isDataEntryForm(JspFormEvidence form) {
        return !form.fields().isEmpty()
                && (form.modelAttribute() != null || "POST".equals(form.httpMethod()));
    }
}
