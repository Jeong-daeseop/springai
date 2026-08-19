package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.figma.contract.FigmaDesignRequestType;
import org.springframework.stereotype.Service;

/**
 * Figma 디자인 요청 타입 판정 및 라우팅.
 * 2-A5-1 요청 흐름: 요청 타입 판정
 *
 * 명시 타입 우선, 자유 텍스트는 LLM 분류
 */
@Service
public class FigmaDesignRequestRouter {

    /**
     * 요청에서 타입을 판정한다.
     * 명시 타입이 있으면 그대로 사용, 없으면 자유 텍스트로 판정.
     */
    public FigmaDesignRequestType determineType(
            String requestType,
            String prompt,
            boolean hasReferenceNodeIds,
            boolean hasEditableNodeIds,
            boolean hasComponentLogicalTypes) {

        // 1. 명시 타입 우선
        if (requestType != null && !requestType.isBlank()) {
            try {
                return FigmaDesignRequestType.valueOf(requestType.toUpperCase());
            } catch (IllegalArgumentException e) {
                // 미지원 타입은 문자 분류로 폴백
            }
        }

        // 2. 컨텍스트 기반 판정
        if (hasEditableNodeIds) {
            return FigmaDesignRequestType.MODIFY_EXISTING;
        }

        if (hasReferenceNodeIds) {
            return FigmaDesignRequestType.REFERENCE_STYLE;
        }

        if (hasComponentLogicalTypes) {
            return FigmaDesignRequestType.COMPONENT_SPECIFIED;
        }

        // 3. 기본값: 자유 텍스트 요청
        return FigmaDesignRequestType.TEXT_DESCRIPTION;
    }

    /**
     * 요청 타입의 신뢰도를 반환한다.
     * 명시 타입: 1.0
     * 컨텍스트: 0.8
     * 기본값: 0.6
     */
    public double getConfidence(FigmaDesignRequestType type, boolean explicit) {
        return explicit ? 1.0 : (type == FigmaDesignRequestType.TEXT_DESCRIPTION ? 0.6 : 0.8);
    }
}
