/**
 * Figma와 Thymeleaf 두 Orchestrator가 직접 호출하지 않고 공유하는 정책 서비스 패키지.
 *
 * <p>{@code OperationHashFactory}는 content hash 정규화·idempotency key 생성 규칙을,
 * {@code GenerationIssueFactory}는 {@link com.krdevops.springai.model.contract.GenerationIssue}
 * 생성 규칙을 단일 구현으로 유지한다. 두 Orchestrator는 이 패키지의 서비스만 의존하며
 * 서로를 직접 참조하지 않는다(§3.4).
 */
package com.krdevops.springai.service.contract;
