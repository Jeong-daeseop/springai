package com.krdevops.springai.chat.util;

public class EgovJsonPromptTemplates {

    public static String createTechnologyInfoPrompt(String query) {
        return """
                다음 질문에 대해 기술 정보를 JSON 형식으로 응답해주세요.

                질문: %s

                다음 JSON 형식으로 정확히 응답해주세요:
                {
                  "name": "기술명",
                  "category": "카테고리 (프레임워크, 언어, 도구 등)",
                  "description": "기술에 대한 설명",
                  "features": ["특징1", "특징2", "특징3"],
                  "useCases": ["사용 사례1", "사용 사례2", "사용 사례3"]
                }

                규칙:
                1. 반드시 위 JSON 형식으로만 응답하세요
                2. 다른 텍스트나 설명을 추가하지 마세요
                3. JSON 형식이 정확해야 합니다
                4. 모든 필드를 채워주세요
                """.formatted(query);
    }
}
