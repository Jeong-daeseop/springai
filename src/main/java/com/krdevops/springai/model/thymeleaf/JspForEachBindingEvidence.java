package com.krdevops.springai.model.thymeleaf;

/**
 * `<c:forEach var="item" items="${resultList}">`처럼 loop 변수를 Model attribute 목록에 묶는 증거.
 * loop 변수의 필드 접근(예: {@code ${item.emplyrId}})을 VO 필드로 되짚어 해석하는 데 쓴다.
 */
public record JspForEachBindingEvidence(
        String loopVariable,
        String itemsAttributeName,
        String sourceLocation
) {
    public JspForEachBindingEvidence {
        if (loopVariable == null || loopVariable.isBlank()) {
            throw new IllegalArgumentException("loopVariable은 필수입니다.");
        }
        if (itemsAttributeName == null || itemsAttributeName.isBlank()) {
            throw new IllegalArgumentException("itemsAttributeName은 필수입니다.");
        }
    }
}
