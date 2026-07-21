package com.krdevops.springai.tools;

import com.krdevops.springai.service.GenerationHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GenerationHistoryTool {

    private final GenerationHistoryService generationHistoryService;

    @Tool(description = """
            eGovFrame CRUD 소스 생성 이력을 DB에 저장합니다.
            소스 생성 완료 후 반드시 호출하여 이력을 기록하세요.
            tableName: 대상 테이블명 (예: LETTNEMPLYRINFO)
            domain: 도메인명 (예: Employer)
            packageName: 패키지명 (예: egovframework.let.emp)
            outputPath: 소스가 저장된 디렉터리 경로
            generatedFiles: 생성된 파일 목록 (쉼표 구분, 예: EmployerVO.java, EmployerMapper.java, ...)
            저장된 이력은 RAG Vector Store에도 등록되어 이후 유사 소스 생성 시 참조됩니다.
            """)
    public String saveGenerationHistory(String tableName, String domain, String packageName,
                                        String outputPath, String generatedFiles) {
        return generationHistoryService.saveHistory(tableName, domain, packageName, outputPath, generatedFiles);
    }

    @Tool(description = """
            eGovFrame CRUD 소스 생성 이력을 조회합니다.
            keyword: 검색어 (테이블명, 도메인명, 패키지명 대상) — 비어 있으면 최근 20건 반환
            이전에 어떤 테이블로 소스를 생성했는지 확인하거나, 동일한 패턴으로 재생성할 때 사용합니다.
            """)
    public String getGenerationHistory(String keyword) {
        return generationHistoryService.getHistory(keyword);
    }
}
