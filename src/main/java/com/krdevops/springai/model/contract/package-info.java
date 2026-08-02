/**
 * Figma 디자인 흐름과 eGovFrame JSP→Thymeleaf 전환 흐름이 공유하는 값 객체 패키지.
 *
 * <p>여기 정의된 타입은 두 도메인 어디에도 전용 필드를 두지 않는다(I-0 완료 게이트).
 * {@code DesignSystemSnapshotRef}, {@code GenerationIssue}, {@code ArtifactRef},
 * {@code SourceRevisionRef}는 Figma {@code FigmaDesignOperation}과 향후 Thymeleaf
 * {@code ThymeleafConversionOperation}이 동일한 의미와 버전 규칙으로 재사용한다.
 *
 * <p>도메인 전용 모델({@code FigmaDesignRequest}, {@code LegacyConversionRequest} 등)은
 * 각자의 패키지({@code model.figma}, 추후 {@code model.thymeleaf})가 소유하며 이 패키지에
 * 두지 않는다. 참고: {@code docs/figma/Figma_MCP와_eGovFrame_JSP_Thymeleaf_통합_구현계획서.md} §3.1, §I-0.
 */
package com.krdevops.springai.model.contract;
