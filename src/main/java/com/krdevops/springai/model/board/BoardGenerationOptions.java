package com.krdevops.springai.model.board;

/** 게시판 프로그램 메타데이터를 명시적으로 지정하는 선택 옵션. */
public record BoardGenerationOptions(
        String programFileName,
        String programUrl,
        String programKoreanName,
        String programStorePath,
        String defaultBbsId,
        String designReferenceId,
        String screenSpecificationId
) {
    public BoardGenerationOptions {
        programFileName = normalize(programFileName);
        programUrl = normalize(programUrl);
        programKoreanName = normalize(programKoreanName);
        programStorePath = normalize(programStorePath);
        defaultBbsId = normalize(defaultBbsId);
        designReferenceId = normalize(designReferenceId);
        screenSpecificationId = normalize(screenSpecificationId);
    }

    public BoardGenerationOptions(
            String programFileName, String programUrl, String programKoreanName,
            String programStorePath, String defaultBbsId) {
        this(programFileName, programUrl, programKoreanName, programStorePath, defaultBbsId, null, null);
    }

    public static BoardGenerationOptions empty() {
        return new BoardGenerationOptions(null, null, null, null, null, null, null);
    }

    public boolean hasExplicitValue() {
        return programFileName != null || programUrl != null || programKoreanName != null
                || programStorePath != null || defaultBbsId != null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
