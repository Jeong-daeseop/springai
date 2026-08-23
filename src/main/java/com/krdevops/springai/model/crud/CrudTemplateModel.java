package com.krdevops.springai.model.crud;

import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.GenerationQueryContract;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.SearchPanelPlacement;
import com.krdevops.springai.model.designsystem.DesignComponentRenderInput;

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
        String tableName,            // LETTNEMPLYRINFO
        String urlPrefix,            // /emp/employer
        String date,                 // 생성일 (yyyy-MM-dd)
        String egovVersion,          // "5.0" | "4.3"
        boolean jakartaValidation,   // true → jakarta.validation, false → javax.validation
        PkModel pk,                  // PK 컬럼 정보 (첫 번째 PK — ORDER BY/검색조건 등 단일 참조용)
        List<FieldModel> pkFields,   // 전체 PK 컬럼 목록 (복합키 지원 — WHERE/hidden field/라우팅에 사용)
        List<FieldModel> fields,     // 전체 필드 목록 (PK 포함)
        List<FieldModel> listFields, // 목록 화면 노출 필드 (핵심/비민감 컬럼)
        List<FieldModel> nonPkFields,// 전체 PK 제외 필드 (UPDATE SET 기준)
        List<FieldModel> formFields, // nonPkFields 중 시스템관리(감사) 컬럼 제외 — 등록/수정 폼 입력 기준
        CrudRouteModel route,        // canonical URL + LETTNPROGRMLIST 등록 URL alias (화면별)
        GenerationQueryContract queryContract, // 승인 화면명세 기반 JOIN/projection
        List<FieldModel> detailFields, // 상세 화면 subset. null은 레거시 모델을 의미한다.
        LayoutDensity layoutDensity,
        FormColumnLayout formColumnLayout,
        ActionPlacement actionPlacement,
        SearchPanelPlacement searchPanelPlacement,
        List<DesignComponentRenderInput> designComponents
) {
    public CrudTemplateModel {
        queryContract = queryContract == null ? GenerationQueryContract.empty() : queryContract;
        layoutDensity = layoutDensity == null ? LayoutDensity.STANDARD : layoutDensity;
        formColumnLayout = formColumnLayout == null ? FormColumnLayout.SINGLE_COLUMN : formColumnLayout;
        actionPlacement = actionPlacement == null ? ActionPlacement.TOP_RIGHT : actionPlacement;
        searchPanelPlacement = searchPanelPlacement == null ? SearchPanelPlacement.ABOVE_TABLE : searchPanelPlacement;
        designComponents = designComponents == null ? List.of() : List.copyOf(designComponents);
    }

    /** designComponents 도입 전 canonical 호출자 호환. */
    public CrudTemplateModel(
            String packageName, String domain, String domainLc, String domainKr,
            String tableName, String urlPrefix, String date, String egovVersion,
            boolean jakartaValidation, PkModel pk, List<FieldModel> pkFields,
            List<FieldModel> fields, List<FieldModel> listFields,
            List<FieldModel> nonPkFields, List<FieldModel> formFields, CrudRouteModel route,
            GenerationQueryContract queryContract, List<FieldModel> detailFields,
            LayoutDensity layoutDensity, FormColumnLayout formColumnLayout,
            ActionPlacement actionPlacement, SearchPanelPlacement searchPanelPlacement) {
        this(packageName, domain, domainLc, domainKr, tableName, urlPrefix, date, egovVersion,
                jakartaValidation, pk, pkFields, fields, listFields, nonPkFields, formFields,
                route, queryContract, detailFields, layoutDensity, formColumnLayout,
                actionPlacement, searchPanelPlacement, List.of());
    }

    /** actionPlacement/searchPanelPlacement 도입 전 canonical 호출자 호환. */
    public CrudTemplateModel(
            String packageName, String domain, String domainLc, String domainKr,
            String tableName, String urlPrefix, String date, String egovVersion,
            boolean jakartaValidation, PkModel pk, List<FieldModel> pkFields,
            List<FieldModel> fields, List<FieldModel> listFields,
            List<FieldModel> nonPkFields, List<FieldModel> formFields, CrudRouteModel route,
            GenerationQueryContract queryContract, List<FieldModel> detailFields,
            LayoutDensity layoutDensity, FormColumnLayout formColumnLayout) {
        this(packageName, domain, domainLc, domainKr, tableName, urlPrefix, date, egovVersion,
                jakartaValidation, pk, pkFields, fields, listFields, nonPkFields, formFields,
                route, queryContract, detailFields, layoutDensity, formColumnLayout,
                ActionPlacement.TOP_RIGHT, SearchPanelPlacement.ABOVE_TABLE);
    }

    /** formColumnLayout 도입 전 canonical 호출자 호환. */
    public CrudTemplateModel(
            String packageName, String domain, String domainLc, String domainKr,
            String tableName, String urlPrefix, String date, String egovVersion,
            boolean jakartaValidation, PkModel pk, List<FieldModel> pkFields,
            List<FieldModel> fields, List<FieldModel> listFields,
            List<FieldModel> nonPkFields, List<FieldModel> formFields, CrudRouteModel route,
            GenerationQueryContract queryContract, List<FieldModel> detailFields,
            LayoutDensity layoutDensity) {
        this(packageName, domain, domainLc, domainKr, tableName, urlPrefix, date, egovVersion,
                jakartaValidation, pk, pkFields, fields, listFields, nonPkFields, formFields,
                route, queryContract, detailFields, layoutDensity, FormColumnLayout.SINGLE_COLUMN);
    }

    /** detailFields/layoutDensity 도입 전 canonical 호출자 호환. */
    public CrudTemplateModel(
            String packageName, String domain, String domainLc, String domainKr,
            String tableName, String urlPrefix, String date, String egovVersion,
            boolean jakartaValidation, PkModel pk, List<FieldModel> pkFields,
            List<FieldModel> fields, List<FieldModel> listFields,
            List<FieldModel> nonPkFields, List<FieldModel> formFields, CrudRouteModel route,
            GenerationQueryContract queryContract) {
        this(packageName, domain, domainLc, domainKr, tableName, urlPrefix, date, egovVersion,
                jakartaValidation, pk, pkFields, fields, listFields, nonPkFields, formFields,
                route, queryContract, null, LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN);
    }

    /** queryContract 도입 전 canonical 생성자 호환. */
    public CrudTemplateModel(
            String packageName, String domain, String domainLc, String domainKr,
            String tableName, String urlPrefix, String date, String egovVersion,
            boolean jakartaValidation, PkModel pk, List<FieldModel> pkFields,
            List<FieldModel> fields, List<FieldModel> listFields,
            List<FieldModel> nonPkFields, List<FieldModel> formFields, CrudRouteModel route) {
        this(packageName, domain, domainLc, domainKr, tableName, urlPrefix, date, egovVersion,
                jakartaValidation, pk, pkFields, fields, listFields, nonPkFields, formFields,
                route, GenerationQueryContract.empty(), null, LayoutDensity.STANDARD,
                FormColumnLayout.SINGLE_COLUMN);
    }

    /** route 미지정 호출자를 위한 하위 호환 생성자 — canonical URL만으로 route를 구성한다. */
    public CrudTemplateModel(
            String packageName, String domain, String domainLc, String domainKr,
            String tableName, String urlPrefix, String date, String egovVersion,
            boolean jakartaValidation, PkModel pk, List<FieldModel> pkFields,
            List<FieldModel> fields, List<FieldModel> listFields,
            List<FieldModel> nonPkFields, List<FieldModel> formFields) {
        this(packageName, domain, domainLc, domainKr, tableName, urlPrefix, date, egovVersion,
                jakartaValidation, pk, pkFields, fields, listFields, nonPkFields, formFields,
                CrudRouteModel.canonicalOnly(urlPrefix), GenerationQueryContract.empty(),
                null, LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN);
    }
}
