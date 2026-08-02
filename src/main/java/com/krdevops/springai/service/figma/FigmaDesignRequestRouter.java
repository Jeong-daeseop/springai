package com.krdevops.springai.service.figma;

import org.springframework.stereotype.Service;

/**
 * Figma 디자인 요청 타입 판정 및 라우팅.
 * 2-A5-1 요청 흐름: 요청 타입 판정
 *
 * 명시 타입 우선, 자유 텍스트는 LLM 분류
 */
@Service
public class FigmaDesignRequestRouter {

    public enum DesignRequestType {
        CREATE_FROM_TEXT,      // 자연어 → 화면 생성
        CREATE_FROM_REFERENCE, // 기존 화면/이미지 → 새 화면
        MODIFY_EXISTING,       // 기존 화면 → 수정
        CREATE_WITH_COMPONENTS // 특정 컴포넌트 → 화면 생성
    }

    /**
     * 요청에서 타입을 판정한다.
     * 명시 타입이 있으면 그대로 사용, 없으면 자유 텍스트로 판정.
     */
    public DesignRequestType determineType(
            String requestType,
            String prompt,
            boolean hasReferenceNodeIds,
            boolean hasEditableNodeIds,
            boolean hasComponentLogicalTypes) {

        // 1. 명시 타입 우선
        if (requestType != null && !requestType.isBlank()) {
            try {
                return DesignRequestType.valueOf(requestType.toUpperCase());
            } catch (IllegalArgumentException e) {
                // 미지원 타입은 문자 분류로 폴백
            }
        }

        // 2. 컨텍스트 기반 판정
        if (hasEditableNodeIds) {
            return DesignRequestType.MODIFY_EXISTING;
        }

        if (hasReferenceNodeIds) {
            return DesignRequestType.CREATE_FROM_REFERENCE;
        }

        if (hasComponentLogicalTypes) {
            return DesignRequestType.CREATE_WITH_COMPONENTS;
        }

        // 3. 기본값: 자유 텍스트 요청
        return DesignRequestType.CREATE_FROM_TEXT;
    }

    /**
     * 요청 타입의 신뢰도를 반환한다.
     * 명시 타입: 1.0
     * 컨텍스트: 0.8
     * 기본값: 0.6
     */
    public double getConfidence(DesignRequestType type, boolean explicit) {
        return explicit ? 1.0 : (type == DesignRequestType.CREATE_FROM_TEXT ? 0.6 : 0.8);
    }
}
