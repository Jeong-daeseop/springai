<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<#--
  =====================================================================
  eGovFrame SpringAI MCP CrudPromptBuilderTool
  Template : board/thymeleaf-detail.html.ftl
  Design   : FTC 스타일 (KRDS)
  =====================================================================
  FreeMarker 변수
    classLabel   : 화면 한글명   (예: 공지·공고)
    classVar     : VO 변수명     (예: board)
    ClassName    : VO 클래스명   (예: Board)
    mappingUrl   : URL 접두어   (예: /board)
    pkName       : PK 필드명    (예: boardNo)
    detailFields : 상세 표시 필드 리스트 [{name, label, colspan?}]
    hasFileYn    : 첨부파일 여부  (Y/N)
    hasContentYn : 본문(내용) 여부(Y/N)
  =====================================================================
-->
<#assign TH_S = r'${'>
<#assign TH_E = r'}'>
<head>
    <title>${classLabel} 상세</title>
    <link rel="stylesheet" th:href="@{/css/ftc-portal.css}">
    <style>* { box-sizing: border-box; } a { text-decoration: none; color: inherit; }</style>
</head>
<body>
<th:block layout:fragment="content">

<div style="font-family:'Pretendard GOV',-apple-system,sans-serif;color:#1e2124;background:#fff;line-height:1.5;">
  <div style="max-width:1200px;margin:0 auto;padding:32px 24px 60px;display:flex;gap:32px;align-items:flex-start;">

    <!-- ① LNB -->
    <aside th:replace="~{layout/lnb :: lnb}"></aside>

    <!-- ② CONTENT -->
    <div style="flex:1;min-width:0;">

      <!-- 브레드크럼 -->
      <nav style="display:flex;align-items:center;gap:6px;font-size:13px;color:#8a949e;margin-bottom:20px;" th:replace="~{layout/breadcrumb :: breadcrumb}"></nav>

      <!-- 페이지 제목 + 상단 버튼 -->
      <div style="display:flex;align-items:center;justify-content:space-between;padding-bottom:16px;border-bottom:2px solid #1e2124;margin-bottom:24px;">
        <h1 style="font-size:26px;font-weight:800;color:#1e2124;margin:0;">${classLabel} 상세</h1>
        <div style="display:flex;gap:8px;">
          <a th:href="@{${mappingUrl}/list}"
             style="display:inline-flex;align-items:center;gap:5px;padding:8px 18px;border:1px solid #cdd1d5;border-radius:4px;font-size:13px;font-weight:600;color:#464c53;">
            목록
          </a>
          <a th:href="@{${mappingUrl}/updateView(${pkName}=${TH_S}${classVar}VO.${pkName}${TH_E})}"
             style="display:inline-flex;align-items:center;gap:5px;padding:8px 18px;border:1px solid #256ef4;border-radius:4px;font-size:13px;font-weight:600;color:#256ef4;">
            <svg width="12" height="12" viewBox="0 0 12 12" fill="none" stroke="#256ef4" stroke-width="1.5"><path d="M8 1.5l2.5 2.5-6.5 6.5H1.5V8L8 1.5z"/></svg>
            수정
          </a>
        </div>
      </div>

      <!-- 게시글 정의 테이블 (krds-table-wrap tbl col) -->
      <table style="width:100%;border-collapse:collapse;font-size:14px;border-top:2px solid #1e2124;">
        <colgroup>
          <col style="width:130px">
          <col>
          <col style="width:130px">
          <col>
        </colgroup>
        <tbody>
          <!-- PK 행 -->
          <tr style="border-bottom:1px solid #e8eaec;">
            <th scope="row" style="padding:14px 16px;background:#f6f7f8;font-weight:700;color:#464c53;text-align:left;white-space:nowrap;">번호</th>
            <td th:text="${TH_S}${classVar}VO.${pkName}${TH_E}"
                style="padding:14px 16px;color:#1e2124;"></td>
            <th scope="row" style="padding:14px 16px;background:#f6f7f8;font-weight:700;color:#464c53;text-align:left;white-space:nowrap;">등록일</th>
            <td th:text="${TH_S}${classVar}VO.regDate${TH_E}"
                style="padding:14px 16px;color:#1e2124;"></td>
          </tr>
<#-- 2컬럼씩 짝을 이루는 필드 행 출력 -->
<#list detailFields as field>
  <#if field?is_odd_item>
          <tr style="border-bottom:1px solid #e8eaec;">
            <th scope="row" style="padding:14px 16px;background:#f6f7f8;font-weight:700;color:#464c53;text-align:left;white-space:nowrap;">${field.label}</th>
            <td th:text="${TH_S}${classVar}VO.${field.name}${TH_E}"
                style="padding:14px 16px;color:#1e2124;"><#if field.colspan??>colspan="${field.colspan}"</#if></td>
    <#if field?has_next>
      <#assign nf = detailFields[field?index + 1]>
            <th scope="row" style="padding:14px 16px;background:#f6f7f8;font-weight:700;color:#464c53;text-align:left;white-space:nowrap;">${nf.label}</th>
            <td th:text="${TH_S}${classVar}VO.${nf.name}${TH_E}"
                style="padding:14px 16px;color:#1e2124;"></td>
    <#else>
            <th scope="row" style="padding:14px 16px;background:#f6f7f8;"></th>
            <td style="padding:14px 16px;"></td>
    </#if>
          </tr>
  </#if>
</#list>
<#if hasFileYn == 'Y'>
          <!-- 첨부파일 행 -->
          <tr style="border-bottom:1px solid #e8eaec;">
            <th scope="row" style="padding:14px 16px;background:#f6f7f8;font-weight:700;color:#464c53;text-align:left;vertical-align:top;">첨부파일</th>
            <td colspan="3" style="padding:14px 16px;">
              <div th:if="${TH_S}not #lists.isEmpty(fileList)${TH_E}" style="display:flex;flex-direction:column;gap:8px;">
                <a th:each="file : ${TH_S}fileList${TH_E}"
                   th:href="@{/cmm/fms/FileDown.do(atchFileId=${TH_S}file.atchFileId${TH_E},fileSn=${TH_S}file.fileSn${TH_E})}"
                   style="display:inline-flex;align-items:center;gap:7px;font-size:13px;color:#256ef4;">
                  <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="#256ef4" stroke-width="1.5"><path d="M7 1v8M4 6l3 3 3-3"/><line x1="1" y1="13" x2="13" y2="13"/></svg>
                  <span th:text="${TH_S}file.orignlFileNm${TH_E}"></span>
                  <span th:text="'(' + ${TH_S}file.fileSize${TH_E} + ')'" style="color:#8a949e;font-size:12px;"></span>
                </a>
              </div>
              <span th:if="${TH_S}#lists.isEmpty(fileList)${TH_E}" style="font-size:13px;color:#8a949e;">첨부파일 없음</span>
            </td>
          </tr>
</#if>
<#if hasContentYn == 'Y'>
          <!-- 본문 내용 -->
          <tr>
            <th scope="row" style="padding:14px 16px;background:#f6f7f8;font-weight:700;color:#464c53;text-align:left;vertical-align:top;">내용</th>
            <td colspan="3" style="padding:24px 20px;min-height:220px;vertical-align:top;">
              <div th:utext="${TH_S}${classVar}VO.cn${TH_E}"
                   style="font-size:15px;color:#1e2124;line-height:1.9;"></div>
            </td>
          </tr>
</#if>
        </tbody>
      </table>

      <!-- 이전글 / 다음글 -->
      <div style="border-top:1px solid #e8eaec;">
        <div th:if="${TH_S}nextVO != null${TH_E}" style="display:flex;align-items:center;gap:16px;padding:13px 0;border-bottom:1px solid #f0f1f3;">
          <span style="display:flex;align-items:center;gap:6px;font-size:13px;font-weight:700;color:#464c53;width:60px;flex:none;">
            <svg width="12" height="12" viewBox="0 0 12 12" fill="none" stroke="#464c53" stroke-width="1.5"><polyline points="2,8 6,4 10,8"/></svg>
            다음글
          </span>
          <a th:href="@{${mappingUrl}/detail(${pkName}=${TH_S}nextVO.${pkName}${TH_E})}"
             th:text="${TH_S}nextVO.nttSj${TH_E}"
             style="font-size:14px;color:#1e2124;flex:1;"></a>
          <span th:text="${TH_S}nextVO.regDate${TH_E}" style="font-size:13px;color:#8a949e;flex:none;"></span>
        </div>
        <div th:if="${TH_S}prevVO != null${TH_E}" style="display:flex;align-items:center;gap:16px;padding:13px 0;border-bottom:1px solid #f0f1f3;">
          <span style="display:flex;align-items:center;gap:6px;font-size:13px;font-weight:700;color:#464c53;width:60px;flex:none;">
            <svg width="12" height="12" viewBox="0 0 12 12" fill="none" stroke="#464c53" stroke-width="1.5"><polyline points="2,4 6,8 10,4"/></svg>
            이전글
          </span>
          <a th:href="@{${mappingUrl}/detail(${pkName}=${TH_S}prevVO.${pkName}${TH_E})}"
             th:text="${TH_S}prevVO.nttSj${TH_E}"
             style="font-size:14px;color:#1e2124;flex:1;"></a>
          <span th:text="${TH_S}prevVO.regDate${TH_E}" style="font-size:13px;color:#8a949e;flex:none;"></span>
        </div>
      </div>

      <!-- 하단 버튼 -->
      <div style="display:flex;justify-content:space-between;align-items:center;margin-top:28px;">
        <a th:href="@{${mappingUrl}/list}"
           style="display:inline-flex;align-items:center;gap:6px;padding:10px 28px;border:1px solid #cdd1d5;border-radius:4px;font-size:14px;font-weight:600;color:#464c53;">
          목록으로
        </a>
        <div style="display:flex;gap:8px;">
          <a th:href="@{${mappingUrl}/updateView(${pkName}=${TH_S}${classVar}VO.${pkName}${TH_E})}"
             style="display:inline-flex;align-items:center;gap:5px;padding:10px 28px;border:1px solid #256ef4;border-radius:4px;font-size:14px;font-weight:600;color:#256ef4;">
            수정
          </a>
          <form th:action="@{${mappingUrl}/delete}" method="post" style="display:inline;"
                onsubmit="return confirm('삭제하시겠습니까?');">
            <input type="hidden" name="${pkName}" th:value="${TH_S}${classVar}VO.${pkName}${TH_E}">
            <input type="hidden" name="_csrf" th:value="${TH_S}_csrf.token${TH_E}">
            <button type="submit"
                    style="display:inline-flex;align-items:center;gap:5px;padding:10px 28px;border:1px solid #d9363e;border-radius:4px;font-size:14px;font-weight:600;color:#d9363e;background:#fff;cursor:pointer;font-family:inherit;">
              삭제
            </button>
          </form>
        </div>
      </div>

    </div><!-- /CONTENT -->
  </div><!-- /wrapper -->
</div>

</th:block>
</body>
</html>
