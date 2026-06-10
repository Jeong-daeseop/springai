package com.krdevops.springai.model;

import java.util.function.Supplier;

/** 생성할 파일 1개. content는 지연 평가 → 렌더 실패를 파일 단위로 격리 */
public record FilePlan(
        String relativePath,
        FileKind kind,
        Supplier<String> content
) {
    public enum FileKind { BUILD, CONFIG, SOURCE, RESOURCE, WEB, TEST, META }

    public static FilePlan of(String path, FileKind kind, Supplier<String> content) {
        return new FilePlan(path, kind, content);
    }
}
