package com.krdevops.springai.model.thymeleaf;

import java.util.List;
import java.util.Optional;

/** VO 파일 1개를 정적으로 읽어 추출한 전체 증거(I-2C). */
public record VoEvidence(
        String voPath,
        String className,
        List<VoFieldEvidence> fields
) {
    public VoEvidence {
        if (voPath == null || voPath.isBlank()) {
            throw new IllegalArgumentException("voPath는 필수입니다.");
        }
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("className은 필수입니다.");
        }
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public Optional<VoFieldEvidence> field(String fieldName) {
        return fields.stream().filter(field -> field.fieldName().equals(fieldName)).findFirst();
    }
}
