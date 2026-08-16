package com.krdevops.springai.model.figma.refinement;

/**
 * MR-DEC-01: Manual Refinement 수명주기.
 * {@code DRAFT → CAPTURED → REVIEW_REQUIRED → APPROVED/REJECTED → APPLIED/SUPERSEDED}
 */
public enum FigmaRefinementStatus {
    DRAFT,
    CAPTURED,
    REVIEW_REQUIRED,
    APPROVED,
    REJECTED,
    APPLIED,
    SUPERSEDED
}
