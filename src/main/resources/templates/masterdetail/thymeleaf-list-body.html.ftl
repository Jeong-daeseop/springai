    <div class="egov-page-header">
        <h1 class="egov-page-title">${master.domainKr} 목록</h1>
        <div class="egov-button-group">
            <button id="btnBulkDelete" type="button" class="krds-btn negative medium egov-hidden"
                    onclick="openBulkDeleteModal()">선택삭제</button>
            <a th:href="@{${urlPrefix}RegistView.do}" class="krds-btn primary medium">
                <span aria-hidden="true">＋</span>
                등록
            </a>
        </div>
    </div>

    <div id="toast-alert" th:if="${'$'}{message}" class="egov-toast" role="alert" aria-live="polite">
        <span aria-hidden="true" class="egov-toast-icon">✓</span>
        <span th:text="${'$'}{message}">처리되었습니다.</span>
    </div>
    <script>
    (function(){var t=document.getElementById('toast-alert');if(t){setTimeout(function(){t.style.transition='opacity 0.4s';t.style.opacity='0';setTimeout(function(){t.remove();},400);},3000);}})();
    </script>

    <div class="egov-search-panel">
        <form th:action="@{${urlPrefix}List.do}" method="get">
            <input type="hidden" name="pageIndex" th:value="${'$'}{searchVO.pageIndex}"/>
            <div class="egov-search-row">
                <select name="searchCondition" class="krds-form-select egov-search-condition" title="검색조건">
                    <option value="1" th:selected="${'$'}{searchVO.searchCondition == '1'}">${master.domainKr} ID</option>
                </select>
                <input type="text" name="searchKeyword" class="krds-input"
                       th:value="${'$'}{searchVO.searchKeyword}" placeholder="검색어를 입력하세요"/>
                <button type="submit" class="krds-btn primary medium egov-inline-action">
                    <svg aria-hidden="true" viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><circle cx="8.5" cy="8.5" r="5.5"/><path d="m13 13 3 3"/></svg>
                    검색
                </button>
                <a th:href="@{${urlPrefix}List.do}" class="krds-btn secondary medium egov-inline-action">
                    <svg aria-hidden="true" viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 8a7 7 0 1 1 .8 3.2"/><path d="M4 3v5h5"/></svg>
                    초기화
                </a>
            </div>
        </form>
    </div>

    <div class="egov-list-summary">
        <span class="egov-muted-text">
            총 <strong class="egov-primary-text"
                       th:text="${'$'}{paginationInfo != null ? paginationInfo.totalRecordCount : 0}">0</strong>건
        </span>
        <span class="egov-subtle-text" th:if="${'$'}{paginationInfo != null}">
            <span th:text="${'$'}{paginationInfo.currentPageNo}">1</span>
            /
            <span th:text="${'$'}{paginationInfo.totalPageCount}">1</span>
            페이지
        </span>
    </div>

    <div class="krds-table-wrap">
        <table class="tbl data">
            <caption>${master.domainKr} 목록 표</caption>
            <colgroup>
                <col class="egov-col-check">
                <col class="egov-col-no">
<#list master.listFields as f>
                <col>
</#list>
                <col class="egov-col-actions">
            </colgroup>
            <thead>
            <tr>
                <th scope="col" class="egov-table-actions">
                    <input type="checkbox" id="checkAll" onclick="toggleAll(this)" aria-label="전체 선택"/>
                </th>
                <th scope="col">번호</th>
<#list master.listFields as f>
                <th scope="col">${f.comment}</th>
</#list>
                <th scope="col">관리</th>
            </tr>
            </thead>
            <tbody>
            <tr th:each="item, status : ${'$'}{resultList}"
                th:data-href="@{${urlPrefix}Detail.do(${master.pk.javaName}=${'$'}{item.${master.pk.javaName}})}"
                data-row-link="true" class="egov-row-link">
                <td class="egov-table-actions" onclick="event.stopPropagation();">
                    <input type="checkbox" class="row-check"
                           th:value="${'$'}{item.${master.pk.javaName}}"
                           aria-label="행 선택"/>
                </td>
                <td th:text="${'$'}{paginationInfo != null
                    ? paginationInfo.totalRecordCount - ((searchVO.pageIndex - 1) * searchVO.pageUnit) - status.index
                    : status.count}" class="egov-table-no">1</td>
<#list master.listFields as f>
<#assign isStatus = f.javaName == "useAt" || f.javaName == "useYn" || f.javaName == "sttus" || f.javaName == "status" || f.javaName == "activeYn">
                <td>
<#if isStatus>
                    <span th:if="${'$'}{item.${f.javaName} == 'Y'}"
                          class="egov-status-badge positive">사용</span>
                    <span th:unless="${'$'}{item.${f.javaName} == 'Y'}"
                          class="egov-status-badge negative">중지</span>
<#else>
                    <a th:href="@{${urlPrefix}Detail.do(${master.pk.javaName}=${'$'}{item.${master.pk.javaName}})}"
                       th:text="${'$'}{item.${f.javaName}}"
                       class="egov-detail-link"></a>
</#if>
                </td>
</#list>
                <td class="egov-table-actions">
                    <a th:href="@{${urlPrefix}UpdtView.do(${master.pk.javaName}=${'$'}{item.${master.pk.javaName}})}"
                       class="krds-btn secondary small"
                       onclick="event.stopPropagation();">수정</a>
                    <button type="button" class="krds-btn negative small"
                            th:attr="data-id=${'$'}{item.${master.pk.javaName}}"
                            onclick="event.stopPropagation();openRowDeleteModal(this.dataset.id)">삭제</button>
                </td>
            </tr>
            <tr th:if="${'$'}{#lists.isEmpty(resultList)}">
                <td colspan="${master.listFields?size + 3}" class="egov-empty-cell">
                    <div class="egov-empty-state">
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
         th:if="${'$'}{paginationInfo != null and paginationInfo.totalRecordCount > 0}">
        <a th:if="${'$'}{paginationInfo.currentPageNo > 1}"
           th:href="@{${urlPrefix}List.do(pageIndex=1,searchCondition=${'$'}{searchVO.searchCondition},searchKeyword=${'$'}{searchVO.searchKeyword})}"
           class="btn-first" title="처음"></a>
        <a th:if="${'$'}{paginationInfo.currentPageNo > 1}"
           th:href="@{${urlPrefix}List.do(pageIndex=${'$'}{paginationInfo.currentPageNo - 1},searchCondition=${'$'}{searchVO.searchCondition},searchKeyword=${'$'}{searchVO.searchKeyword})}"
           class="btn-prev" title="이전"></a>
        <ol>
            <li th:each="pageNo : ${'$'}{#numbers.sequence(paginationInfo.firstPageNoOnPageList, paginationInfo.lastPageNoOnPageList)}"
                th:classappend="${'$'}{pageNo == paginationInfo.currentPageNo} ? ' on' : ''">
                <a th:href="@{${urlPrefix}List.do(pageIndex=${'$'}{pageNo},searchCondition=${'$'}{searchVO.searchCondition},searchKeyword=${'$'}{searchVO.searchKeyword})}"
                   th:text="${'$'}{pageNo}">1</a>
            </li>
        </ol>
        <a th:if="${'$'}{paginationInfo.currentPageNo < paginationInfo.totalPageCount}"
           th:href="@{${urlPrefix}List.do(pageIndex=${'$'}{paginationInfo.currentPageNo + 1},searchCondition=${'$'}{searchVO.searchCondition},searchKeyword=${'$'}{searchVO.searchKeyword})}"
           class="btn-next" title="다음"></a>
        <a th:if="${'$'}{paginationInfo.currentPageNo < paginationInfo.totalPageCount}"
           th:href="@{${urlPrefix}List.do(pageIndex=${'$'}{paginationInfo.totalPageCount},searchCondition=${'$'}{searchVO.searchCondition},searchKeyword=${'$'}{searchVO.searchKeyword})}"
           class="btn-last" title="마지막"></a>
    </nav>

    <dialog id="rowDeleteModal" class="egov-modal">
        <h2 class="egov-modal-title">삭제 확인</h2>
        <p class="egov-modal-desc">삭제하시겠습니까?<br>삭제된 데이터는 복구할 수 없습니다.</p>
        <div class="egov-modal-actions">
            <button type="button" class="krds-btn secondary medium"
                    onclick="document.getElementById('rowDeleteModal').close()">취소</button>
            <button type="button" class="krds-btn negative medium"
                    onclick="document.getElementById('rowDeleteForm').submit()">삭제</button>
        </div>
    </dialog>
    <form id="rowDeleteForm" th:action="@{${urlPrefix}Delete.do}" method="post" class="egov-hidden">
        <input id="rowDeleteId" type="hidden" name="${master.pk.javaName}" value=""/>
    </form>

    <dialog id="bulkDeleteModal" class="egov-modal">
        <h2 class="egov-modal-title">선택 삭제 확인</h2>
        <p class="egov-modal-desc">선택한 항목을 삭제하시겠습니까?<br>삭제된 데이터는 복구할 수 없습니다.</p>
        <div class="egov-modal-actions">
            <button type="button" class="krds-btn secondary medium"
                    onclick="document.getElementById('bulkDeleteModal').close()">취소</button>
            <button type="button" class="krds-btn negative medium"
                    onclick="document.getElementById('bulkDeleteForm').submit()">삭제</button>
        </div>
    </dialog>
    <!-- TODO: ${urlPrefix}BulkDelete.do — Controller/Service/Mapper 구현 필요 -->
    <form id="bulkDeleteForm" th:action="@{${urlPrefix}BulkDelete.do}" method="post" class="egov-hidden"></form>
