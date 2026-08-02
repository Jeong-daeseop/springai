package com.krdevops.springai.config.mcp;

import com.krdevops.springai.tools.AuthTool;
import com.krdevops.springai.tools.CaptureWebPageTool;
import com.krdevops.springai.tools.CodeSaverTool;
import com.krdevops.springai.tools.CodeTemplateTool;
import com.krdevops.springai.tools.CodeValidatorTool;
import com.krdevops.springai.tools.CommonCodeTool;
import com.krdevops.springai.tools.DateTimeTool;
import com.krdevops.springai.tools.DesignArtifactTool;
import com.krdevops.springai.tools.DesignReferenceTool;
import com.krdevops.springai.tools.DesignSystemTool;
import com.krdevops.springai.tools.EmployeeTool;
import com.krdevops.springai.tools.FigmaApprovedSpecificationTool;
import com.krdevops.springai.tools.FigmaDesignOrchestrationTool;
import com.krdevops.springai.tools.FigmaExportTool;
import com.krdevops.springai.tools.GenerationHistoryTool;
import com.krdevops.springai.tools.MenuTool;
import com.krdevops.springai.tools.OutputPathResolverTool;
import com.krdevops.springai.tools.ProjectHealthTool;
import com.krdevops.springai.tools.ProjectInitializrTool;
import com.krdevops.springai.tools.ProjectScannerTool;
import com.krdevops.springai.tools.RagTool;
import com.krdevops.springai.tools.SchemaReaderTool;
import com.krdevops.springai.tools.SecurityTemplateTool;
import com.krdevops.springai.tools.SqlTool;
import com.krdevops.springai.tools.ThymeleafLayoutTool;
import com.krdevops.springai.tools.ThymeleafProjectWorkflowTool;
import com.krdevops.springai.tools.WorkflowGuideTool;
import com.krdevops.springai.tools.generation.BoardGenerationTool;
import com.krdevops.springai.tools.generation.BoardScreenSourceTool;
import com.krdevops.springai.tools.generation.CrudGenerationTool;
import com.krdevops.springai.tools.generation.CrudScreenSourceTool;
import com.krdevops.springai.tools.generation.JoinQueryTool;
import com.krdevops.springai.tools.generation.MasterDetailGenerationTool;
import com.krdevops.springai.tools.generation.MasterDetailScreenSourceTool;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.krdevops.springai.config.mcp.McpToolRiskLevel.APPLY;
import static com.krdevops.springai.config.mcp.McpToolRiskLevel.DB_WRITE;
import static com.krdevops.springai.config.mcp.McpToolRiskLevel.EXTERNAL;
import static com.krdevops.springai.config.mcp.McpToolRiskLevel.FILE_WRITE;
import static com.krdevops.springai.config.mcp.McpToolRiskLevel.READ;

/**
 * ARCH-0102: 등록된 모든 Tool 객체 클래스와 위험 등급의 inventory.
 *
 * <p>2026-08-03 기준 실측(WP2 이후 34개 Tool 객체)으로 작성했다. 여기 없는 Tool 클래스가
 * {@code McpConfig}에 등록되면 {@link #riskLevelOf(Class)}가 즉시 예외를 던져 기동을
 * 차단한다(ARCH-0103) — 새 Tool을 추가할 때는 반드시 이 registry에 먼저 등급을 등록해야 한다.
 *
 * <p>등급은 각 Tool이 위임하는 Service의 실제 부작용(DB 쓰기/파일 쓰기/외부 호출/Apply)을
 * 기준으로 분류했다. 텍스트만 생성하고 저장은 별도 Tool({@code CodeSaverTool} 등)에 위임하는
 * 클래스(예: {@code AuthTool.generateAuthInsertSql}, {@code MenuTool.generateMenuInsertSql},
 * {@code SecurityTemplateTool.getSecurityTemplate})는 READ로 분류했다.
 */
@Component
public class McpToolRiskRegistry {

    private static final Map<Class<?>, McpToolRiskLevel> RISK_BY_CLASS = buildRegistry();

    private static Map<Class<?>, McpToolRiskLevel> buildRegistry() {
        Map<Class<?>, McpToolRiskLevel> m = new HashMap<>();

        // READ — 조회, 정적 템플릿/가이드, 텍스트만 생성(저장은 별도 Tool에 위임)
        m.put(DateTimeTool.class, READ);
        m.put(SchemaReaderTool.class, READ);
        m.put(CodeTemplateTool.class, READ);
        m.put(CodeValidatorTool.class, READ);
        m.put(ProjectScannerTool.class, READ);
        m.put(CommonCodeTool.class, READ);
        m.put(WorkflowGuideTool.class, READ);
        m.put(JoinQueryTool.class, READ);
        m.put(CrudScreenSourceTool.class, READ);
        m.put(BoardScreenSourceTool.class, READ);
        m.put(MasterDetailScreenSourceTool.class, READ);
        m.put(ProjectHealthTool.class, READ);
        m.put(MenuTool.class, READ);
        m.put(AuthTool.class, READ);
        m.put(SecurityTemplateTool.class, READ);
        m.put(SqlTool.class, READ);
        m.put(OutputPathResolverTool.class, READ);

        // EXTERNAL — OpenAI/Ollama/Figma API/Web Capture Extractor 등 외부 시스템 호출
        m.put(DesignReferenceTool.class, EXTERNAL);
        m.put(RagTool.class, EXTERNAL);
        m.put(CaptureWebPageTool.class, EXTERNAL);
        m.put(DesignArtifactTool.class, EXTERNAL);
        m.put(FigmaExportTool.class, EXTERNAL);
        m.put(DesignSystemTool.class, EXTERNAL);

        // DB_WRITE — 애플리케이션 관리 테이블 쓰기
        m.put(EmployeeTool.class, DB_WRITE);
        m.put(GenerationHistoryTool.class, DB_WRITE);

        // FILE_WRITE — 로컬 파일 시스템에 생성 결과/설정을 씀
        m.put(CodeSaverTool.class, FILE_WRITE);
        m.put(CrudGenerationTool.class, FILE_WRITE);
        m.put(BoardGenerationTool.class, FILE_WRITE);
        m.put(MasterDetailGenerationTool.class, FILE_WRITE);
        m.put(ProjectInitializrTool.class, FILE_WRITE);
        m.put(ThymeleafLayoutTool.class, FILE_WRITE);

        // APPLY — 승인된 변경을 대상 프로젝트/Figma Canvas에 실제 적용
        m.put(FigmaDesignOrchestrationTool.class, APPLY);
        m.put(FigmaApprovedSpecificationTool.class, APPLY);
        m.put(ThymeleafProjectWorkflowTool.class, APPLY);

        return Collections.unmodifiableMap(m);
    }

    /**
     * @throws IllegalStateException 등록되지 않은 Tool 클래스일 때(ARCH-0103) — 기동 실패로 이어진다.
     */
    public McpToolRiskLevel riskLevelOf(Class<?> toolObjectClass) {
        McpToolRiskLevel level = RISK_BY_CLASS.get(toolObjectClass);
        if (level == null) {
            throw new IllegalStateException(
                    "MCP Tool 클래스가 위험 등급 레지스트리에 없습니다: " + toolObjectClass.getName()
                    + " — McpToolRiskRegistry.buildRegistry()에 먼저 등급을 등록하세요.");
        }
        return level;
    }

    public Set<Class<?>> registeredClasses() {
        return RISK_BY_CLASS.keySet();
    }
}
