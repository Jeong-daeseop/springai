package com.krdevops.springai.service.security;

import com.krdevops.springai.model.FilePlan;
import com.krdevops.springai.model.SecuritySpec;
import com.krdevops.springai.service.initializr.VersionCapabilityResolver;
import com.krdevops.springai.service.security.template.SecurityTemplateRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityFilePlanFactoryTest {

    @Mock
    SecurityTemplateRenderer renderer;

    @InjectMocks
    SecurityFilePlanFactory factory;

    VersionCapabilityResolver resolver = new VersionCapabilityResolver();

    SecuritySpec spec43;
    SecuritySpec spec50;

    @BeforeEach
    void setUp() {
        spec43 = SecuritySpec.of("javaConfig", "egovframework.let.sample", "war", null, resolver.resolve("4.3"));
        spec50 = SecuritySpec.of("javaConfig", "egovframework.let.sample", "war", null, resolver.resolve("5.0"));
        when(renderer.render(anyString(), any())).thenReturn("RENDERED");
    }

    // -------------------------------------------------------------------------
    // expand() 테스트
    // -------------------------------------------------------------------------

    @Test
    void expand_singleType_returnsListOfOne() {
        List<String> result = factory.expand("webXmlFilter", resolver.resolve("4.3"));
        assertThat(result).containsExactly("webxmlfilter");
    }

    @Test
    void expand_setupWar43_returns9Types() {
        // setup-war-43 은 setup-war-43-xml의 alias — XML Security 방식
        // 필터 구현체 3종(loginfilter/logoutfilter/loginpolicyfilter) 포함:
        // web.xml.fragment가 이 클래스를 참조하므로 조합 내 참조가 완결되어야 함
        List<String> result = factory.expand("setup-war-43", resolver.resolve("4.3"));
        assertThat(result).hasSize(9);
        assertThat(result).containsSubsequence("webxmlfilter", "contextsecurity", "userdetailsservice");
        assertThat(result).contains("loginfilter", "logoutfilter", "loginpolicyfilter");
        assertThat(result).doesNotContain("javaconfig");
        assertThat(result).doesNotContain("rolehierarchy");
    }

    @Test
    void expand_setupWar50_returns5Types() {
        List<String> result = factory.expand("setup-war-50", resolver.resolve("5.0"));
        assertThat(result).hasSize(5);
        assertThat(result).doesNotContain("webxmlfilter");
        assertThat(result).doesNotContain("userdetailsservice");
    }

    @Test
    void expand_setupFilters_returns4Types() {
        List<String> result = factory.expand("setup-filters", resolver.resolve("5.0"));
        assertThat(result).containsExactly(
                "loginfilter", "logoutfilter", "loginpolicyfilter", "sessionmapping");
    }

    @Test
    void expand_setupHandlers43_returns3Types() {
        List<String> result = factory.expand("setup-handlers-43", resolver.resolve("4.3"));
        assertThat(result).containsExactly(
                "successhandler", "failurehandler", "accessdeniedhandler");
    }

    @Test
    void expand_setupAllWar43_contains10UniqueTypes() {
        List<String> result = factory.expand("setup-all-war-43", resolver.resolve("4.3"));
        // 9 (war43Xml — 필터 3종 포함) + 1 (securityMapper) = 10
        assertThat(result).hasSize(10);
        assertThat(result).doesNotHaveDuplicates();
        assertThat(result).contains("securitymapper");
        assertThat(result).contains("loginfilter", "logoutfilter", "loginpolicyfilter");
        assertThat(result).doesNotContain("javaconfig");
        assertThat(result).doesNotContain("rolehierarchy");
    }

    @Test
    void expand_setupAllWar50_contains11UniqueTypes() {
        List<String> result = factory.expand("setup-all-war-50", resolver.resolve("5.0"));
        // 5 (war50) + 4 (filters) + 1 (accessDenied) + 1 (mapper) = 11
        assertThat(result).hasSize(11);
        assertThat(result).contains("securitymapper", "accessdeniedhandler");
    }

    @Test
    void expand_setupWar43Xml_doesNotContainRoleHierarchy() {
        // XML Security 조합에서 roleHierarchy Java Config 미포함 검증
        // (context-security.xml이 roleHierarchy Bean을 XML로 직접 선언 — Bean 중복 방지)
        List<String> result = factory.expand("setup-war-43-xml", resolver.resolve("4.3"));
        assertThat(result).doesNotContain("rolehierarchy");
    }

    @Test
    void expand_setupAllWar43Xml_returnsDistinctTypesOf10() {
        List<String> result = factory.expand("setup-all-war-43-xml", resolver.resolve("4.3"));
        assertThat(result).doesNotHaveDuplicates();
        assertThat(result).hasSize(10);
    }

    @Test
    void expand_setupWar43Xml_doesNotContainJavaConfig() {
        List<String> result = factory.expand("setup-war-43-xml", resolver.resolve("4.3"));
        assertThat(result).doesNotContain("javaconfig");
        assertThat(result).contains("contextsecurity");
    }

    @Test
    void expand_setupWar43Java_doesNotContainContextSecurity() {
        List<String> result = factory.expand("setup-war-43-java", resolver.resolve("4.3"));
        assertThat(result).doesNotContain("contextsecurity");
        assertThat(result).contains("javaconfig");
    }

    @Test
    void expand_setupWar43Xml_containsSessionMapping() {
        List<String> result = factory.expand("setup-war-43-xml", resolver.resolve("4.3"));
        assertThat(result).contains("sessionmapping");
    }

    @Test
    void expand_setupWar43WithVersion50_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> factory.expand("setup-war-43", resolver.resolve("5.0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("setup-war-43");
    }

    @Test
    void expand_setupWar43XmlWithVersion50_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> factory.expand("setup-war-43-xml", resolver.resolve("5.0")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expand_setupAllWar43XmlWithVersion50_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> factory.expand("setup-all-war-43-xml", resolver.resolve("5.0")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expand_setupWar50WithVersion43_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> factory.expand("setup-war-50", resolver.resolve("4.3")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("setup-war-50");
    }

    // -------------------------------------------------------------------------
    // plan() 테스트
    // -------------------------------------------------------------------------

    @Test
    void plan_singleType_returnsOneFilePlan() {
        SecuritySpec s = SecuritySpec.of("webXmlFilter", "egovframework.let.sample", "war", null, resolver.resolve("4.3"));
        List<FilePlan> plans = factory.plan(s);
        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).relativePath()).isEqualTo("src/main/webapp/WEB-INF/web.xml.fragment");
    }

    @Test
    void plan_setupWar43_noDuplicatePaths() {
        SecuritySpec s = SecuritySpec.of("setup-war-43", "egovframework.let.sample", "war", null, resolver.resolve("4.3"));
        List<FilePlan> plans = factory.plan(s);
        long distinctPaths = plans.stream().map(FilePlan::relativePath).distinct().count();
        assertThat(distinctPaths).isEqualTo(plans.size());
    }

    @Test
    void expand_setupWar43Xml_containsAllWebXmlReferencedFilters() {
        // web.xml.fragment가 참조하는 필터 구현체 3종이 조합에 포함되어야 함
        // 미포함 시 런타임 ClassNotFoundException 발생
        List<String> result = factory.expand("setup-war-43-xml", resolver.resolve("4.3"));
        assertThat(result).contains("loginfilter", "logoutfilter", "loginpolicyfilter");
    }

    @Test
    void expand_setupAllWar43Xml_containsAllWebXmlReferencedFilters() {
        List<String> result = factory.expand("setup-all-war-43-xml", resolver.resolve("4.3"));
        assertThat(result).contains("loginfilter", "logoutfilter", "loginpolicyfilter");
        assertThat(result).contains("securitymapper");
    }

    @Test
    void plan_setupWar43Xml_containsAllWebXmlReferencedFilterPaths() {
        // web.xml.fragment가 참조하는 필터 구현체 3종의 실제 FilePlan 경로 존재 검증
        SecuritySpec s = SecuritySpec.of("setup-war-43-xml", "egovframework.let.emp", "war", null, resolver.resolve("4.3"));
        List<FilePlan> plans = factory.plan(s);
        assertThat(plans)
                .extracting(FilePlan::relativePath)
                .contains(
                        "src/main/java/egovframework/let/emp/sec/filter/EgovSpringSecurityLoginFilter.java",
                        "src/main/java/egovframework/let/emp/sec/filter/EgovSpringSecurityLogoutFilter.java",
                        "src/main/java/egovframework/let/emp/uat/uap/filter/EgovLoginPolicyFilter.java"
                );
    }

    @Test
    void plan_unsupportedType_throwsIllegalArgumentException() {
        SecuritySpec s = SecuritySpec.of("unknownType", "egovframework.let.sample", "war", null, resolver.resolve("5.0"));
        assertThatThrownBy(() -> factory.plan(s))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknowntype");
    }

    // -------------------------------------------------------------------------
    // toPlan() 테스트
    // -------------------------------------------------------------------------

    @Test
    void toPlan_webXmlFilter_correctPath() {
        FilePlan fp = factory.toPlan("webxmlfilter", spec43);
        assertThat(fp.relativePath()).isEqualTo("src/main/webapp/WEB-INF/web.xml.fragment");
        assertThat(fp.kind()).isEqualTo(FilePlan.FileKind.WEB);
    }

    @Test
    void toPlan_javaConfig_usesPackagePath() {
        SecuritySpec s = SecuritySpec.of("javaConfig", "egovframework.let.cmm", "war", null, resolver.resolve("4.3"));
        FilePlan fp = factory.toPlan("javaconfig", s);
        assertThat(fp.relativePath())
                .isEqualTo("src/main/java/egovframework/let/cmm/config/EgovProjectSecurityConfig.java");
    }

    @Test
    void toPlan_loginFilter_correctPath() {
        FilePlan fp = factory.toPlan("loginfilter", spec43);
        assertThat(fp.relativePath())
                .contains("sec/filter/EgovSpringSecurityLoginFilter.java");
    }

    // -------------------------------------------------------------------------
    // renderSingle() 테스트
    // -------------------------------------------------------------------------

    @Test
    void renderSingle_delegatesToRenderer() {
        SecuritySpec s = SecuritySpec.of("webXmlFilter", "egovframework.let.sample", "war", null, resolver.resolve("4.3"));
        String result = factory.renderSingle(s);
        assertThat(result).isEqualTo("RENDERED");
        verify(renderer).render(eq("webXmlFilter"), eq(s));
    }
}
