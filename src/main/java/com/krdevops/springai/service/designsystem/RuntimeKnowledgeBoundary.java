package com.krdevops.springai.service.designsystem;

import org.springframework.stereotype.Service;

@Service
public class RuntimeKnowledgeBoundary {
    public void requireRuntimeSource(boolean fromKnowledgeSearch) {
        if (fromKnowledgeSearch) throw new IllegalStateException("Knowledge Search/RAG 결과는 Runtime SSOT로 사용할 수 없습니다.");
    }
}
