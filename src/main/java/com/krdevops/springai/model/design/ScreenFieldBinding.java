package com.krdevops.springai.model.design;

import com.krdevops.springai.model.design.role.FieldMode;
import com.krdevops.springai.model.design.role.SemanticRole;

/**
 * KRV-013: {@code dataRole}는 DB 컬럼이 담고 있는 데이터 의미(제목/상태/작성자 등)이고,
 * {@code semanticRole}은 Figma UI Component 해석에 쓰이는 화면 의미(field.text 등)로
 * 서로 독립적으로 직렬화된다. Figma 정규화 이전 단계(ScreenSpecAssembler 등)는
 * semanticRole/mode를 알 수 없으므로 null로 둘 수 있다.
 */
public record ScreenFieldBinding(
        String id,
        String label,
        UiFieldRole dataRole,
        SemanticRole semanticRole,
        FieldMode mode,
        FieldSource source,
        boolean visible,
        boolean required,
        boolean searchable,
        boolean sortable,
        String control,
        double confidence
) {
    /** KRV-013 도입 전 호출자 호환. semanticRole/mode는 아직 파생되지 않았으므로 null로 채운다. */
    public ScreenFieldBinding(
            String id,
            String label,
            UiFieldRole dataRole,
            FieldSource source,
            boolean visible,
            boolean required,
            boolean searchable,
            boolean sortable,
            String control,
            double confidence
    ) {
        this(id, label, dataRole, null, null, source, visible, required, searchable, sortable, control, confidence);
    }
}
