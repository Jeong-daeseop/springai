package com.krdevops.springai.service.security;

import com.krdevops.springai.model.SecuritySpec;
import com.krdevops.springai.service.initializr.VersionCapabilityResolver;
import com.krdevops.springai.service.security.template.SecurityTemplateRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 .tpl 파일 로딩 + 변수 치환 + package 선언 정합성 검증.
 * Critical 버그(package 선언 ↔ 저장 경로 불일치) 재발 방지용.
 */
@SpringBootTest
class SecurityTemplateRendererIntegrationTest {

    @Autowired SecurityTemplateRenderer renderer;
    @Autowired VersionCapabilityResolver resolver;

    private static final String PKG = "egovframework.let.emp";

    // -------------------------------------------------------------------------
    // login-filter.java.tpl — sec.filter
    // -------------------------------------------------------------------------

    @Test
    void loginFilter_packageDeclaration_matchesStoragePath() {
        SecuritySpec spec = spec("loginfilter", "4.3");
        String result = renderer.render("loginfilter", spec);
        assertThat(result).contains("package egovframework.let.emp.sec.filter;");
    }

    // -------------------------------------------------------------------------
    // success-handler / failure-handler — sec.handler (4.3 전용)
    // -------------------------------------------------------------------------

    @Test
    void successHandler_packageDeclaration_matchesStoragePath() {
        SecuritySpec spec = spec("successhandler", "4.3");
        String result = renderer.render("successhandler", spec);
        assertThat(result).contains("package egovframework.let.emp.sec.handler;");
    }

    @Test
    void failureHandler_packageDeclaration_matchesStoragePath() {
        SecuritySpec spec = spec("failurehandler", "4.3");
        String result = renderer.render("failurehandler", spec);
        assertThat(result).contains("package egovframework.let.emp.sec.handler;");
    }

    // -------------------------------------------------------------------------
    // access-denied-handler — sec.handler (4.3 / 5.0)
    // -------------------------------------------------------------------------

    @Test
    void accessDeniedHandler_43_packageDeclaration_matchesStoragePath() {
        SecuritySpec spec = spec("accessdeniedhandler", "4.3");
        String result = renderer.render("accessdeniedhandler", spec);
        assertThat(result).contains("package egovframework.let.emp.sec.handler;");
    }

    @Test
    void accessDeniedHandler_50_packageDeclaration_matchesStoragePath() {
        SecuritySpec spec = spec("accessdeniedhandler", "5.0");
        String result = renderer.render("accessdeniedhandler", spec);
        assertThat(result).contains("package egovframework.let.emp.sec.handler;");
    }

    // -------------------------------------------------------------------------
    // session-mapping.java.tpl — uat.uia.service.impl
    // -------------------------------------------------------------------------

    @Test
    void sessionMapping_packageDeclaration_matchesStoragePath() {
        SecuritySpec spec = spec("sessionmapping", "4.3");
        String result = renderer.render("sessionmapping", spec);
        assertThat(result).contains("package egovframework.let.emp.uat.uia.service.impl;");
    }

    @Test
    void sessionMapping_jdbcMapClass_usesCorrectPackage() {
        SecuritySpec spec = spec("sessionmapping", "4.3");
        String result = renderer.render("sessionmapping", spec);
        assertThat(result).contains("egovframework.let.emp.uat.uia.service.impl.EgovSessionMapping");
    }

    // -------------------------------------------------------------------------
    // java-config.java.tpl (4.3) — import 경로
    // -------------------------------------------------------------------------

    @Test
    void javaConfig_43_imports_useCorrectPackages() {
        SecuritySpec spec = spec("javaconfig", "4.3");
        String result = renderer.render("javaconfig", spec);
        assertThat(result).contains("import egovframework.let.emp.sec.handler.EgovAuthenticationSuccessHandler;");
        assertThat(result).contains("import egovframework.let.emp.sec.handler.EgovAuthenticationFailureHandler;");
        assertThat(result).contains("import egovframework.let.emp.sec.handler.EgovAccessDeniedHandler;");
        assertThat(result).contains("import egovframework.let.emp.sec.service.impl.EgovUserDetailsServiceImpl;");
    }

    // -------------------------------------------------------------------------
    // userDetailsService — 4.3 java / 5.0 notice
    // -------------------------------------------------------------------------

    @Test
    void userDetailsService_43_containsJavaClass() {
        SecuritySpec spec = spec("userdetailsservice", "4.3");
        String result = renderer.render("userdetailsservice", spec);
        assertThat(result).contains("class EgovUserDetailsServiceImpl");
    }

    @Test
    void userDetailsService_50_containsNoticeMessage() {
        SecuritySpec spec = spec("userdetailsservice", "5.0");
        String result = renderer.render("userdetailsservice", spec);
        // 5.0은 안내 메시지 tpl — java class 선언 없음
        assertThat(result).doesNotContain("class EgovUserDetailsServiceImpl");
    }

    // -------------------------------------------------------------------------
    // Fix-1: java-config class명
    // -------------------------------------------------------------------------

    @Test
    void javaConfig_43_classNameMatchesFileName() {
        SecuritySpec spec = spec("javaconfig", "4.3");
        String result = renderer.render("javaconfig", spec);
        assertThat(result).contains("class EgovProjectSecurityConfig");
    }

    // -------------------------------------------------------------------------
    // Fix-2: logout-filter
    // -------------------------------------------------------------------------

    @Test
    void logoutFilter_packageDeclaration_matchesStoragePath() {
        SecuritySpec spec = spec("logoutfilter", "4.3");
        String result = renderer.render("logoutfilter", spec);
        assertThat(result).contains("package egovframework.let.emp.sec.filter;");
    }

    @Test
    void logoutFilter_43_noHardcodedJakarta() {
        SecuritySpec spec = spec("logoutfilter", "4.3");
        String result = renderer.render("logoutfilter", spec);
        assertThat(result).doesNotContain("jakarta.servlet");
        assertThat(result).contains("javax.servlet");
    }

    @Test
    void logoutFilter_50_usesJakarta() {
        SecuritySpec spec = spec("logoutfilter", "5.0");
        String result = renderer.render("logoutfilter", spec);
        assertThat(result).contains("jakarta.servlet");
        assertThat(result).doesNotContain("javax.servlet");
    }

    // -------------------------------------------------------------------------
    // Fix-3: login-policy-filter
    // -------------------------------------------------------------------------

    @Test
    void loginPolicyFilter_packageDeclaration_matchesStoragePath() {
        SecuritySpec spec = spec("loginpolicyfilter", "4.3");
        String result = renderer.render("loginpolicyfilter", spec);
        assertThat(result).contains("package egovframework.let.emp.uat.uap.filter;");
    }

    @Test
    void loginPolicyFilter_43_noHardcodedJakarta() {
        SecuritySpec spec = spec("loginpolicyfilter", "4.3");
        String result = renderer.render("loginpolicyfilter", spec);
        assertThat(result).doesNotContain("jakarta.servlet");
        assertThat(result).contains("javax.servlet");
    }

    // -------------------------------------------------------------------------
    // Fix-4: login-filter
    // -------------------------------------------------------------------------

    @Test
    void loginFilter_43_noHardcodedJakarta() {
        SecuritySpec spec = spec("loginfilter", "4.3");
        String result = renderer.render("loginfilter", spec);
        assertThat(result).doesNotContain("jakarta.servlet");
        assertThat(result).contains("javax.servlet");
    }

    // -------------------------------------------------------------------------
    // Follow-3: role-hierarchy package
    // -------------------------------------------------------------------------

    @Test
    void roleHierarchy_43_packageDeclaration_matchesStoragePath() {
        SecuritySpec spec = spec("rolehierarchy", "4.3");
        String result = renderer.render("rolehierarchy", spec);
        assertThat(result).contains("package egovframework.let.emp.sec.config;");
    }

    @Test
    void roleHierarchy_50_packageDeclaration_matchesStoragePath() {
        SecuritySpec spec = spec("rolehierarchy", "5.0");
        String result = renderer.render("rolehierarchy", spec);
        assertThat(result).contains("package egovframework.let.emp.sec.config;");
    }

    // -------------------------------------------------------------------------
    // Follow-1: roleHierarchy XML Bean 존재 확인
    // -------------------------------------------------------------------------

    @Test
    void contextSecurity_43_containsXmlRoleHierarchyBean() {
        SecuritySpec spec = spec("contextsecurity", "4.3");
        String xml = renderer.render("contextsecurity", spec);
        assertThat(xml).contains("<beans:bean id=\"roleHierarchy\"");
    }

    // -------------------------------------------------------------------------
    // Follow-2: loginFilter DelegatingFilterProxy + Bean 등록
    // -------------------------------------------------------------------------

    @Test
    void webXml_loginFilter_usesDelegatingFilterProxy() {
        SecuritySpec spec = spec("webxmlfilter", "4.3");
        String webXml = renderer.render("webxmlfilter", spec);
        assertThat(webXml).contains("org.springframework.web.filter.DelegatingFilterProxy");
        assertThat(webXml).contains("<param-value>egovSpringSecurityLoginFilter</param-value>");
    }

    @Test
    void contextSecurity_43_registersLoginFilterBean() {
        SecuritySpec spec = spec("contextsecurity", "4.3");
        String xml = renderer.render("contextsecurity", spec);
        assertThat(xml).contains("id=\"egovSpringSecurityLoginFilter\"");
        assertThat(xml).contains("egovframework.let.emp.sec.filter.EgovSpringSecurityLoginFilter");
    }

    // -------------------------------------------------------------------------
    // Fix-8: user-details-service
    // -------------------------------------------------------------------------

    @Test
    void userDetailsService_43_packageDeclaration_matchesStoragePath() {
        SecuritySpec spec = spec("userdetailsservice", "4.3");
        String result = renderer.render("userdetailsservice", spec);
        assertThat(result).contains("package egovframework.let.emp.sec.service.impl;");
    }

    // -------------------------------------------------------------------------
    // 생성물 간 연결 검증 (Fix-5, Fix-6, Fix-7)
    // -------------------------------------------------------------------------

    @Test
    void webXmlFilter_loginFilter_usesDelegatingProxyLinkedToBean() {
        // loginFilter는 생성자 주입 때문에 filter-class 직접 등록 불가.
        // web.xml → DelegatingFilterProxy + targetBeanName으로 연결되고
        // Bean FQCN 검증은 contextSecurity_43_registersLoginFilterBean()에서 수행.
        SecuritySpec spec = spec("webxmlfilter", "4.3");
        String webXml = renderer.render("webxmlfilter", spec);
        assertThat(webXml).contains("org.springframework.web.filter.DelegatingFilterProxy");
        assertThat(webXml).contains("egovSpringSecurityLoginFilter");
    }

    @Test
    void webXmlFilter_logoutFilterClass_matchesLogoutFilterPackage() {
        SecuritySpec spec = spec("webxmlfilter", "4.3");
        String webXml = renderer.render("webxmlfilter", spec);
        String logoutFqcn = extractFilterClass(webXml, "EgovSpringSecurityLogoutFilter");
        String logoutFilter = renderer.render("logoutfilter", spec);
        String logoutPkg = extractPackage(logoutFilter);
        assertThat(logoutFqcn).startsWith(logoutPkg);
        assertThat(logoutFqcn).endsWith("EgovSpringSecurityLogoutFilter");
    }

    @Test
    void webXmlFilter_loginPolicyFilterClass_matchesLoginPolicyFilterPackage() {
        SecuritySpec spec = spec("webxmlfilter", "4.3");
        String webXml = renderer.render("webxmlfilter", spec);
        String policyFqcn = extractFilterClass(webXml, "EgovLoginPolicyFilter");
        String policyFilter = renderer.render("loginpolicyfilter", spec);
        String policyPkg = extractPackage(policyFilter);
        assertThat(policyFqcn).startsWith(policyPkg);
        assertThat(policyFqcn).endsWith("EgovLoginPolicyFilter");
    }

    @Test
    void contextSecurity_43_jdbcMapClass_matchesSessionMappingPackage() {
        SecuritySpec spec = spec("contextsecurity", "4.3");
        String contextXml = renderer.render("contextsecurity", spec);
        String jdbcMapClass = extractJdbcMapClass(contextXml);
        String sessionMapping = renderer.render("sessionmapping", spec);
        String sessionPkg = extractPackage(sessionMapping);
        assertThat(jdbcMapClass).startsWith(sessionPkg);
        assertThat(jdbcMapClass).endsWith("EgovSessionMapping");
    }

    @Test
    void contextSecurity_50_jdbcMapClass_matchesSessionMappingPackage() {
        SecuritySpec spec = spec("contextsecurity", "5.0");
        String contextXml = renderer.render("contextsecurity", spec);
        String jdbcMapClass = extractJdbcMapClass(contextXml);
        String sessionMapping = renderer.render("sessionmapping", spec);
        String sessionPkg = extractPackage(sessionMapping);
        assertThat(jdbcMapClass).startsWith(sessionPkg);
        assertThat(jdbcMapClass).endsWith("EgovSessionMapping");
    }

    // -------------------------------------------------------------------------
    // webXmlFilter 5.0 미지원
    // -------------------------------------------------------------------------

    @Test
    void webXmlFilter_withEgov50_throwsIllegalArgumentException() {
        SecuritySpec spec = spec("webxmlfilter", "5.0");
        assertThatThrownBy(() -> renderer.render("webxmlfilter", spec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4.3")
                .hasMessageContaining("setup-war-50");
    }

    // -------------------------------------------------------------------------
    // helper
    // -------------------------------------------------------------------------

    private SecuritySpec spec(String type, String version) {
        return SecuritySpec.of(type, PKG, "war", null, resolver.resolve(version));
    }

    /** "package foo.bar.baz;" → "foo.bar.baz" */
    private static String extractPackage(String source) {
        return source.lines()
                .filter(l -> l.trim().startsWith("package "))
                .findFirst()
                .map(l -> l.trim().replace("package ", "").replace(";", "").trim())
                .orElseThrow(() -> new AssertionError("package 선언을 찾을 수 없습니다"));
    }

    /**
     * web.xml 렌더링 결과에서 특정 클래스명을 포함하는 FQCN 줄을 반환한다.
     * 소문자로 시작하는 줄만 대상으로 삼아 주석 행(⑥ 등)을 제외한다.
     */
    private static String extractFilterClass(String webXml, String className) {
        return webXml.lines()
                .map(String::trim)
                .filter(l -> l.contains(className)
                        && !l.isEmpty()
                        && Character.isLowerCase(l.charAt(0)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("filter-class에서 " + className + "을 찾을 수 없습니다"));
    }

    /**
     * context-security.xml 렌더링 결과에서 EgovSessionMapping FQCN을 추출한다.
     * egov43: jdbcMapClass="..." / egov50: value="..." 두 패턴 모두 지원.
     * FQCN은 소문자로 시작하므로 [a-z] 앵커로 주석·키 이름과 구분한다.
     */
    private static String extractJdbcMapClass(String xml) {
        Matcher m = Pattern.compile("(?:jdbcMapClass|value)=\"([a-z][^\"]*EgovSessionMapping[^\"]*)\"")
                .matcher(xml);
        if (m.find()) return m.group(1);
        throw new AssertionError("jdbcMapClass에서 EgovSessionMapping을 찾을 수 없습니다");
    }
}
