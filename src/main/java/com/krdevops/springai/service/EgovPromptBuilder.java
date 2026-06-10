package com.krdevops.springai.service;

import org.springframework.stereotype.Component;

/**
 * eGovFrame AI 공통 프롬프트 조립기.
 *
 * 시스템 역할 문자열과 CRUD 공통 제약 블록을 한 곳에서 관리합니다.
 * 이 클래스를 수정하면 채팅 서비스·CRUD 생성·마스터-디테일 생성에 일괄 반영됩니다.
 */
@Component
public class EgovPromptBuilder {

    // ── 시스템 역할 (단일 진실 공급원) ─────────────────────────────────────

    /**
     * 일반 채팅용 시스템 역할.
     * RAG 컨텍스트 없이 일반 질문에 응답할 때 사용합니다.
     */
    public String systemRole() {
        return "당신은 eGovFrame 5.x 전문 AI 어시스턴트입니다.\n" +
               "전자정부 표준프레임워크 기반의 소스 생성, 쿼리 분석, 아키텍처 안내를 담당합니다.\n" +
               "반드시 한국어로 답변하세요.";
    }

    /**
     * RAG 컨텍스트를 포함한 시스템 역할.
     * Vector Store에서 검색된 참고 문서가 있을 때 사용합니다.
     *
     * @param ragContext buildRagContext()가 반환한 참고 문서 블록
     */
    public String ragSystemPrompt(String ragContext) {
        return systemRole() + "\n\n아래 참고 문서를 기반으로 답변하세요:\n\n" + ragContext;
    }

    /**
     * ContextAssembler가 조합한 통합 컨텍스트(Schema + 이력 + 관계 + RAG)를 포함한 시스템 역할.
     * ragSystemPrompt()와 포맷은 동일하나, 컨텍스트 출처가 더 풍부합니다.
     *
     * @param context ContextAssembler.build()가 반환한 통합 컨텍스트
     */
    public String assembledSystemPrompt(String context) {
        return systemRole() + "\n\n아래 정보를 참고하여 답변하세요:\n\n" + context;
    }

    // ── CRUD 소스 생성 공통 제약 ────────────────────────────────────────────

    /**
     * [소스 생성 제약] 블록.
     * CrudPromptBuilderService와 MasterDetailService가 공유합니다.
     */
    public String crudConstraints() {
        return "[소스 생성 제약 — 필수 준수]\n" +
               "  - 각 레이어는 반드시 getCodeTemplate(layer)가 반환한 템플릿을 기반으로 생성하세요.\n" +
               "  - 위 [플레이스홀더 치환 규칙]의 값을 정확히 대입하고 임의 해석하지 마세요.\n" +
               "  - 템플릿 구조·어노테이션·상속을 변경하지 마세요.\n" +
               "  - import는 템플릿에 명시된 항목만 사용하세요. 단, {{VALIDATION_IMPORT}} 플레이스홀더 치환값(javax/jakarta validation)은 그대로 유지하세요.\n" +
               "  - 플레이스홀더 외 메서드·주석·필드 추가·삭제 금지.\n" +
               "  - {{DOMAIN_KR}} 등 한국어 값은 위 규칙에 명시된 값만 사용하세요.\n\n";
    }

    /**
     * [생성 완료 후 필수 처리] 블록.
     *
     * @param outputPath  소스 저장 경로
     * @param tableParam  이력 저장용 테이블 식별자 (단일: "TABLE", 마스터-디테일: "MASTER+DETAIL")
     * @param domain      도메인 클래스명 (예: Employee)
     * @param packageName 패키지명 (예: egovframework.let.emp)
     * @param domainLc    도메인 소문자 시작 (예: employee)
     * @param fileCount   생성 파일 수 설명 (예: "10개 파일", "12개 파일")
     */
    public String postGeneration(String outputPath, String tableParam,
                                  String domain, String packageName,
                                  String domainLc, String fileCount) {
        return "[생성 완료 후 필수 처리]\n" +
               "  1. validateGeneratedCodeDirectory(\"" + outputPath + "\")\n" +
               "  2. saveGenerationHistory(\"" + tableParam + "\", \"" + domain + "\", \"" +
                    packageName + "\", \"" + outputPath + "\", \"" + fileCount + "\")\n" +
               "  3. checkProjectHealth(projectRootPath, \"" + domainLc + "\")\n" +
               "\n[생성 결과 — 반드시 아래 형식으로 사용자에게 출력하세요]\n" +
               "✅ " + fileCount + " 생성 완료\n" +
               "📁 저장 경로: " + outputPath + "\n" +
               "생성된 파일을 순서대로 나열하고, 사용자가 해당 경로에서 확인할 수 있도록 안내하세요.\n";
    }
}
