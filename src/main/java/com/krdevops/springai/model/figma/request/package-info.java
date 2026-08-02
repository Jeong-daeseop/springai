/**
 * I-1: 디자인 요청을 안전하고 멱등적인 Preview Operation으로 저장하기 위한 Figma 전용 모델.
 *
 * <p>{@code FigmaDesignRequest}/{@code FigmaDesignOperation}은 이 패키지가 소유하며,
 * 공통 값 객체({@code DesignSystemSnapshotRef}, {@code GenerationIssue}, {@code ArtifactRef},
 * {@code SourceRevisionRef})는 {@link com.krdevops.springai.model.contract}를 그대로 참조한다.
 * 이 패키지는 {@code FigmaDesignRequestRouter}·{@code FigmaContextAnalyzer}(I-3) 같은 분석·라우팅
 * 로직을 포함하지 않는다 — 계약과 상태 저장 기반만 제공한다.
 */
package com.krdevops.springai.model.figma.request;
