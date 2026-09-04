package com.krdevops.springai.model.crud;

import com.krdevops.springai.model.designsystem.DesignComponentRenderInput;

import java.util.List;
import java.util.Map;

/**
 * V2_APPLY(픽셀 재현) 경로에서만 채워지는, CRUD FreeMarker 템플릿이 KRDS 컴포넌트 fragment를
 * {@code th:replace}로 부를지 판단하는 데 필요한 정보.
 *
 * <p>plan이 {@code null}이면(=V2_PREVIEW 이하, 승인 Mapping 미해석) 템플릿은 전부 기존 마크업으로
 * 폴백해 생성 산출물이 바이트 동일하다. 이 record는 <b>렌더 전용</b>이며 VO/Mapper/스키마 계약에
 * 영향을 주지 않는다 — {@code commonCodeFields}의 공통코드 판정도 표시용으로만 쓴다.
 *
 * @param byLogicalType    승인 Mapping이 해석된 컴포넌트 — logicalType("text-input"/"select"/
 *                         "date-input"/"button"/"data-table"/"pagination") → 렌더 입력
 * @param commonCodeFields 등록/수정 폼에서 공통코드 select로 렌더할 필드 (자바 필드명 + CODE_ID)
 */
public record CrudDesignComponentPlan(
        Map<String, DesignComponentRenderInput> byLogicalType,
        List<CommonCodeField> commonCodeFields
) {
    public CrudDesignComponentPlan {
        byLogicalType = byLogicalType == null ? Map.of() : Map.copyOf(byLogicalType);
        commonCodeFields = commonCodeFields == null ? List.of() : List.copyOf(commonCodeFields);
    }

    /**
     * 공통코드 select 대상 필드 1개.
     *
     * @param javaName 자바 필드명(예: {@code sttusCode})
     * @param codeId   공통코드 그룹 CODE_ID. 화면명세가 명시하지 않았으면 {@code null} —
     *                 이 경우 생성기는 mapper.xml에 {@code '???'} 자리표시자 + 교체 안내 주석을 남긴다.
     */
    public record CommonCodeField(String javaName, String codeId) {}

    public boolean has(String logicalType) {
        return byLogicalType.containsKey(logicalType);
    }

    public DesignComponentRenderInput get(String logicalType) {
        return byLogicalType.get(logicalType);
    }

    public boolean isCommonCode(String javaName) {
        return commonCodeFields.stream().anyMatch(field -> field.javaName().equals(javaName));
    }

    /** 알려진 CODE_ID가 없으면 {@code null}. */
    public String commonCodeId(String javaName) {
        return commonCodeFields.stream()
                .filter(field -> field.javaName().equals(javaName))
                .map(CommonCodeField::codeId)
                .findFirst()
                .orElse(null);
    }

    public boolean isEmpty() {
        return byLogicalType.isEmpty() && commonCodeFields.isEmpty();
    }
}
