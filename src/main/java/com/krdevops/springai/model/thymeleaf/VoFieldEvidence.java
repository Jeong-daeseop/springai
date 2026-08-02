package com.krdevops.springai.model.thymeleaf;

import java.util.List;

/** VO 필드 1개의 타입·접근자·Bean Validation 증거. */
public record VoFieldEvidence(
        String fieldName,
        String javaType,
        boolean readable,
        boolean writable,
        List<String> validationAnnotations,
        String sourceLocation
) {
    public VoFieldEvidence {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName은 필수입니다.");
        }
        if (javaType == null || javaType.isBlank()) {
            throw new IllegalArgumentException("javaType은 필수입니다.");
        }
        validationAnnotations = validationAnnotations == null ? List.of() : List.copyOf(validationAnnotations);
    }

    public boolean required() {
        return validationAnnotations.stream()
                .anyMatch(name -> name.startsWith("@NotNull") || name.startsWith("@NotBlank")
                        || name.startsWith("@NotEmpty"));
    }
}
