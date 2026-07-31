package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.service.figma.builder.FigmaScreenBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * R2-002: screenType에 맞는 FigmaScreenBuilder를 선택한다(08번 §7.4). 업무 테이블마다
 * Builder를 새로 만들지 않고, 화면유형별 공통 Builder 하나로 표현할 수 없는 예외만 향후
 * override Builder로 등록한다.
 */
@Component
public class FigmaScreenBuilderRegistry {

    private final Map<FigmaScreenType, FigmaScreenBuilder> buildersByType;

    public FigmaScreenBuilderRegistry(List<FigmaScreenBuilder> builders) {
        this.buildersByType = builders.stream()
                .collect(Collectors.toUnmodifiableMap(FigmaScreenBuilder::supportedType, Function.identity()));
    }

    public FigmaScreenBuilder builderFor(FigmaScreenType screenType) {
        FigmaScreenBuilder builder = buildersByType.get(screenType);
        if (builder == null) {
            throw new IllegalStateException("screenType " + screenType + "에 대한 Builder가 등록되어 있지 않습니다.");
        }
        return builder;
    }
}
