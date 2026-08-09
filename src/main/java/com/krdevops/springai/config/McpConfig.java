package com.krdevops.springai.config;

import com.krdevops.springai.config.mcp.McpAuthorizingToolCallback;
import com.krdevops.springai.config.mcp.McpToolRiskAnnotationResolver;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;
import com.krdevops.springai.config.mcp.ToolAuthorizationPolicy;
import com.krdevops.springai.config.mcp.McpSensitiveDataRedactor;
import com.krdevops.springai.tools.AuthTool;
import com.krdevops.springai.tools.OutputPathResolverTool;
import com.krdevops.springai.tools.SqlTool;
import com.krdevops.springai.tools.CodeSaverTool;
import com.krdevops.springai.tools.SecurityTemplateTool;
import com.krdevops.springai.tools.CodeTemplateTool;
import com.krdevops.springai.tools.CodeValidatorTool;
import com.krdevops.springai.tools.CommonCodeTool;
import com.krdevops.springai.tools.generation.BoardGenerationTool;
import com.krdevops.springai.tools.generation.BoardScreenSourceTool;
import com.krdevops.springai.tools.generation.CrudGenerationTool;
import com.krdevops.springai.tools.generation.CrudScreenSourceTool;
import com.krdevops.springai.tools.generation.JoinQueryTool;
import com.krdevops.springai.tools.generation.MasterDetailGenerationTool;
import com.krdevops.springai.tools.generation.MasterDetailScreenSourceTool;
import com.krdevops.springai.tools.DateTimeTool;
import com.krdevops.springai.tools.DesignReferenceTool;
import com.krdevops.springai.tools.EmployeeTool;
import com.krdevops.springai.tools.GenerationHistoryTool;
import com.krdevops.springai.tools.ProjectHealthTool;
import com.krdevops.springai.tools.ProjectInitializrTool;
import com.krdevops.springai.tools.ProjectScannerTool;
import com.krdevops.springai.tools.RagTool;
import com.krdevops.springai.tools.SchemaReaderTool;
import com.krdevops.springai.tools.MenuTool;
import com.krdevops.springai.tools.ThymeleafLayoutTool;
import com.krdevops.springai.tools.WorkflowGuideTool;
import com.krdevops.springai.tools.CaptureWebPageTool;
import com.krdevops.springai.tools.DesignArtifactTool;
import com.krdevops.springai.tools.DesignSystemTool;
import com.krdevops.springai.tools.FigmaExportTool;
import com.krdevops.springai.tools.FigmaDesignOrchestrationTool;
import com.krdevops.springai.tools.FigmaApprovedSpecificationTool;
import com.krdevops.springai.tools.ThymeleafBaselineApprovalTool;
import com.krdevops.springai.tools.ThymeleafBindingGenerationTool;
import com.krdevops.springai.tools.ThymeleafProjectWorkflowTool;
import com.krdevops.springai.service.observability.OperationalTelemetry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * {@code McpSecurityProperties}는 {@code SpringaiApplication}의 {@code @ConfigurationPropertiesScan}으로
 * 자동 등록된다(별도 {@code @EnableConfigurationProperties} 불필요).
 */
@Configuration
public class McpConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    public ToolCallbackProvider allToolCallbacks(
            DateTimeTool dateTimeTool,
            DesignReferenceTool designReferenceTool,
            EmployeeTool employeeTool,
            SchemaReaderTool schemaReaderTool,
            CodeSaverTool codeSaverTool,
            CodeTemplateTool codeTemplateTool,
            RagTool ragTool,
            GenerationHistoryTool generationHistoryTool,
            CodeValidatorTool codeValidatorTool,
            ProjectScannerTool projectScannerTool,
            CommonCodeTool commonCodeTool,
            WorkflowGuideTool workflowGuideTool,
            CrudGenerationTool crudGenerationTool,
            BoardGenerationTool boardGenerationTool,
            MasterDetailGenerationTool masterDetailGenerationTool,
            JoinQueryTool joinQueryTool,
            CrudScreenSourceTool crudScreenSourceTool,
            BoardScreenSourceTool boardScreenSourceTool,
            MasterDetailScreenSourceTool masterDetailScreenSourceTool,
            ProjectHealthTool projectHealthTool,
            ProjectInitializrTool projectInitializrTool,
            MenuTool menuTool,
            AuthTool authTool,
            SecurityTemplateTool securityTemplateTool,
            SqlTool sqlTool,
            OutputPathResolverTool outputPathResolverTool,
            ThymeleafLayoutTool thymeleafLayoutTool,
            CaptureWebPageTool captureWebPageTool,
            DesignArtifactTool designArtifactTool,
            FigmaExportTool figmaExportTool,
            DesignSystemTool designSystemTool,
            FigmaDesignOrchestrationTool figmaDesignOrchestrationTool,
            FigmaApprovedSpecificationTool figmaApprovedSpecificationTool,
            ThymeleafBindingGenerationTool thymeleafBindingGenerationTool,
            ThymeleafProjectWorkflowTool thymeleafProjectWorkflowTool,
            ThymeleafBaselineApprovalTool thymeleafBaselineApprovalTool,
            McpToolRiskAnnotationResolver riskResolver,
            ToolAuthorizationPolicy authorizationPolicy,
            McpSensitiveDataRedactor redactor,
            OperationalTelemetry telemetry) {
        ToolCallbackProvider rawProvider = MethodToolCallbackProvider.builder()
                .toolObjects(
                        dateTimeTool, designReferenceTool, employeeTool, schemaReaderTool, codeSaverTool,
                        codeTemplateTool, ragTool, generationHistoryTool, codeValidatorTool,
                        projectScannerTool, commonCodeTool, workflowGuideTool,
                        crudGenerationTool, boardGenerationTool, masterDetailGenerationTool, joinQueryTool,
                        crudScreenSourceTool, boardScreenSourceTool, masterDetailScreenSourceTool,
                        projectHealthTool, projectInitializrTool, menuTool, authTool,
                        securityTemplateTool, sqlTool, outputPathResolverTool, thymeleafLayoutTool,
                        captureWebPageTool, designArtifactTool, figmaExportTool, designSystemTool,
                        figmaDesignOrchestrationTool,
                        figmaApprovedSpecificationTool, thymeleafBindingGenerationTool,
                        thymeleafProjectWorkflowTool,
                        thymeleafBaselineApprovalTool)
                .build();

        // ARCH-0108/ARCH-0103: 모든 등록 Tool을 ToolAuthorizationPolicy로 감싸고,
        // 대상 메서드에 @McpToolRisk가 없으면 기동을 즉시 차단한다.
        ToolCallback[] raw = rawProvider.getToolCallbacks();
        ToolCallback[] authorized = new ToolCallback[raw.length];
        for (int i = 0; i < raw.length; i++) {
            ToolCallback callback = raw[i];
            Method toolMethod = resolveToolMethod(callback);
            McpToolRiskLevel riskLevel = riskResolver.resolve(toolMethod);
            authorized[i] = new McpAuthorizingToolCallback(
                    callback, riskLevel, authorizationPolicy, redactor, telemetry);
        }
        return ToolCallbackProvider.from(authorized);
    }

    /**
     * {@code McpToolDefinitionSnapshotTest}와 동일한 리플렉션 패턴 — {@code MethodToolCallback}이
     * 실제 {@code @Tool} 메서드를 담아두는 private {@code toolMethod} 필드를 읽는다.
     */
    private static Method resolveToolMethod(ToolCallback callback) {
        if (!(callback instanceof MethodToolCallback)) {
            throw new IllegalStateException(
                    "예상치 못한 ToolCallback 구현체: " + callback.getClass()
                    + " — McpConfig가 MethodToolCallbackProvider만 사용한다는 전제가 깨졌습니다.");
        }
        try {
            Field field = MethodToolCallback.class.getDeclaredField("toolMethod");
            field.setAccessible(true);
            return (Method) field.get(callback);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("MethodToolCallback의 toolMethod 필드를 읽을 수 없습니다.", e);
        }
    }
}
