package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.ControllerEvidence;
import com.krdevops.springai.model.thymeleaf.ControllerMethodEvidence;
import com.krdevops.springai.model.thymeleaf.JspDisplayFieldEvidence;
import com.krdevops.springai.model.thymeleaf.JspEvidence;
import com.krdevops.springai.model.thymeleaf.JspFormEvidence;
import com.krdevops.springai.model.thymeleaf.JspFormFieldEvidence;
import com.krdevops.springai.model.thymeleaf.JspForEachBindingEvidence;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.ScreenRoleSuggestion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** R6-053: JSP·Controller 증거로부터 화면 유형을 추정하는 결정론적 판정 로직. */
class LegacyScreenRoleResolverTest {

    private final LegacyScreenRoleResolver resolver = new LegacyScreenRoleResolver();

    @Test
    void routeAndJspAgreeingOnListProducesHighConfidence() {
        ScreenRoleSuggestion suggestion = resolver.suggest(jspWithForEach(), controllerWithGetRoute("/emp/employerList.do"));

        assertThat(suggestion.suggestedRole()).isEqualTo(LegacyScreenRole.LIST);
        assertThat(suggestion.confidence()).isEqualTo(0.95);
    }

    @Test
    void routeAndJspAgreeingOnFormProducesHighConfidence() {
        ScreenRoleSuggestion suggestion = resolver.suggest(
                jspWithEditableForm(), controllerWithGetRoute("/emp/employerRegistView.do"));

        assertThat(suggestion.suggestedRole()).isEqualTo(LegacyScreenRole.FORM);
        assertThat(suggestion.confidence()).isEqualTo(0.95);
    }

    @Test
    void routeAndJspAgreeingOnDetailProducesHighConfidence() {
        ScreenRoleSuggestion suggestion = resolver.suggest(
                jspWithDisplayFieldsOnly(), controllerWithGetRoute("/emp/employerDetail.do"));

        assertThat(suggestion.suggestedRole()).isEqualTo(LegacyScreenRole.DETAIL);
        assertThat(suggestion.confidence()).isEqualTo(0.95);
    }

    @Test
    void routeOnlyResolvesWithMediumConfidenceWhenJspIsAmbiguous() {
        ScreenRoleSuggestion suggestion = resolver.suggest(emptyJsp(), controllerWithGetRoute("/emp/employerList.do"));

        assertThat(suggestion.suggestedRole()).isEqualTo(LegacyScreenRole.LIST);
        assertThat(suggestion.confidence()).isEqualTo(0.8);
    }

    @Test
    void jspOnlyResolvesWithMediumConfidenceWhenRouteIsAmbiguous() {
        ScreenRoleSuggestion suggestion = resolver.suggest(jspWithForEach(), controllerWithGetRoute("/emp/employer.do"));

        assertThat(suggestion.suggestedRole()).isEqualTo(LegacyScreenRole.LIST);
        assertThat(suggestion.confidence()).isEqualTo(0.7);
    }

    @Test
    void conflictingSignalsPrefersRouteWithLowConfidence() {
        ScreenRoleSuggestion suggestion = resolver.suggest(
                jspWithEditableForm(), controllerWithGetRoute("/emp/employerList.do"));

        assertThat(suggestion.suggestedRole()).isEqualTo(LegacyScreenRole.LIST);
        assertThat(suggestion.confidence()).isEqualTo(0.5);
    }

    @Test
    void noSignalsProducesUnresolvedZeroConfidence() {
        ScreenRoleSuggestion suggestion = resolver.suggest(emptyJsp(), controllerWithGetRoute("/emp/employer.do"));

        assertThat(suggestion.resolved()).isFalse();
        assertThat(suggestion.confidence()).isEqualTo(0.0);
    }

    @Test
    void postOrRedirectMethodsAreIgnoredForRouteResolution() {
        ControllerEvidence controller = new ControllerEvidence("legacy/C.java", "C", "/emp",
                List.of(new ControllerMethodEvidence(
                        "list", "GET", "/emp/employerList.do", "searchVO", "EmployerVO", false,
                        List.of(), "list", false, List.of(), "C.java:1"),
                        new ControllerMethodEvidence(
                                "regist", "POST", "/emp/employerRegist.do", "vo", "EmployerVO", true,
                                List.of(), "redirect:/emp/employerList.do", true, List.of(), "C.java:2")));

        ScreenRoleSuggestion suggestion = resolver.suggest(emptyJsp(), controller);

        assertThat(suggestion.suggestedRole()).isEqualTo(LegacyScreenRole.LIST);
    }

    private JspEvidence jspWithForEach() {
        return new JspEvidence("legacy/List.jsp", Map.of(), List.of(), List.of(), List.of(),
                List.of(new JspForEachBindingEvidence("item", "resultList", "List.jsp:10")),
                List.of(), List.of());
    }

    private JspEvidence jspWithEditableForm() {
        JspFormEvidence form = new JspFormEvidence("regForm", "employerVO", "employerRegist.do",
                "/emp/employerRegist.do", "POST",
                List.of(new JspFormFieldEvidence("emplyrNm", "form:input", false, false, "Regist.jsp:5")),
                "Regist.jsp:1");
        return new JspEvidence("legacy/Regist.jsp", Map.of(), List.of(), List.of(form), List.of(),
                List.of(), List.of(), List.of());
    }

    private JspEvidence jspWithDisplayFieldsOnly() {
        return new JspEvidence("legacy/Detail.jsp", Map.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new JspDisplayFieldEvidence("result", "emplyrNm", "Detail.jsp:8")), List.of());
    }

    private JspEvidence emptyJsp() {
        return new JspEvidence("legacy/Empty.jsp", Map.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
    }

    private ControllerEvidence controllerWithGetRoute(String route) {
        return new ControllerEvidence("legacy/C.java", "C", "/emp",
                List.of(new ControllerMethodEvidence(
                        "view", "GET", route, "searchVO", "EmployerVO", false,
                        List.of(), "view", false, List.of(), "C.java:1")));
    }
}
