package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.figma.FigmaExportBundle;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import org.springframework.stereotype.Component;

/** R2-010: FigmaScreenSpec/FigmaExportBundle을 파일 다운로드·저장용 JSON 문자열로 직렬화한다. */
@Component
public class FigmaScreenSpecSerializer {

    private final ObjectMapper objectMapper;

    public FigmaScreenSpecSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().findAndRegisterModules()
                .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
    }

    public String toJson(FigmaScreenSpec spec) {
        try {
            return objectMapper.writeValueAsString(spec);
        } catch (Exception e) {
            throw new IllegalStateException("FigmaScreenSpec JSON 직렬화 실패", e);
        }
    }

    public String toJson(FigmaExportBundle bundle) {
        try {
            return objectMapper.writeValueAsString(bundle);
        } catch (Exception e) {
            throw new IllegalStateException("FigmaExportBundle JSON 직렬화 실패", e);
        }
    }
}
