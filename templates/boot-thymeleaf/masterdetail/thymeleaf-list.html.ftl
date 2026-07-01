<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<#--
  =====================================================================
  eGovFrame SpringAI MCP CrudPromptBuilderTool
  Template : masterdetail/thymeleaf-list.html.ftl
  Design   : FTC 스타일 (KRDS) — CRUD 마스터-디테일 목록형
  =====================================================================
  FreeMarker 변수 (Master)
    mClassLabel  : 마스터 화면 한글명  (예: 업무 분류)
    mClassVar    : 마스터 VO 변수명    (예: masterVO)
    mMappingUrl  : 마스터 URL 접두어   (예: /master)
    mPkName      : 마스터 PK 필드명   (예: masterId)
    mListFields  : 마스터 목록 필드    [{name, label, width?}]
    mSearchFields: 마스터 검색 필드    [{name, label}]
  =====================================================================
-->
<#assign TH_S = r'${'>
<#assign TH_E = r'}'>
<head>
    <title>${mClassLabel} 목록</title>
    <link rel="stylesheet" th:href="@{/css/ftc-portal.css}">
    <style>
        * { box-sizing: border-box; }
        a { text-decoration: none; color: inherit; }
        .crud-tr { cursor: pointer; transition: background 0.1s; }
        .crud-tr:hover td { background: #f0f5ff !important; }
        .crud-del-btn { display:inline-flex;align-items:center;gap:3px;padding:4px 12px;border:1px solid #f5c0c2;border-radius:3px;font-size:12px;font-weight:600;color:#d9363e;background:#fff;cursor:pointer;font-family:inherit; }
        .crud-edit-btn { display:inline-flex;align-items:center;gap:3px;padding:4px 12px;border:1px solid #b8d0f8;border-radius:3px;font-size:12px;font-weight:600;color:#256ef4;background:#fff;cursor:pointer;font-family:inherit; }
    </style>
</head>
<body>
<th:block layout:fragment="content">

<div style="font-family:'Pretendard GOV',-apple-system,sans-serif;color:#1e2124;background:#fff;line-height:1.5;">
  <div style="max-width:1200px;margin:0 auto;padding:32px 24px 60px;display:flex;gap:32px;align-items:flex-start;">

    <!-- ① LNB (업무관리) -->
    <aside th:replace="~{layout/lnb :: lnb}"></aside>

    <!-- ② CONTENT -->
    <div style="flex:1;min-width:0;">

      <!-- 브레드크럼 -->
      <nav style="display:flex;align-items:center;gap:6px;font-size:13px;color:#8a949e;margin-bottom:20px;flex-wrap:nowrap;white-space:nowrap;"
           th:replace="~{layout/breadcrumb :: breadcrumb}"></nav>

      <!-- 페이지 제목 + 등록 버튼 -->
      <div style="display:flex;align-items:center;justify-content:space-between;padding-bottom:16px;border-bottom:2px solid #1e2124;margin-bottom:20px;">
        <h1 style="font-size:26px;font-weight:800;color:#1e2124;margin:0;">${mClassLabel} 목록</h1>
        <a th:href="@{${mMappingUrl}/regist}"
           style="display:inline-flex;align-items:center;gap:6px;padding:9px 22px;background:#083891;border-radius:4px;font-size:14px;font-weight:700;color:#fff;">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="#fff" stroke-width="2"><line x1="7" y1="1" x2="7" y2="13"/><line x1="1" y1="7" x2="13" y2="7"/></svg>
          등록
        </a>
      </div>

      <!-- 검색 폼 (krds 스타일) -->
      <form th:action="@{${mMappingUrl}/list}" method="get"
            style="background:#f6f7f8;border:1px solid #e8eaec;border-radius:6px;padding:18px 20px;margin-bottom:16px;display:flex;gap:8px;align-items:center;">
        <input type="hidden" name="pageIndex" value="1">
        <select name="searchCondition" th:field="*{searchVO.searchCondition}"
                style="padding:9px 32px 9px 12px;border:1px solid #cdd1d5;border-radius:4px;font-size:13px;color:#464c53;background:#fff;font-family:inherit;min-width:120px;appearance:none;">
          <option value="0">전체</option>
<#list mSearchFields as sf>
          <option value="${sf?index + 1}">${sf.label}</option>
</#list>
        </select>
        <input type="text" name="searchKeyword" th:value="*{searchVO.searchKeyword}"
               placeholder="검색어를 입력하세요"
               style="flex:1;padding:9px 14px;border:1px solid #cdd1d5;border-radius:4px;font-size:14px;font-family:inherit;">
        <button type="submit"
                style="display:inline-flex;align-items:center;gap:6px;padding:9px 24px;background:#083891;border:none;border-radius:4px;font-size:14px;font-weight:700;color:#fff;cursor:pointer;font-family:inherit;">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="#fff" stroke-width="1.8"><circle cx="6" cy="6" r="4.5"/><line x1="9.5" y1="9.5" x2="13" y2="13"/></svg>
          검색
        </button>
        <a th:href="@{${mMappingUrl}/list}"
           style="padding:9px 16px;border:1px solid #cdd1d5;border-radius:4px;font-size:13px;color:#464c53;background:#fff;">초기화</a>
      </form>

      <!-- 총건수 + 페이지사이즈 + 선택삭제 -->
      <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:10px;">
        <span style="font-size:13px;color:#6d7882;">
          총 <strong th:text="${TH_S}paginationInfo.totalRecordCount${TH_E}" style="color:#083891;">0</strong>건
        </span>
        <div style="display:flex;align-items:center;gap:8px;">
          <form id="batchDeleteForm" th:action="@{${mMappingUrl}/deleteList}" method="post" style="display:inline;">
            <input type="hidden" name="_csrf" th:value="${TH_S}_csrf.token${TH_E}">
            <button type="submit" onclick="return confirmBatchDelete()"
                    style="display:inline-flex;align-items:center;gap:5px;padding:6px 16px;border:1px solid #f5c0c2;border-radius:4px;font-size:13px;font-weight:600;color:#d9363e;background:#fff;cursor:pointer;font-family:inherit;">
              선택 삭제
            </button>
          </form>
          <form th:action="@{${mMappingUrl}/list}" method="get">
            <input type="hidden" name="pageIndex" value="1">
            <select name="recordCountPerPage" th:field="*{searchVO.recordCountPerPage}"
                    onchange="this.form.submit()"
                    style="padding:6px 28px 6px 10px;border:1px solid #cdd1d5;border-radius:4px;font-size:13px;font-family:inherit;appearance:none;">
              <option value="10">10개</option>
              <option value="20">20개</option>
              <option value="50">50개</option>
            </select>
          </form>
        </div>
      </div>

      <!-- 마스터 목록 테이블 (krds-table-wrap tbl col) -->
      <form id="listForm">
        <input type="hidden" name="_csrf" th:value="${TH_S}_csrf.token${TH_E}">
        <div style="border:1px solid #e8eaec;border-radius:4px;overflow:hidden;">
          <table style="width:100%;border-collapse:collapse;font-size:14px;">
            <thead>
              <tr style="background:#f6f7f8;border-top:2px solid #1e2124;">
                <th style="padding:12px 10px;text-align:center;width:44px;border-bottom:1px solid #e8eaec;">
                  <input type="checkbox" id="checkAll" onchange="toggleAll(this)"
                         style="width:15px;height:15px;accent-color:#256ef4;cursor:pointer;">
                </th>
                <th style="padding:12px 8px;text-align:center;font-weight:700;color:#464c53;width:60px;border-bottom:1px solid #e8eaec;">번호</th>
<#list mListFields as field>
                <th style="padding:12px 12px;text-align:left;font-weight:700;color:#464c53;<#if field.width??>width:${field.width};</#if>border-bottom:1px solid #e8eaec;">${field.label}</th>
</#list>
                <th style="padding:12px 8px;text-align:center;font-weight:700;color:#464c53;width:70px;border-bottom:1px solid #e8eaec;">상태</th>
                <th style="padding:12px 8px;text-align:center;font-weight:700;color:#464c53;width:100px;border-bottom:1px solid #e8eaec;">등록일</th>
                <th style="padding:12px 8px;text-align:center;font-weight:700;color:#464c53;width:120px;border-bottom:1px solid #e8eaec;">관리</th>
              </tr>
            </thead>
            <tbody>
              <tr th:each="${mClassVar}, iterStat : ${TH_S}${mClassVar}List${TH_E}"
                  class="crud-tr"
                  style="border-bottom:1px solid #f0f1f3;"
                  th:onclick="|location.href='@{${mMappingUrl}/detail(${mPkName}=${TH_S}${mClassVar}.${mPkName}${TH_E})}'|">
                <td style="padding:12px 10px;text-align:center;" onclick="event.stopPropagation()">
                  <input type="checkbox" name="deleteIds" th:value="${TH_S}${mClassVar}.${mPkName}${TH_E}"
                         style="width:15px;height:15px;accent-color:#256ef4;cursor:pointer;">
                </td>
                <td th:text="${TH_S}paginationInfo.totalRecordCount - paginationInfo.firstRecordIndex - iterStat.index${TH_E}"
                    style="padding:12px 8px;text-align:center;color:#8a949e;font-size:13px;"></td>
<#list mListFields as field>
  <#if field?is_first>
                <td style="padding:12px 12px;">
                  <a th:href="@{${mMappingUrl}/detail(${mPkName}=${TH_S}${mClassVar}.${mPkName}${TH_E})}"
                     th:text="${TH_S}${mClassVar}.${field.name}${TH_E}"
                     style="color:#1e2124;font-weight:500;" onclick="event.stopPropagation()"></a>
                </td>
  <#else>
                <td th:text="${TH_S}${mClassVar}.${field.name}${TH_E}"
                    style="padding:12px 12px;color:#464c53;"></td>
  </#if>
</#list>
                <td style="padding:12px 8px;text-align:center;">
                  <span th:if="${TH_S}'Y'.equals(${mClassVar}.useAt)${TH_E}"
                        style="display:inline-block;padding:3px 10px;background:#e8f5ee;border-radius:20px;font-size:12px;font-weight:700;color:#1a7f42;">사용</span>
                  <span th:unless="${TH_S}'Y'.equals(${mClassVar}.useAt)${TH_E}"
                        style="display:inline-block;padding:3px 10px;background:#f6f7f8;border-radius:20px;font-size:12px;font-weight:700;color:#8a949e;">중지</span>
                </td>
                <td th:text="${TH_S}${mClassVar}.regDt${TH_E}"
                    style="padding:12px 8px;text-align:center;color:#8a949e;font-size:12px;"></td>
                <td style="padding:12px 8px;text-align:center;" onclick="event.stopPropagation()">
                  <div style="display:flex;justify-content:center;gap:4px;">
                    <a th:href="@{${mMappingUrl}/updateView(${mPkName}=${TH_S}${mClassVar}.${mPkName}${TH_E})}"
                       class="crud-edit-btn">
                      <svg width="11" height="11" viewBox="0 0 11 11" fill="none" stroke="#256ef4" stroke-width="1.4"><path d="M7.5 1l2.5 2.5-6 6H1.5v-2.5l6-6z"/></svg>수정
                    </a>
                    <form th:action="@{${mMappingUrl}/delete}" method="post" style="display:inline;"
                          onsubmit="event.stopPropagation(); return confirm('삭제하시겠습니까?');">
                      <input type="hidden" name="${mPkName}" th:value="${TH_S}${mClassVar}.${mPkName}${TH_E}">
                      <input type="hidden" name="_csrf" th:value="${TH_S}_csrf.token${TH_E}">
                      <button type="submit" class="crud-del-btn">
                        <svg width="11" height="11" viewBox="0 0 11 11" fill="none" stroke="#d9363e" stroke-width="1.4"><polyline points="1.5,3 9.5,3"/><path d="M3.5 3V2h4v1"/><rect x="2" y="3" width="7" height="7" rx="1"/></svg>삭제
                      </button>
                    </form>
                  </div>
                </td>
              </tr>
              <!-- 데이터 없음 -->
              <tr th:if="${TH_S}${mClassVar}List.empty${TH_E}">
                <td colspan="${mListFields?size + 5}"
                    style="padding:52px 0;text-align:center;color:#8a949e;font-size:14px;">
                  <div style="display:flex;flex-direction:column;align-items:center;gap:10px;">
                    <svg width="36" height="36" viewBox="0 0 36 36" fill="none"><rect x="4" y="4" width="28" height="28" rx="3" stroke="#cdd1d5" stroke-width="1.6"/><line x1="10" y1="13" x2="26" y2="13" stroke="#cdd1d5" stroke-width="1.4"/><line x1="10" y1="18" x2="26" y2="18" stroke="#cdd1d5" stroke-width="1.4"/></svg>
                    검색 결과가 없습니다.
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </form>

      <!-- 페이지네이션 (krds-pagination) -->
      <div style="display:flex;align-items:center;justify-content:center;gap:4px;margin-top:24px;">
        <a th:href="@{${mMappingUrl}/list(pageIndex=1,searchCondition=*{searchVO.searchCondition},searchKeyword=*{searchVO.searchKeyword})}"
           style="width:32px;height:32px;border:1px solid #e8eaec;border-radius:4px;background:#fff;color:#464c53;font-size:13px;display:flex;align-items:center;justify-content:center;">‹‹</a>
        <a th:href="@{${mMappingUrl}/list(pageIndex=${TH_S}paginationInfo.currentPageNo > 1 ? paginationInfo.currentPageNo - 1 : 1${TH_E},searchCondition=*{searchVO.searchCondition},searchKeyword=*{searchVO.searchKeyword})}"
           style="width:32px;height:32px;border:1px solid #e8eaec;border-radius:4px;background:#fff;color:#464c53;font-size:13px;display:flex;align-items:center;justify-content:center;">‹</a>
        <th:block th:each="pageNum : ${TH_S}#numbers.sequence(paginationInfo.firstPageNoOnPageList, paginationInfo.lastPageNoOnPageList)${TH_E}">
          <a th:href="@{${mMappingUrl}/list(pageIndex=${TH_S}pageNum${TH_E},searchCondition=*{searchVO.searchCondition},searchKeyword=*{searchVO.searchKeyword})}"
             th:text="${TH_S}pageNum${TH_E}"
             th:style="${TH_S}pageNum == paginationInfo.currentPageNo ? 'width:32px;height:32px;border:1px solid #083891;border-radius:4px;background:#083891;color:#fff;font-size:13px;font-weight:700;display:flex;align-items:center;justify-content:center;' : 'width:32px;height:32px;border:1px solid #e8eaec;border-radius:4px;background:#fff;color:#464c53;font-size:13px;display:flex;align-items:center;justify-content:center;'${TH_E}">1</a>
        </th:block>
        <a th:href="@{${mMappingUrl}/list(pageIndex=${TH_S}paginationInfo.currentPageNo < paginationInfo.totalPageCount ? paginationInfo.currentPageNo + 1 : paginationInfo.totalPageCount${TH_E},searchCondition=*{searchVO.searchCondition},searchKeyword=*{searchVO.searchKeyword})}"
           style="width:32px;height:32px;border:1px solid #e8eaec;border-radius:4px;background:#fff;color:#464c53;font-size:13px;display:flex;align-items:center;justify-content:center;">›</a>
        <a th:href="@{${mMappingUrl}/list(pageIndex=${TH_S}paginationInfo.totalPageCount${TH_E},searchCondition=*{searchVO.searchCondition},searchKeyword=*{searchVO.searchKeyword})}"
           style="width:32px;height:32px;border:1px solid #e8eaec;border-radius:4px;background:#fff;color:#464c53;font-size:13px;display:flex;align-items:center;justify-content:center;">››</a>
      </div>

    </div><!-- /CONTENT -->
  </div><!-- /wrapper -->
</div>

<script>
function toggleAll(cb) {
  document.querySelectorAll('input[name="deleteIds"]').forEach(c => c.checked = cb.checked);
}
function confirmBatchDelete() {
  const checked = document.querySelectorAll('input[name="deleteIds"]:checked');
  if (checked.length === 0) { alert('삭제할 항목을 선택하세요.'); return false; }
  if (!confirm(checked.length + '건을 삭제하시겠습니까?')) return false;
  checked.forEach(c => {
    const input = document.createElement('input');
    input.type = 'hidden'; input.name = 'deleteIds'; input.value = c.value;
    document.getElementById('batchDeleteForm').appendChild(input);
  });
  return true;
}
</script>

</th:block>
</body>
</html>
