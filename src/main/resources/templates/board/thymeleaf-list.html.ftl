<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<head>
    <title>${domainKr} 목록</title>
</head>
<section layout:fragment="content">
    <th:block th:replace="~{layout/breadcrumb :: breadcrumb}"></th:block>

    <div style="display:flex;align-items:center;justify-content:space-between;gap:16px;padding-bottom:16px;margin-bottom:20px;border-bottom:2px solid #1e2124;">
        <h1 style="font-size:28px;font-weight:800;margin:0;">${domainKr} 목록</h1>
        <a th:href="@{${urlPrefix}RegistView.do(bbsId=${r"${searchVO.bbsId}"})}" class="krds-btn primary medium">
            <span aria-hidden="true">＋</span>
            등록
        </a>
    </div>

    <div id="toast-alert" th:if="${r"${message}"}"
         style="position:fixed;bottom:32px;left:50%;transform:translateX(-50%);display:flex;align-items:center;gap:10px;padding:14px 20px;border-radius:8px;background:#1e2124;color:#fff;font-size:14px;font-weight:600;z-index:9999;box-shadow:0 4px 16px rgba(0,0,0,.18);white-space:nowrap;"
         role="alert" aria-live="polite">
        <span aria-hidden="true" style="color:#5cb85c;">✓</span>
        <span th:text="${r"${message}"}">처리되었습니다.</span>
    </div>
    <script>
    (function(){var t=document.getElementById('toast-alert');if(t){setTimeout(function(){t.style.transition='opacity 0.4s';t.style.opacity='0';setTimeout(function(){t.remove();},400);},3000);}})();
    </script>

    <div style="margin-bottom:16px;padding:16px 20px;border:1px solid #e8eaec;border-radius:6px;background:#f6f7f8;">
        <form id="searchForm" th:action="@{${urlPrefix}List.do}" method="get">
            <input type="hidden" name="bbsId" th:value="${r"${searchVO.bbsId}"}"/>
            <input type="hidden" name="pageIndex" th:value="${r"${searchVO.pageIndex}"}"/>
            <div style="display:flex;align-items:center;gap:8px;">
                <select name="searchCondition" class="krds-form-select" title="검색조건" style="min-width:120px;">
                    <option value="1" th:selected="${r"${searchVO.searchCondition == '1'}"}">제목</option>
                    <option value="2" th:selected="${r"${searchVO.searchCondition == '2'}"}">내용</option>
                    <option value="3" th:selected="${r"${searchVO.searchCondition == '3'}"}">작성자</option>
                </select>
                <input type="text" name="searchKeyword" class="krds-input"
                       th:value="${r"${searchVO.searchKeyword}"}" placeholder="검색어를 입력하세요"/>
                <button type="submit" class="krds-btn primary medium" style="display:inline-flex;align-items:center;gap:4px;white-space:nowrap;">
                    <svg aria-hidden="true" viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><circle cx="8.5" cy="8.5" r="5.5"/><path d="m13 13 3 3"/></svg>
                    검색
                </button>
                <a th:href="@{${urlPrefix}List.do(bbsId=${r"${searchVO.bbsId}"})}" class="krds-btn secondary medium" style="display:inline-flex;align-items:center;gap:4px;white-space:nowrap;">
                    <svg aria-hidden="true" viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 8a7 7 0 1 1 .8 3.2"/><path d="M4 3v5h5"/></svg>
                    초기화
                </a>
            </div>
        </form>
    </div>

    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:10px;">
        <span style="font-size:13px;color:#6d7882;">
            총 <strong style="color:#083891;"
                       th:text="${r"${paginationInfo != null ? paginationInfo.totalRecordCount : 0}"}">0</strong>건
        </span>
        <span style="font-size:13px;color:#8a949e;" th:if="${r"${paginationInfo != null}"}">
            <span th:text="${r"${paginationInfo.currentPageNo}"}">1</span>
            /
            <span th:text="${r"${paginationInfo.totalPageCount}"}">1</span>
            페이지
        </span>
    </div>

    <div class="krds-table-wrap">
        <table class="tbl data">
            <caption>${domainKr} 목록 표</caption>
            <colgroup>
                <col style="width:64px;">
<#list listFields as f>
                <col>
</#list>
<#if hasFile && atchFileId??>
                <col style="width:70px;">
</#if>
                <col style="width:120px;">
            </colgroup>
            <thead>
            <tr>
                <th scope="col">번호</th>
<#list listFields as f>
                <th scope="col">${f.comment}</th>
</#list>
<#if hasFile && atchFileId??>
                <th scope="col">첨부</th>
</#if>
                <th scope="col">관리</th>
            </tr>
            </thead>
            <tbody>
            <tr th:each="item, status : ${r"${resultList}"}"
                th:data-href="@{${urlPrefix}Detail.do(${bbsId.javaName}=${r"${item."}${bbsId.javaName}${r"}"},${nttId.javaName}=${r"${item."}${nttId.javaName}${r"}"})}"
                data-row-link="true" style="cursor:pointer;"
<#if noticeAtExists>
                th:styleappend="${r"${item.noticeAt == 'Y'}"} ? 'background:#eef3fe;' : ''"
</#if>
                >
                <td style="text-align:center;color:#8a949e;font-size:13px;"
<#if noticeAtExists>
                    >
                    <span th:if="${r"${item.noticeAt == 'Y'}"}"
                          style="display:inline-flex;align-items:center;justify-content:center;min-width:34px;height:22px;padding:0 7px;border-radius:3px;background:#083891;color:#fff;font-size:11px;font-weight:700;">공지</span>
                    <span th:unless="${r"${item.noticeAt == 'Y'}"}" th:text="${r"${status.count}"}">1</span>
                </td>
<#else>
                    th:text="${r"${status.count}"}">1</td>
</#if>
<#list listFields as f>
                <td>
                    <a th:href="@{${urlPrefix}Detail.do(${bbsId.javaName}=${r"${item."}${bbsId.javaName}${r"}"},${nttId.javaName}=${r"${item."}${nttId.javaName}${r"}"})}"
<#if f.javaName == "noticeAt">
                       style="display:inline-flex;align-items:center;justify-content:center;min-width:34px;height:22px;padding:0 7px;border-radius:3px;background:#eef3fe;color:#083891;font-size:12px;font-weight:700;"
                       th:text="${r"${item."}${f.javaName}${r" == 'Y' ? '공지' : '일반'}"}"></a>
<#else>
                       th:text="${r"${item."}${f.javaName}${r"}"}"
                       style="<#if f.pk>font-family:monospace;color:#256ef4;font-weight:700;<#else>color:#1e2124;font-weight:500;</#if>"></a>
</#if>
                </td>
</#list>
<#if hasFile && atchFileId??>
                <td style="text-align:center;">
                    <a th:if="${r"${item."}${atchFileId.javaName}${r" != null and item."}${atchFileId.javaName}${r" != ''}"}"
                       th:href="@{${urlPrefix}FileDownload.do(${bbsId.javaName}=${r"${item."}${bbsId.javaName}${r"}"},${nttId.javaName}=${r"${item."}${nttId.javaName}${r"}"},${atchFileId.javaName}=${r"${item."}${atchFileId.javaName}${r"}"})}"
                       title="첨부파일 다운로드"
                       onclick="event.stopPropagation();"
                       style="display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border-radius:4px;color:#256ef4;">
                        <svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M12 3v12"></path>
                            <path d="m7 10 5 5 5-5"></path>
                            <path d="M5 21h14"></path>
                        </svg>
                        <span style="position:absolute;width:1px;height:1px;margin:-1px;padding:0;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0;">첨부파일</span>
                    </a>
                    <span th:unless="${r"${item."}${atchFileId.javaName}${r" != null and item."}${atchFileId.javaName}${r" != ''}"}"
                          style="color:#cdd1d5;">-</span>
                </td>
</#if>
                <td style="text-align:center;">
                    <a th:href="@{${urlPrefix}Detail.do(${bbsId.javaName}=${r"${item."}${bbsId.javaName}${r"}"},${nttId.javaName}=${r"${item."}${nttId.javaName}${r"}"})}"
                       class="krds-btn secondary small"
                       onclick="event.stopPropagation();">상세</a>
                </td>
            </tr>
            <tr th:if="${r"${#lists.isEmpty(resultList)}"}">
                <td colspan="${listFields?size + 2 + (hasFile && atchFileId??)?then(1, 0)}"
                    style="padding:52px 0;text-align:center;color:#8a949e;">
                    <div style="display:flex;flex-direction:column;align-items:center;gap:10px;">
                        <svg aria-hidden="true" viewBox="0 0 48 48" width="42" height="42" fill="none">
                            <rect x="10" y="8" width="28" height="32" rx="3" stroke="#cdd1d5" stroke-width="2"/>
                            <path d="M16 18h16M16 25h16M16 32h10" stroke="#8a949e" stroke-width="2" stroke-linecap="round"/>
                        </svg>
                        <span>검색 결과가 없습니다.</span>
                    </div>
                </td>
            </tr>
            </tbody>
        </table>
    </div>

    <nav class="krds-pagination" aria-label="페이지 이동"
         th:if="${r"${paginationInfo != null and paginationInfo.totalRecordCount > 0}"}">
        <a th:if="${r"${paginationInfo.currentPageNo > 1}"}"
           th:href="@{${urlPrefix}List.do(pageIndex=1,bbsId=${r"${searchVO.bbsId}"},searchCondition=${r"${searchVO.searchCondition}"},searchKeyword=${r"${searchVO.searchKeyword}"})}"
           class="btn-first" title="처음"></a>
        <a th:if="${r"${paginationInfo.currentPageNo > 1}"}"
           th:href="@{${urlPrefix}List.do(pageIndex=${r"${paginationInfo.currentPageNo - 1}"},bbsId=${r"${searchVO.bbsId}"},searchCondition=${r"${searchVO.searchCondition}"},searchKeyword=${r"${searchVO.searchKeyword}"})}"
           class="btn-prev" title="이전"></a>
        <ol>
            <li th:each="pageNo : ${r"${#numbers.sequence(paginationInfo.firstPageNoOnPageList, paginationInfo.lastPageNoOnPageList)}"}"
                th:classappend="${r"${pageNo == paginationInfo.currentPageNo}"} ? ' on' : ''">
                <a th:href="@{${urlPrefix}List.do(pageIndex=${r"${pageNo}"},bbsId=${r"${searchVO.bbsId}"},searchCondition=${r"${searchVO.searchCondition}"},searchKeyword=${r"${searchVO.searchKeyword}"})}"
                   th:text="${r"${pageNo}"}">1</a>
            </li>
        </ol>
        <a th:if="${r"${paginationInfo.currentPageNo < paginationInfo.totalPageCount}"}"
           th:href="@{${urlPrefix}List.do(pageIndex=${r"${paginationInfo.currentPageNo + 1}"},bbsId=${r"${searchVO.bbsId}"},searchCondition=${r"${searchVO.searchCondition}"},searchKeyword=${r"${searchVO.searchKeyword}"})}"
           class="btn-next" title="다음"></a>
        <a th:if="${r"${paginationInfo.currentPageNo < paginationInfo.totalPageCount}"}"
           th:href="@{${urlPrefix}List.do(pageIndex=${r"${paginationInfo.totalPageCount}"},bbsId=${r"${searchVO.bbsId}"},searchCondition=${r"${searchVO.searchCondition}"},searchKeyword=${r"${searchVO.searchKeyword}"})}"
           class="btn-last" title="마지막"></a>
    </nav>
</section>

<th:block layout:fragment="scripts">
<script>
document.querySelectorAll('tr[data-row-link="true"][data-href]').forEach(function(row) {
    row.addEventListener('click', function() {
        window.location.href = this.dataset.href;
    });
});
</script>
</th:block>
</html>
