<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<#--
  =====================================================================
  eGovFrame SpringAI MCP CrudPromptBuilderTool
  Template : board/thymeleaf-list.html.ftl
  Design   : FTC 스타일 (KRDS)
  =====================================================================
  FreeMarker 변수
    classLabel   : 화면 한글명   (예: 공지·공고)
    classVar     : VO 변수명     (예: board)
    ClassName    : VO 클래스명   (예: Board)
    mappingUrl   : URL 접두어   (예: /board)
    pkName       : PK 필드명    (예: boardNo)
    listFields   : 목록 표시 필드 리스트 [{name, label, width?}]
    searchFields : 검색 필드 리스트      [{name, label}]
  =====================================================================
-->
<#assign TH_S = r'${'>
<#assign TH_E = r'}'>
<head>
    <title>${classLabel} 목록</title>
    <link rel="stylesheet" th:href="@{/css/ftc-portal.css}">
    <style>
        * { box-sizing: border-box; }
        a { text-decoration: none; color: inherit; }
        ul, ol { list-style: none; margin: 0; padding: 0; }
        .ftc-board-tr:hover td { background: #f0f5ff !important; }
        .ftc-btn-primary { display:inline-flex;align-items:center;gap:6px;padding:9px 22px;background:#083891;border:none;border-radius:4px;font-size:14px;font-weight:700;color:#fff;cursor:pointer;font-family:inherit; }
        .ftc-btn-outline { display:inline-flex;align-items:center;gap:5px;padding:9px 20px;border:1px solid #cdd1d5;border-radius:4px;font-size:13px;font-weight:600;color:#464c53;background:#fff;cursor:pointer;font-family:inherit; }
    </style>
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

      <!-- 페이지 제목 + 등록 버튼 -->
      <div style="display:flex;align-items:center;justify-content:space-between;padding-bottom:16px;border-bottom:2px solid #1e2124;margin-bottom:20px;">
        <h1 style="font-size:28px;font-weight:800;color:#1e2124;margin:0;">${classLabel}</h1>
        <div style="display:flex;gap:8px;">
          <button type="button" class="ftc-btn-outline" onclick="window.print()">프린트</button>
          <a th:href="@{${mappingUrl}/regist}" class="ftc-btn-primary">
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none" stroke="#fff" stroke-width="2"><line x1="6.5" y1="1" x2="6.5" y2="12"/><line x1="1" y1="6.5" x2="12" y2="6.5"/></svg>
            등록
          </a>
        </div>
      </div>

      <!-- 검색 폼 -->
      <form th:action="@{${mappingUrl}/list}" method="get" style="background:#f6f7f8;border:1px solid #e8eaec;border-radius:6px;padding:18px 20px;margin-bottom:16px;display:flex;gap:8px;align-items:center;">
        <input type="hidden" name="pageIndex" value="1">
        <!-- 검색 구분 -->
        <select name="searchCondition" th:field="*{searchVO.searchCondition}"
                style="padding:9px 32px 9px 12px;border:1px solid #cdd1d5;border-radius:4px;font-size:13px;color:#464c53;background:#fff;font-family:inherit;min-width:120px;appearance:none;">
          <option value="0">전체</option>
<#list searchFields as sf>
          <option value="${sf?index + 1}">${sf.label}</option>
</#list>
        </select>
        <!-- 검색어 -->
        <input type="text" name="searchKeyword" th:value="*{searchVO.searchKeyword}"
               placeholder="검색어를 입력하세요"
               style="flex:1;padding:9px 14px;border:1px solid #cdd1d5;border-radius:4px;font-size:14px;font-family:inherit;">
        <button type="submit" class="ftc-btn-primary">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="#fff" stroke-width="1.8"><circle cx="6" cy="6" r="4.5"/><line x1="9.5" y1="9.5" x2="13" y2="13"/></svg>
          검색
        </button>
        <a th:href="@{${mappingUrl}/list}" style="padding:9px 16px;border:1px solid #cdd1d5;border-radius:4px;font-size:13px;color:#464c53;background:#fff;">초기화</a>
      </form>

      <!-- 총건수 + 페이지 사이즈 -->
      <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:10px;">
        <span style="font-size:13px;color:#6d7882;">
          총 <strong th:text="${TH_S}paginationInfo.totalRecordCount${TH_E}" style="color:#083891;">0</strong>건
        </span>
        <form th:action="@{${mappingUrl}/list}" method="get">
          <input type="hidden" name="pageIndex" value="1">
          <input type="hidden" name="searchCondition" th:value="*{searchVO.searchCondition}">
          <input type="hidden" name="searchKeyword"   th:value="*{searchVO.searchKeyword}">
          <select name="recordCountPerPage" th:field="*{searchVO.recordCountPerPage}"
                  onchange="this.form.submit()"
                  style="padding:6px 28px 6px 10px;border:1px solid #cdd1d5;border-radius:4px;font-size:13px;font-family:inherit;appearance:none;">
            <option value="10">10개</option>
            <option value="20">20개</option>
            <option value="50">50개</option>
          </select>
        </form>
      </div>

      <!-- 목록 테이블 (krds-table-wrap tbl col) -->
      <table style="width:100%;border-collapse:collapse;font-size:14px;">
        <thead>
          <tr style="background:#f6f7f8;border-top:2px solid #1e2124;">
            <th style="padding:12px 8px;text-align:center;font-weight:700;color:#464c53;width:70px;border-bottom:1px solid #e8eaec;">번호</th>
<#list listFields as field>
            <th style="padding:12px 12px;text-align:left;font-weight:700;color:#464c53;<#if field.width??>width:${field.width};</#if>border-bottom:1px solid #e8eaec;">${field.label}</th>
</#list>
            <th style="padding:12px 8px;text-align:center;font-weight:700;color:#464c53;width:110px;border-bottom:1px solid #e8eaec;">등록일</th>
            <th style="padding:12px 8px;text-align:center;font-weight:700;color:#464c53;width:80px;border-bottom:1px solid #e8eaec;">조회수</th>
            <th style="padding:12px 8px;text-align:center;font-weight:700;color:#464c53;width:60px;border-bottom:1px solid #e8eaec;">첨부</th>
          </tr>
        </thead>
        <tbody>
          <!-- 공지 고정 행 -->
          <tr th:each="${classVar}VO : ${TH_S}${classVar}NoticList${TH_E}"
              style="background:#fafbff;border-bottom:1px solid #e8eaec;">
            <td style="padding:12px 8px;text-align:center;">
              <span style="font-size:11px;font-weight:700;color:#fff;background:#083891;padding:2px 7px;border-radius:3px;">공지</span>
            </td>
<#list listFields as field>
  <#if field?is_first>
            <td style="padding:12px 12px;" th:colspan="${listFields?size}">
              <a th:href="@{${mappingUrl}/detail(${pkName}=${TH_S}${classVar}VO.${pkName}${TH_E})}"
                 th:text="${TH_S}${classVar}VO.${field.name}${TH_E}"
                 style="color:#083891;font-weight:600;"></a>
            </td>
  <#else>
            <#-- merged into title column above -->
  </#if>
</#list>
            <td th:text="${TH_S}${classVar}VO.regDate${TH_E}"
                style="padding:12px 8px;text-align:center;color:#8a949e;font-size:13px;"></td>
            <td th:text="${TH_S}${classVar}VO.readCnt${TH_E}"
                style="padding:12px 8px;text-align:center;color:#8a949e;font-size:13px;"></td>
            <td style="padding:12px 8px;text-align:center;">
              <span th:if="${TH_S}${classVar}VO.fileCnt > 0${TH_E}">
                <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="#256ef4" stroke-width="1.5"><path d="M7 1v8M4 6l3 3 3-3"/><line x1="1" y1="13" x2="13" y2="13"/></svg>
              </span>
            </td>
          </tr>
          <!-- 일반 행 -->
          <tr th:each="${classVar}VO, iterStat : ${TH_S}${classVar}List${TH_E}"
              class="ftc-board-tr"
              style="border-bottom:1px solid #f0f1f3;cursor:pointer;"
              th:onclick="|location.href='@{${mappingUrl}/detail(${pkName}=${TH_S}${classVar}VO.${pkName}${TH_E})}'|">
            <td th:text="${TH_S}paginationInfo.totalRecordCount - paginationInfo.firstRecordIndex - iterStat.index${TH_E}"
                style="padding:12px 8px;text-align:center;color:#8a949e;font-size:13px;"></td>
<#list listFields as field>
  <#if field?is_first>
            <td style="padding:12px 12px;">
              <a th:href="@{${mappingUrl}/detail(${pkName}=${TH_S}${classVar}VO.${pkName}${TH_E})}"
                 th:text="${TH_S}${classVar}VO.${field.name}${TH_E}"
                 style="color:#1e2124;"></a>
            </td>
  <#else>
            <td th:text="${TH_S}${classVar}VO.${field.name}${TH_E}"
                style="padding:12px 12px;color:#464c53;"></td>
  </#if>
</#list>
            <td th:text="${TH_S}${classVar}VO.regDate${TH_E}"
                style="padding:12px 8px;text-align:center;color:#8a949e;font-size:13px;"></td>
            <td th:text="${TH_S}${classVar}VO.readCnt${TH_E}"
                style="padding:12px 8px;text-align:center;color:#8a949e;font-size:13px;"></td>
            <td style="padding:12px 8px;text-align:center;">
              <span th:if="${TH_S}${classVar}VO.fileCnt > 0${TH_E}">
                <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="#256ef4" stroke-width="1.5"><path d="M7 1v8M4 6l3 3 3-3"/><line x1="1" y1="13" x2="13" y2="13"/></svg>
              </span>
            </td>
          </tr>
          <!-- 데이터 없음 -->
          <tr th:if="${TH_S}${classVar}List.empty${TH_E}">
            <td colspan="${listFields?size + 4}"
                style="padding:52px 0;text-align:center;color:#8a949e;font-size:14px;">
              검색 결과가 없습니다.
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 페이지네이션 (krds-pagination) -->
      <div style="display:flex;align-items:center;justify-content:center;gap:4px;margin-top:28px;">
        <a th:href="@{${mappingUrl}/list(pageIndex=1,searchCondition=*{searchVO.searchCondition},searchKeyword=*{searchVO.searchKeyword})}"
           style="width:32px;height:32px;border:1px solid #e8eaec;border-radius:4px;background:#fff;color:#464c53;font-size:13px;display:flex;align-items:center;justify-content:center;">‹‹</a>
        <a th:href="@{${mappingUrl}/list(pageIndex=${TH_S}paginationInfo.currentPageNo > 1 ? paginationInfo.currentPageNo - 1 : 1${TH_E},searchCondition=*{searchVO.searchCondition},searchKeyword=*{searchVO.searchKeyword})}"
           style="width:32px;height:32px;border:1px solid #e8eaec;border-radius:4px;background:#fff;color:#464c53;font-size:13px;display:flex;align-items:center;justify-content:center;">‹</a>
        <th:block th:each="pageNum : ${TH_S}#numbers.sequence(paginationInfo.firstPageNoOnPageList, paginationInfo.lastPageNoOnPageList)${TH_E}">
          <a th:href="@{${mappingUrl}/list(pageIndex=${TH_S}pageNum${TH_E},searchCondition=*{searchVO.searchCondition},searchKeyword=*{searchVO.searchKeyword})}"
             th:text="${TH_S}pageNum${TH_E}"
             th:style="${TH_S}pageNum == paginationInfo.currentPageNo ? 'width:32px;height:32px;border:1px solid #083891;border-radius:4px;background:#083891;color:#fff;font-size:13px;font-weight:700;display:flex;align-items:center;justify-content:center;' : 'width:32px;height:32px;border:1px solid #e8eaec;border-radius:4px;background:#fff;color:#464c53;font-size:13px;display:flex;align-items:center;justify-content:center;'${TH_E}">1</a>
        </th:block>
        <a th:href="@{${mappingUrl}/list(pageIndex=${TH_S}paginationInfo.currentPageNo < paginationInfo.totalPageCount ? paginationInfo.currentPageNo + 1 : paginationInfo.totalPageCount${TH_E},searchCondition=*{searchVO.searchCondition},searchKeyword=*{searchVO.searchKeyword})}"
           style="width:32px;height:32px;border:1px solid #e8eaec;border-radius:4px;background:#fff;color:#464c53;font-size:13px;display:flex;align-items:center;justify-content:center;">›</a>
        <a th:href="@{${mappingUrl}/list(pageIndex=${TH_S}paginationInfo.totalPageCount${TH_E},searchCondition=*{searchVO.searchCondition},searchKeyword=*{searchVO.searchKeyword})}"
           style="width:32px;height:32px;border:1px solid #e8eaec;border-radius:4px;background:#fff;color:#464c53;font-size:13px;display:flex;align-items:center;justify-content:center;">››</a>
      </div>

    </div><!-- /CONTENT -->
  </div><!-- /wrapper -->
</div>

</th:block>
</body>
</html>
