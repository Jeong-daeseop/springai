package com.krdevops.springai.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class WorkflowGuideService {

    // CRUD 생성 표준 14단계
    private static final List<Step> WORKFLOW = List.of(
        new Step(1,  "스키마 조회",       "getTableSchema(database, tableName)",          "테이블 컬럼·PK·타입 정보 파악"),
        new Step(2,  "VO 생성",           "getCodeTemplate(\"vo\")",                      "VO.java 생성 후 saveGeneratedCode"),
        new Step(3,  "Mapper 생성",       "getCodeTemplate(\"mapper\")",                  "Mapper.java 생성 후 saveGeneratedCode"),
        new Step(4,  "MapperXml 생성",    "getCodeTemplate(\"mapperXml\")",               "Mapper.xml 생성 후 saveGeneratedCode"),
        new Step(5,  "Service 생성",      "getCodeTemplate(\"service\")",                 "Service.java 생성 후 saveGeneratedCode"),
        new Step(6,  "ServiceImpl 생성",  "getCodeTemplate(\"serviceImpl\")",             "ServiceImpl.java 생성 후 saveGeneratedCode"),
        new Step(7,  "Controller 생성",   "getCodeTemplate(\"controller\")",              "Controller.java 생성 후 saveGeneratedCode"),
        new Step(8,  "목록JSP 생성",      "getCodeTemplate(\"jspList\")",                 "List.jsp 생성 후 saveGeneratedCode"),
        new Step(9,  "상세JSP 생성",      "getCodeTemplate(\"jspDetail\")",               "Detail.jsp 생성 후 saveGeneratedCode"),
        new Step(10, "등록JSP 생성",      "getCodeTemplate(\"jspRegist\")",               "Regist.jsp 생성 후 saveGeneratedCode"),
        new Step(11, "수정JSP 생성",      "getCodeTemplate(\"jspUpdt\")",                 "Updt.jsp 생성 후 saveGeneratedCode"),
        new Step(12, "소스 검증",         "validateGeneratedCodeDirectory(outputPath)",   "표준 준수 여부 일괄 검증"),
        new Step(13, "생성 이력 저장",    "saveGenerationHistory(...)",                   "DB + RAG 이력 등록"),
        new Step(14, "완성도 점검",       "checkProjectHealth(projectPath, domain)",      "최종 완성도 확인")
    );

    public String suggestNextStep(String currentContext) {
        if (currentContext == null || currentContext.isBlank()) {
            return buildFullGuide(0);
        }

        int completedStep = detectCompletedStep(currentContext.toLowerCase());
        return buildFullGuide(completedStep);
    }

    private int detectCompletedStep(String ctx) {
        // 전체 키워드를 모두 스캔하여 완료된 단계 Set 구성 (중복·비순차 입력 대응)
        Set<Integer> completed = new HashSet<>();
        if (contains(ctx, "헬스체크", "checkprojecthealth", "완성도 점검"))          completed.add(14);
        if (contains(ctx, "이력 저장", "savegeneration", "이력 등록"))                completed.add(13);
        if (contains(ctx, "검증 완료", "validategenerated", "소스 검증"))             completed.add(12);
        if (contains(ctx, "jspupdt", "수정 jsp", "updt.jsp", "수정jsp"))              completed.add(11);
        if (contains(ctx, "jspregist", "등록 jsp", "regist.jsp", "등록jsp"))          completed.add(10);
        if (contains(ctx, "jspdetail", "상세 jsp", "detail.jsp", "상세jsp"))          completed.add(9);
        if (contains(ctx, "jsplist", "목록 jsp", "list.jsp", "목록jsp"))              completed.add(8);
        if (contains(ctx, "controller", "컨트롤러"))                                  completed.add(7);
        if (contains(ctx, "serviceimpl", "서비스impl", "구현체"))                     completed.add(6);
        if (contains(ctx, "service.java", "service 인터페이스", "서비스 인터페이스")) completed.add(5);
        if (contains(ctx, "mapper.xml", "mapperxml", "매퍼xml"))                      completed.add(4);
        if (contains(ctx, "mapper.java", "mapper 인터페이스", "매퍼"))                completed.add(3);
        if (contains(ctx, "vo.java", "vo 생성", "valueobjec"))                        completed.add(2);
        if (contains(ctx, "gettableschema", "스키마 조회", "테이블 스키마"))          completed.add(1);

        if (completed.isEmpty()) return 0;

        // 연속된 최고 완료 단계 탐색 — 중간 누락 단계가 있으면 그 직전까지만 인정
        int maxContinuous = 0;
        for (int i = 1; i <= WORKFLOW.size(); i++) {
            if (completed.contains(i)) {
                maxContinuous = i;
            } else {
                break;  // 연속이 끊기면 중단
            }
        }
        // 연속이 없어도 감지된 단계 중 최솟값을 기준으로 다음 단계 안내
        if (maxContinuous == 0) {
            maxContinuous = completed.stream().min(Integer::compareTo).orElse(0) - 1;
        }
        return Math.max(0, maxContinuous);
    }

    private boolean contains(String ctx, String... keywords) {
        for (String kw : keywords) {
            if (ctx.contains(kw.toLowerCase())) return true;
        }
        return false;
    }

    private String buildFullGuide(int completedStep) {
        StringBuilder sb = new StringBuilder();

        int totalSteps = WORKFLOW.size();
        int progress = (int) ((completedStep / (double) totalSteps) * 100);

        sb.append("=== eGovFrame CRUD 생성 워크플로우 ===\n");
        sb.append(String.format("진행률: %d/%d 단계 완료 (%d%%)\n\n", completedStep, totalSteps, progress));

        if (completedStep >= totalSteps) {
            sb.append("✅ 모든 단계가 완료되었습니다!\n");
            sb.append("checkProjectHealth()로 최종 완성도를 확인하세요.\n");
            return sb.toString();
        }

        // 완료된 단계
        if (completedStep > 0) {
            sb.append("[완료 단계]\n");
            for (int i = 0; i < completedStep; i++) {
                sb.append(String.format("  ✅ Step %2d: %s\n", WORKFLOW.get(i).no(), WORKFLOW.get(i).name()));
            }
            sb.append("\n");
        }

        // 다음 단계 (즉시 실행)
        Step next = WORKFLOW.get(completedStep);
        sb.append("[다음 단계 — 즉시 실행]\n");
        sb.append(String.format("  ▶ Step %d: %s\n", next.no(), next.name()));
        sb.append(String.format("     Tool: %s\n", next.tool()));
        sb.append(String.format("     설명: %s\n\n", next.desc()));

        // 남은 단계
        if (completedStep + 1 < totalSteps) {
            sb.append("[남은 단계]\n");
            for (int i = completedStep + 1; i < totalSteps; i++) {
                Step s = WORKFLOW.get(i);
                sb.append(String.format("  ⬜ Step %2d: %-20s → %s\n", s.no(), s.name(), s.tool()));
            }
        }

        return sb.toString();
    }

    private record Step(int no, String name, String tool, String desc) {}
}
