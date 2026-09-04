package com.krdevops.springai.model.design;

import org.jspecify.annotations.Nullable;

/**
 * 등록/수정/상세 폼의 단수(1단/2단) 결정 규칙 — 임계값 단일 출처.
 *
 * <p>디자인 참조(ScreenSpecification)가 있으면 그 값을 그대로 존중한다.
 * 디자인 참조가 없는 순수 스키마 생성일 때만, 폼 입력 필드 수 기반 heuristic을 적용한다.
 * (과거 {@code LayoutTypeResolver.resolveFormColumnLayout}의 검증되지 않은 {@code >6} 초안을
 * 대체한다 — 2026-09.)</p>
 */
public final class FormColumnLayoutPolicy {

    /** 이 값 이상의 폼 입력 필드(PK·감사컬럼 제외)면 2단. 조정은 이 상수 한 곳만 바꾼다. */
    public static final int TWO_COLUMN_MIN_FORM_FIELDS = 10;

    private FormColumnLayoutPolicy() {
    }

    /**
     * @param formFieldCount 폼 입력 필드 수 (PK·감사컬럼 제외 = CrudTemplateModel.formFields 크기)
     * @param screenSpec     디자인 참조로부터 만들어진 화면명세. null이면 순수 스키마 생성
     * @return 적용할 {@link FormColumnLayout}
     */
    public static FormColumnLayout resolve(int formFieldCount, @Nullable ScreenSpecification screenSpec) {
        if (screenSpec != null) {
            return screenSpec.formColumnLayout() == null
                    ? FormColumnLayout.SINGLE_COLUMN : screenSpec.formColumnLayout();
        }
        return formFieldCount >= TWO_COLUMN_MIN_FORM_FIELDS
                ? FormColumnLayout.TWO_COLUMN : FormColumnLayout.SINGLE_COLUMN;
    }
}
