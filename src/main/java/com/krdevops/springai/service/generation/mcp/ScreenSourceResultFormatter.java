package com.krdevops.springai.service.generation.mcp;

import com.krdevops.springai.service.generation.model.GeneratedSource;
import org.springframework.stereotype.Component;

/** {@link GeneratedSource}를 사람이 읽기 좋은 응답 문자열로 변환한다. */
@Component
public class ScreenSourceResultFormatter {

    public String format(GeneratedSource result) {
        return """
                === 화면 소스 생성 완료 ===

                featureType: %s
                domain: %s
                screen: %s
                viewType: %s
                layerKey: %s
                권장 저장 경로: %s

                [source]
                %s
                """.formatted(
                        result.featureType().name(),
                        result.domain(),
                        result.screenType().label(),
                        result.viewType().value(),
                        result.layerKey(),
                        result.recommendedPath(),
                        result.source());
    }
}
