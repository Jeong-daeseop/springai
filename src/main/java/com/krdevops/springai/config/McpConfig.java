package com.krdevops.springai.config;

import com.krdevops.springai.tools.AuthTool;
import com.krdevops.springai.tools.OutputPathResolverTool;
import com.krdevops.springai.tools.SqlTool;
import com.krdevops.springai.tools.CodeSaverTool;
import com.krdevops.springai.tools.SecurityTemplateTool;
import com.krdevops.springai.tools.CodeTemplateTool;
import com.krdevops.springai.tools.CodeValidatorTool;
import com.krdevops.springai.tools.CommonCodeTool;
import com.krdevops.springai.tools.CrudPromptBuilderTool;
import com.krdevops.springai.tools.DateTimeTool;
import com.krdevops.springai.tools.EmployeeTool;
import com.krdevops.springai.tools.GenerationHistoryTool;
import com.krdevops.springai.tools.ProjectHealthTool;
import com.krdevops.springai.tools.ProjectInitializrTool;
import com.krdevops.springai.tools.ProjectScannerTool;
import com.krdevops.springai.tools.RagTool;
import com.krdevops.springai.tools.SchemaReaderTool;
import com.krdevops.springai.tools.MenuTool;
import com.krdevops.springai.tools.WorkflowGuideTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public ToolCallbackProvider dateTimeToolCallbacks(DateTimeTool dateTimeTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(dateTimeTool)
                .build();
    }

    @Bean
    public ToolCallbackProvider employeeToolCallbacks(EmployeeTool employeeTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(employeeTool)
                .build();
    }

    @Bean
    public ToolCallbackProvider schemaReaderToolCallbacks(SchemaReaderTool schemaReaderTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(schemaReaderTool)
                .build();
    }

    @Bean
    public ToolCallbackProvider codeSaverToolCallbacks(CodeSaverTool codeSaverTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(codeSaverTool)
                .build();
    }

    @Bean
    public ToolCallbackProvider codeTemplateToolCallbacks(CodeTemplateTool codeTemplateTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(codeTemplateTool)
                .build();
    }

    @Bean
    public ToolCallbackProvider ragToolCallbacks(RagTool ragTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(ragTool)
                .build();
    }

    @Bean
    public ToolCallbackProvider generationHistoryToolCallbacks(GenerationHistoryTool generationHistoryTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(generationHistoryTool)
                .build();
    }

    @Bean
    public ToolCallbackProvider codeValidatorToolCallbacks(CodeValidatorTool codeValidatorTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(codeValidatorTool)
                .build();
    }

    @Bean
    public ToolCallbackProvider projectScannerToolCallbacks(ProjectScannerTool projectScannerTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(projectScannerTool)
                .build();
    }

    @Bean
    public ToolCallbackProvider commonCodeToolCallbacks(CommonCodeTool commonCodeTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(commonCodeTool)
                .build();
    }

    @Bean
    public ToolCallbackProvider workflowGuideToolCallbacks(WorkflowGuideTool workflowGuideTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(workflowGuideTool)
                .build();
    }

    @Bean
    public ToolCallbackProvider crudPromptBuilderToolCallbacks(CrudPromptBuilderTool crudPromptBuilderTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(crudPromptBuilderTool)
                .build();
    }

    @Bean
    public ToolCallbackProvider projectHealthToolCallbacks(ProjectHealthTool projectHealthTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(projectHealthTool)
                .build();
    }

    @Bean
    public ToolCallbackProvider projectInitializrToolCallbacks(ProjectInitializrTool projectInitializrTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(projectInitializrTool)
                .build();
    }

    @Bean
    public ToolCallbackProvider menuToolCallbacks(MenuTool menuTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(menuTool)
                .build();
    }

    @Bean
    public ToolCallbackProvider authToolCallbacks(AuthTool authTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(authTool)
                .build();
    }

    @Bean
    public ToolCallbackProvider securityTemplateToolCallbacks(SecurityTemplateTool securityTemplateTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(securityTemplateTool)
                .build();
    }

    @Bean
    public ToolCallbackProvider sqlToolCallbacks(SqlTool sqlTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(sqlTool)
                .build();
    }

    @Bean
    public ToolCallbackProvider outputPathResolverToolCallbacks(OutputPathResolverTool outputPathResolverTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(outputPathResolverTool)
                .build();
    }
}
