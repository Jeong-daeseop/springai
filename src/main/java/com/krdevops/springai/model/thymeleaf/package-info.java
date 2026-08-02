/**
 * I-2: 기존 JSP·Controller·VO 업무 화면을 Thymeleaf HTML 생성 전에 추적 가능한 계약으로
 * 확정하는 모델 패키지.
 *
 * <p>{@code LegacyConversionRequest}·{@code LegacyScreenAnalysis}·{@code ThymeleafBindingContract}는
 * Figma {@code FigmaDesignRequest}·{@code FigmaDesignOperation}과 도메인 의미가 다르므로 통합하지
 * 않는다(§3.2). 공통 값 객체({@code SourceRevisionRef}, {@code GenerationIssue})는
 * {@link com.krdevops.springai.model.contract}를 그대로 재사용한다.
 *
 * <p>이 패키지는 I-2A~E(계약·보안·Source Reader·Binding Contract)만 다루며, HTML Skeleton 생성(I-4),
 * Responsive 변환과 Project Apply(I-5), Frontend CSS·JS Source Graph(I-2D)는 포함하지 않는다.
 * 참고: {@code docs/figma/Figma_MCP와_eGovFrame_JSP_Thymeleaf_통합_구현계획서.md} §I-2.
 */
package com.krdevops.springai.model.thymeleaf;
