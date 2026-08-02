package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.figma.contract.FigmaDesignRequestType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * R6-030: 자유 텍스트 요청을 7가지 FigmaDesignRequestType 중 하나로 분류.
 * confidence 기반 필터링: confidence < 0.6인 경우는 추측 실행 거부.
 *
 * 현재는 간단한 keyword-based classification으로 구현.
 * 추후 Spring AI 구조화 출력으로 업그레이드 가능.
 */
@Slf4j
@Service
public class FigmaDesignRequestClassifierService {

    /**
     * 자유 텍스트 프롬프트를 분석해 요청 유형과 confidence를 반환.
     * confidence < 0.6이면 Optional.empty() 반환 (추측 거부).
     */
    public Optional<ClassificationResult> classifyRequest(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return Optional.empty();
        }

        String lowerPrompt = prompt.toLowerCase();
        double confidence = 0.0;
        FigmaDesignRequestType detectedType = null;

        // 간단한 keyword 기반 분류
        if (containsAny(lowerPrompt, "modify", "update", "change", "edit existing")) {
            detectedType = FigmaDesignRequestType.MODIFY_EXISTING;
            confidence = 0.8;
        } else if (containsAny(lowerPrompt, "reference", "based on", "like", "similar")) {
            detectedType = FigmaDesignRequestType.REFERENCE_STYLE;
            confidence = 0.7;
        } else if (containsAny(lowerPrompt, "image", "screenshot", "photo", "design image")) {
            detectedType = FigmaDesignRequestType.IMAGE_REFERENCE;
            confidence = 0.75;
        } else if (containsAny(lowerPrompt, "multiple screens", "flow", "navigation", "pages")) {
            detectedType = FigmaDesignRequestType.MULTI_SCREEN_FLOW;
            confidence = 0.7;
        } else if (containsAny(lowerPrompt, "component", "button", "field", "form")) {
            detectedType = FigmaDesignRequestType.COMPONENT_SPECIFIED;
            confidence = 0.65;
        } else if (containsAny(lowerPrompt, "mobile", "tablet", "desktop", "responsive", "convert")) {
            detectedType = FigmaDesignRequestType.PLATFORM_CONVERT;
            confidence = 0.7;
        } else {
            // 기본값: TEXT_DESCRIPTION
            detectedType = FigmaDesignRequestType.TEXT_DESCRIPTION;
            confidence = 0.6;
        }

        if (confidence < 0.6) {
            log.debug("Classification confidence too low: {}", confidence);
            return Optional.empty();
        }

        return Optional.of(new ClassificationResult(detectedType, confidence, "Keyword-based classification"));
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 분류 결과 DTO (R6-030)
     */
    public record ClassificationResult(
            FigmaDesignRequestType type,
            double confidence,
            String reasoning
    ) {
    }
}
