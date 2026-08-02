package com.krdevops.springai.config;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
            FigmaDesignOrchestrationTool figmaDesignOrchestrationTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(
                        dateTimeTool, designReferenceTool, employeeTool, schemaReaderTool, codeSaverTool,
                        codeTemplateTool, ragTool, generationHistoryTool, codeValidatorTool,
                        projectScannerTool, commonCodeTool, workflowGuideTool,
                        crudGenerationTool, boardGenerationTool, masterDetailGenerationTool, joinQueryTool,
                        crudScreenSourceTool, boardScreenSourceTool, masterDetailScreenSourceTool,
                        projectHealthTool, projectInitializrTool, menuTool, authTool,
                        securityTemplateTool, sqlTool, outputPathResolverTool, thymeleafLayoutTool,
                        captureWebPageTool, designArtifactTool, figmaExportTool, designSystemTool,
                        figmaDesignOrchestrationTool)
                .build();
    }
}
