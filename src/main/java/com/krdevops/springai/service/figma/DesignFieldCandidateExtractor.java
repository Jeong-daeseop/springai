package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.design.UiDesignSpec;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 22/23번 문서 PROP-02/S-02: {@code UiDesignSpec}에서 DB 매핑 없이 필드 후보(label+roleHint)만
 * 추출한다. {@code AWAITING_TABLE_BINDING} 상태에서 사람이 "무엇을 보고 있는지" 확인할 수 있게
 * 하는 것이 목적이다. 실제 컬럼 매칭은 이 후보를 별도로 재사용하지 않고, 기존
 * {@code ScreenSpecAssembler.bindingsFromHints()}가 database/tableName 확정 후 수행한다
 * (S-03 `FieldRoleToColumnMatcher`는 이 기존 로직과 중복돼 삭제됨 — 23번 문서 참고).
 */
@Service
public class DesignFieldCandidateExtractor {

    /** 동일 입력에 결정적으로 동일한 후보 목록을 반환한다. id가 중복되면 첫 등장만 남긴다. */
    public List<UiDesignSpec.FieldHint> extract(UiDesignSpec uiSpec) {
        if (uiSpec == null || uiSpec.fieldHints().isEmpty()) {
            return List.of();
        }
        List<UiDesignSpec.FieldHint> candidates = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        for (UiDesignSpec.FieldHint hint : uiSpec.fieldHints()) {
            if (hint.id() != null && !seenIds.add(hint.id())) {
                continue;
            }
            candidates.add(hint);
        }
        return List.copyOf(candidates);
    }
}
