package com.krdevops.springai.model.crud;

/**
 * 범용 CRUD 프로그램 메타데이터를 명시적으로 지정하는 선택 옵션.
 * {@code BoardGenerationOptions}와 동일한 역할이며, bbsId 개념이 없는 대신
 * programFileName은 목록(list) 화면 프로그램을 고정하는 데 사용된다.
 */
public record CrudGenerationOptions(
        String programFileName,
        String programUrl,
        String programKoreanName,
        String programStorePath,
        String designReferenceId,
        String screenSpecificationId
) {
    public CrudGenerationOptions {
        programFileName = normalize(programFileName);
        programUrl = normalize(programUrl);
        programKoreanName = normalize(programKoreanName);
        programStorePath = normalize(programStorePath);
        designReferenceId = normalize(designReferenceId);
        screenSpecificationId = normalize(screenSpecificationId);
    }

    public CrudGenerationOptions(
            String programFileName, String programUrl, String programKoreanName, String programStorePath) {
        this(programFileName, programUrl, programKoreanName, programStorePath, null, null);
    }

    public static CrudGenerationOptions empty() {
        return new CrudGenerationOptions(null, null, null, null, null, null);
    }

    public boolean hasExplicitValue() {
        return programFileName != null || programUrl != null
                || programKoreanName != null || programStorePath != null;
    }

    public boolean hasDesignReference() {
        return designReferenceId != null || screenSpecificationId != null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
