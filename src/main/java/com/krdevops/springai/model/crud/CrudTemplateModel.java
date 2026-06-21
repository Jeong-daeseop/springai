package com.krdevops.springai.model.crud;

import java.util.List;

/**
 * FreeMarker CRUD 템플릿 렌더링에 필요한 전체 컨텍스트.
 * CrudTemplateRenderer.render()에 전달된다.
 *
 * <p>FreeMarker 2.3.33은 Java record accessor를 지원하므로
 * 템플릿에서 ${domain}, ${packageName} 등으로 직접 접근 가능하다.
 * 접근 불안정 시 CrudTemplateRenderer 내부에서 Map 모델로 전환한다.</p>
 */
public record CrudTemplateModel(
        String packageName,          // egovframework.let.emp
        String domain,               // Employer
        String domainLc,             // employer
        String domainKr,             // 직원
        String tableName,            // COMTNEMPLYRINFO
        String urlPrefix,            // /emp/employer
        String date,                 // 생성일 (yyyy-MM-dd)
        String egovVersion,          // "5.0" | "4.3"
        boolean jakartaValidation,   // true → jakarta.validation, false → javax.validation
        PkModel pk,                  // PK 컬럼 정보
        List<FieldModel> fields,     // 전체 필드 목록 (PK 포함)
        List<FieldModel> listFields, // 목록 화면 노출 필드 (핵심/비민감 컬럼)
        List<FieldModel> nonPkFields // PK 제외 필드 (UPDATE SET, form input 기준)
) {}
