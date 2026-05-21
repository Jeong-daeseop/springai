package com.krdevops.springai.tools;

import com.krdevops.springai.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RagTool {

    private final RagService ragService;

    @Tool(description = """
            텍스트 문서를 Vector Store에 임베딩하여 RAG 검색 대상으로 등록합니다.
            docId: 문서 식별자 (예: egov-login-guide, history-20260518)
            content: 임베딩할 텍스트 내용
            type: 문서 유형 — document(가이드/문서), source_code(소스코드), history(생성이력)
            이후 ragSearch 또는 ragChat으로 유사 문서를 검색할 수 있습니다.
            """)
    public String ragIngest(String docId, String content, String type) {
        return ragService.ingestText(docId, content, type);
    }

    @Tool(description = """
            디렉터리 내 .java 파일을 전부 Vector Store에 임베딩합니다.
            directoryPath: 스캔할 디렉터리 절대 경로 (예: /Users/user/project/src/main/java)
            기존 프로젝트 소스코드를 참조 컨텍스트로 등록할 때 사용합니다.
            """)
    public String ragIngestDirectory(String directoryPath) {
        return ragService.ingestJavaDirectory(directoryPath);
    }

    @Tool(description = """
            단일 URL의 HTML 페이지를 크롤링하여 텍스트를 추출하고 Vector Store에 임베딩합니다.
            url  : 크롤링할 URL (예: https://www.egovframe.go.kr/docs/5.0/getting-started/)
            docId: 문서 식별자. 비워두면 URL에서 자동 생성됩니다.
            HTML 태그·스크립트·네비게이션을 제거한 순수 텍스트를 1000자 단위 청크로 분할 저장합니다.
            """)
    public String ragIngestUrl(String url, String docId) {
        return ragService.ingestUrl(url, docId);
    }

    @Tool(description = """
            여러 URL을 한 번에 크롤링하여 Vector Store에 일괄 임베딩합니다.
            urls: 쉼표(,)로 구분된 URL 목록
            예) "https://url1.com, https://url2.com, https://url3.com"
            각 URL을 순서대로 처리하고 성공/실패 결과를 반환합니다.
            eGovFrame 공식 문서, 가이드, API 레퍼런스 등 여러 페이지를 한 번에 등록할 때 사용합니다.
            """)
    public String ragIngestUrls(String urls) {
        return ragService.ingestUrls(urls);
    }

    @Tool(description = """
            Vector Store에서 쿼리와 유사한 문서를 검색합니다.
            query: 검색 쿼리 (자연어)
            topK: 반환할 최대 문서 수 (기본 3)
            등록된 가이드 문서, 소스코드, 생성 이력 중 관련 내용을 찾을 때 사용합니다.
            """)
    public String ragSearch(String query, int topK) {
        return ragService.buildRagContext(query, topK);
    }
}
