package com.krdevops.springai.service.figma.builder;

import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.service.figma.LogicalNodeIdFactory;

/** 화면유형 하나를 논리 Figma 컴포넌트 트리로 변환하는 공통 Builder(08번 §7.4). */
public interface FigmaScreenBuilder {

    FigmaScreenType supportedType();

    FigmaNodeSpec build(ScreenSpecification screenSpecification, PageSpec page, LogicalNodeIdFactory idFactory);
}
