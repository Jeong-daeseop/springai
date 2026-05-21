package com.krdevops.springai.tools;

import com.krdevops.springai.service.WorkflowGuideService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkflowGuideTool {

    private final WorkflowGuideService workflowGuideService;

    @Tool(description = """
            eGovFrame CRUD 소스 생성 워크플로우에서 다음 단계를 제안합니다.
            currentContext: 현재까지 완료한 작업 내용을 자유롭게 입력하세요.
            예) "EmployerVO.java 생성 완료", "Mapper XML까지 생성됨", "Controller 저장 완료"
            비워두면 전체 14단계 워크플로우를 처음부터 안내합니다.
            반환값: 현재 진행률(%), 다음 실행할 Tool 이름, 남은 단계 목록
            CRUD 소스 생성 중 어디까지 진행됐는지 모를 때 또는 다음에 무엇을 해야 할지 확인할 때 사용합니다.
            """)
    public String suggestNextStep(String currentContext) {
        return workflowGuideService.suggestNextStep(currentContext);
    }
}
