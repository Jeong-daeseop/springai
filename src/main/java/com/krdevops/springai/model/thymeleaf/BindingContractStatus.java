package com.krdevops.springai.model.thymeleaf;

/**
 * {@link ThymeleafBindingContract}의 결과 상태. {@code FATAL} 수준 문제는 상태로 남기지 않고
 * Assembler가 즉시 예외를 던져 계약 자체를 만들지 않는다("FATAL 이후 후속 단계가 실행되지 않음").
 */
public enum BindingContractStatus {
    RESOLVED,
    REVIEW_REQUIRED
}
