package com.krdevops.springai.model.parity;

import com.krdevops.springai.model.figma.contract.FigmaDesignOperation;
import com.krdevops.springai.model.thymeleaf.ThymeleafProjectOperation;

/**
 * ARCH-0207: {@code DesignParityValidationUseCase} 입력 계약.
 *
 * <p>호출자는 이미 승인 경로를 거쳐 실존이 보장된 {@link FigmaDesignOperation}과
 * {@link ThymeleafProjectOperation}을 직접 전달한다 — 이 UseCase는 operationId로
 * Repository를 조회하지 않는다(호출자가 이미 조회 책임을 진다). 임의 서버 경로 문자열은
 * 받지 않는다(ARCH-0206) — Figma 쪽은 Operation에 이미 포함된 {@code artifactId}로,
 * Thymeleaf 쪽은 Operation에 이미 포함된 {@code previewArtifacts} 키로만 참조한다.
 */
public record DesignParityRequest(
        FigmaDesignOperation figmaOperation,
        String figmaArtifactId,
        ThymeleafProjectOperation thymeleafOperation,
        String thymeleafRelativePath,
        /** 호출자가 미리 선언한 기대 hash. null/blank면 evidence 미제공으로 UNSUPPORTED. */
        String expectedThymeleafContentHash) {
}
