package com.krdevops.springai.model.thymeleaf;

import java.util.List;

/** Controller 파일 1개를 정적으로 읽어 추출한 전체 증거(I-2C). */
public record ControllerEvidence(
        String controllerPath,
        String className,
        String classLevelRequestMapping,
        List<ControllerMethodEvidence> methods
) {
    public ControllerEvidence {
        if (controllerPath == null || controllerPath.isBlank()) {
            throw new IllegalArgumentException("controllerPath는 필수입니다.");
        }
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("className은 필수입니다.");
        }
        methods = methods == null ? List.of() : List.copyOf(methods);
    }
}
