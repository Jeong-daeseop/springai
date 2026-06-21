package com.krdevops.springai.model.board;

import com.krdevops.springai.model.crud.FieldModel;

import java.util.List;

/**
 * eGovFrame 게시판(BBS) FreeMarker 렌더링 모델.
 *
 * <p>게시글 테이블(COMTNBBS) + 마스터(COMTNBBSMASTER) + 사용권한(COMTNBBSUSE) +
 * 첨부파일(COMTNFILE/COMTNFILEDETAIL) 연동을 표현한다.
 * 복합 PK(BBS_ID, NTT_ID)는 Mapper XML(columnName)·Controller/View(javaName) 양쪽에서
 * 모두 필요하므로 {@link FieldModel} 타입으로 보관한다.
 */
public record BoardTemplateModel(
        String packageName,           // egovframework.let.bbs
        String domain,                // Bbs
        String domainLc,              // bbs
        String domainKr,              // BBS
        String tableName,             // COMTNBBS
        String masterTableName,       // COMTNBBSMASTER
        String useTableName,          // COMTNBBSUSE (null 허용)
        String urlPrefix,             // /bbs/bbs
        String date,
        String egovVersion,
        boolean jakartaValidation,
        // 복합 PK — columnName(Mapper XML용) + javaName(Controller/View용) 모두 필요
        FieldModel bbsId,             // BBS_ID / bbsId
        FieldModel nttId,             // NTT_ID / nttId
        // 첨부파일
        boolean hasFile,
        FieldModel atchFileId,        // ATCH_FILE_ID / atchFileId (hasFile=false면 null)
        String fileDetailTableName,   // COMTNFILEDETAIL (null 허용)
        // 필드
        List<FieldModel> fields,       // COMTNBBS 전체 필드
        List<FieldModel> listFields,   // 목록 화면 노출 필드
        List<FieldModel> insertFields, // INSERT SQL용 (BBS_ID·NTT_ID 포함, 자동관리 컬럼 제외)
        List<FieldModel> formFields,   // 등록/수정 폼 필드 (복합PK 제외, UI 표시용)
        List<FieldModel> searchFields, // 검색 조건 필드
        boolean noticeAtExists         // NOTICE_AT 컬럼 존재 여부 (ORDER BY 조건부 생성용)
) {}
