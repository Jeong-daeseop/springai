package com.krdevops.springai.service;

import com.krdevops.springai.model.FilePlan;
import com.krdevops.springai.model.GenerationReport;
import com.krdevops.springai.model.SecuritySpec;
import com.krdevops.springai.service.initializr.FilePlanExecutor;
import com.krdevops.springai.service.initializr.VersionCapabilityResolver;
import com.krdevops.springai.service.security.SecurityFilePlanFactory;
import com.krdevops.springai.service.security.SecurityResultBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityTemplateServiceTest {

    @Spy
    VersionCapabilityResolver resolver = new VersionCapabilityResolver();

    @Mock
    SecurityFilePlanFactory factory;

    @Mock
    FilePlanExecutor executor;

    @Mock
    SecurityResultBuilder resultBuilder;

    @InjectMocks
    SecurityTemplateService service;

    @Test
    void getSecurityTemplate_3arg_delegatesToFactory() {
        when(factory.renderSingle(any())).thenReturn("TEMPLATE_CONTENT");

        String result = service.getSecurityTemplate("webXmlFilter", "egovframework.let.sample", "4.3");

        assertThat(result).isEqualTo("TEMPLATE_CONTENT");
        verify(factory).renderSingle(any(SecuritySpec.class));
    }

    @Test
    void getSecurityTemplate_withoutOutputPath_returnsRenderedString() {
        when(factory.renderSingle(any())).thenReturn("RENDERED");

        String result = service.getSecurityTemplate("javaConfig", "egovframework.let.emp", "5.0", null, "war");

        assertThat(result).isEqualTo("RENDERED");
        verify(executor, never()).execute(any(Path.class), anyList());
    }

    @Test
    void getSecurityTemplate_withOutputPath_callsExecutorAndResultBuilder() {
        List<FilePlan> plans = List.of();
        GenerationReport report = new GenerationReport("/tmp/test");
        when(factory.plan(any())).thenReturn(plans);
        when(executor.execute(any(Path.class), anyList())).thenReturn(report);
        when(resultBuilder.build(report)).thenReturn("저장 완료");

        String result = service.getSecurityTemplate("javaConfig", "egovframework.let.emp", "4.3", "/tmp/test", "war");

        assertThat(result).isEqualTo("저장 완료");
        verify(factory).plan(any(SecuritySpec.class));
        verify(executor).execute(any(Path.class), anyList());
        verify(resultBuilder).build(report);
    }

    @Test
    void getSecurityTemplate_unsupportedType_returnsHelpMessage() {
        when(factory.renderSingle(any())).thenThrow(new IllegalArgumentException("지원하지 않는 securityType: unknown"));

        String result = service.getSecurityTemplate("unknown", "egovframework.let.sample", "5.0");

        assertThat(result).contains("지원하지 않는 securityType");
        assertThat(result).contains("webXmlFilter");
    }

    @Test
    void getSecurityTemplate_withOutputPath_unsupportedType_returnsHelpMessage() {
        when(factory.plan(any())).thenThrow(new IllegalArgumentException("지원하지 않는 securityType: badType"));

        String result = service.getSecurityTemplate("badType", "egovframework.let.sample", "5.0", "/tmp/test", "war");

        assertThat(result).contains("지원하지 않는 securityType");
        verify(executor, never()).execute(any(Path.class), anyList());
    }

    @Test
    void getSecurityTemplate_webXmlFilter_50_returnsVersionMismatchMessage() {
        // 버전 불일치 예외는 unsupported() 도움말로 덮지 않고 메시지 그대로 반환
        when(factory.renderSingle(any())).thenThrow(new IllegalArgumentException(
                "webXmlFilter는 eGovFrame 4.3 WAR XML Security 전용입니다. 5.0에서는 setup-war-50을 사용하세요."));

        String result = service.getSecurityTemplate("webXmlFilter", "egovframework.let.emp", "5.0");

        assertThat(result).contains("4.3");
        assertThat(result).contains("setup-war-50");
        assertThat(result).doesNotContain("사용 가능한 securityType 목록");
    }

    @Test
    void getSecurityTemplate_webXmlFilter_50_withOutputPath_returnsVersionMismatchMessage() {
        // 파일 저장 경로에서도 버전 불일치 메시지 그대로 반환
        when(factory.plan(any())).thenThrow(new IllegalArgumentException(
                "webXmlFilter는 eGovFrame 4.3 WAR XML Security 전용입니다. 5.0에서는 setup-war-50을 사용하세요."));

        String result = service.getSecurityTemplate("webXmlFilter", "egovframework.let.emp", "5.0", "/tmp/test", "war");

        assertThat(result).contains("4.3");
        assertThat(result).contains("setup-war-50");
        assertThat(result).doesNotContain("사용 가능한 securityType 목록");
        verify(executor, never()).execute(any(Path.class), anyList());
    }
}
