package com.krdevops.springai.controller;

import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.EmployeeService;
import com.krdevops.springai.service.LlmRouterService;
import com.krdevops.springai.service.SchemaService;
import com.krdevops.springai.vo.EmployeeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Function Calling 전용 REST API.
 * Claude Desktop은 SSE(/sse)를 사용하고,
 * OpenAI는 이 엔드포인트를 Function Calling으로 호출한다.
 *
 * 인증: X-API-Key 헤더 (SecurityConfig 참조)
 */
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolApiController {

    private final SchemaService schemaService;
    private final CodeService codeService;
    private final EmployeeService employeeService;
    private final LlmRouterService llmRouterService;

    @PostMapping("/getTableList")
    public Map<String, String> getTableList(@RequestBody Map<String, String> body) {
        String database = body.get("database");
        if (database == null || database.isBlank()) throw new IllegalArgumentException("필수 파라미터 누락: database");
        return Map.of("result", schemaService.getTableList(database));
    }

    @PostMapping("/getTableSchema")
    public Map<String, String> getTableSchema(@RequestBody Map<String, String> body) {
        String database  = body.get("database");
        String tableName = body.get("tableName");
        if (database == null || database.isBlank() || tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("필수 파라미터 누락: database, tableName");
        }
        return Map.of("result", schemaService.getTableSchema(database, tableName));
    }

    @PostMapping("/saveGeneratedCode")
    public Map<String, String> saveGeneratedCode(@RequestBody Map<String, String> body) {
        String filePath = body.get("filePath");
        String code     = body.get("code");
        if (filePath == null || filePath.isBlank() || code == null) {
            throw new IllegalArgumentException("필수 파라미터 누락: filePath, code");
        }
        return Map.of("result", codeService.saveGeneratedCode(filePath, code));
    }

    @PostMapping("/getEmployee")
    public Map<String, Object> getEmployee(@RequestBody Map<String, String> body) {
        String emplyrId = body.get("emplyrId");
        if (emplyrId == null || emplyrId.isBlank()) throw new IllegalArgumentException("필수 파라미터 누락: emplyrId");
        EmployeeVO vo = employeeService.getEmployee(emplyrId);
        if (vo == null) return Map.of("result", "직원을 찾을 수 없습니다: " + emplyrId);
        return Map.of("result", vo);
    }

    @PostMapping("/getEmployeeList")
    public Map<String, Object> getEmployeeList(@RequestBody Map<String, String> body) {
        List<EmployeeVO> list = employeeService.getEmployeeList(body.getOrDefault("keyword", ""));
        return Map.of("result", list, "count", list.size());
    }

    /**
     * LlmRouterService를 통한 라우팅 호출.
     * taskType에 따라 OpenAI 또는 Ollama로 자동 분기된다.
     *
     * 요청 예: {"taskType": "SENSITIVE_DATA", "message": "처리할 내용"}
     * - CLASSIFICATION / SIMPLE_QUERY → OpenAI
     * - SENSITIVE_DATA                → Ollama (사내 처리)
     * - CODE_GENERATION               → 오류 반환 (Claude Desktop MCP SSE 사용)
     */
    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> body) {
        String taskType = body.get("taskType");
        String message  = body.get("message");
        if (taskType == null || taskType.isBlank() || message == null || message.isBlank()) {
            throw new IllegalArgumentException("필수 파라미터 누락: taskType, message");
        }
        try {
            String result = llmRouterService.chat(taskType, message);
            return Map.of("result", result, "routedTo", taskType);
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
    }
}
