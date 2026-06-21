<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<head>
    <title>${domainKr} 목록</title>
</head>
<th:block layout:fragment="content">
<div class="d-flex justify-content-between align-items-center mb-3">
    <div>
        <h4 class="mb-1">${domainKr} 목록</h4>
        <nav aria-label="breadcrumb">
            <ol class="breadcrumb mb-0">
                <li class="breadcrumb-item"><a th:href="@{/}">홈</a></li>
                <li class="breadcrumb-item active">${domainKr} 목록</li>
            </ol>
        </nav>
    </div>
    <a th:href="@{${urlPrefix}RegistView.do}" class="krds-btn primary medium">
        <i class="bi bi-plus-lg me-1"></i>등록
    </a>
</div>

<div th:if="${'$'}{message}" class="alert alert-success alert-dismissible fade show" role="alert">
    <span th:text="${'$'}{message}"></span>
    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
</div>

<div class="card mb-3">
    <div class="card-body">
        <form name="searchForm" th:action="@{${urlPrefix}List.do}" method="get">
            <input type="hidden" name="pageIndex" th:value="${'$'}{searchVO.pageIndex}"/>
            <div class="row g-2 align-items-center">
                <div class="col-auto">
                    <select name="searchCondition" class="krds-form-select medium" title="검색조건">
                        <option value="1">${domainKr}ID</option>
                    </select>
                </div>
                <div class="col">
                    <input type="text" name="searchKeyword" class="krds-input medium"
                           th:value="${'$'}{searchVO.searchKeyword}" placeholder="검색어를 입력하세요"/>
                </div>
                <div class="col-auto">
                    <button type="submit" class="krds-btn primary medium">
                        <i class="bi bi-search me-1"></i>검색
                    </button>
                </div>
            </div>
        </form>
    </div>
</div>

<div class="card">
    <div class="card-body p-0">
        <div class="krds-table-wrap">
            <table class="tbl col data">
                <caption>${domainKr} 목록 표</caption>
                <colgroup>
                    <col style="width: 8%;">
<#list listFields as f>
                    <col>
</#list>
                    <col style="width: 10%;">
                </colgroup>
                <thead>
                <tr>
                    <th scope="col">번호</th>
<#list listFields as f>
                    <th scope="col">${f.comment}</th>
</#list>
                    <th scope="col">관리</th>
                </tr>
                </thead>
                <tbody>
                <tr th:each="item, status : ${'$'}{resultList}"
                    th:data-href="@{${urlPrefix}Detail.do(${pk.javaName}=${'$'}{item.${pk.javaName}})}"
                    style="cursor:pointer;">
                    <td th:text="${'$'}{paginationInfo.totalRecordCount - ((searchVO.pageIndex - 1) * searchVO.pageUnit) - status.index}">1</td>
<#list listFields as f>
                    <td th:text="${'$'}{item.${f.javaName}}"></td>
</#list>
                    <td>
                        <a th:href="@{${urlPrefix}Detail.do(${pk.javaName}=${'$'}{item.${pk.javaName}})}"
                           class="krds-btn secondary xsmall"
                           onclick="event.stopPropagation();">상세</a>
                    </td>
                </tr>
                <tr th:if="${'$'}{#lists.isEmpty(resultList)}">
                    <td colspan="${listFields?size + 2}" class="no-data text-center py-4">조회된 데이터가 없습니다.</td>
                </tr>
                </tbody>
            </table>
        </div>
    </div>
</div>

<div class="krds-pagination d-flex justify-content-center align-items-center gap-1 mt-3"
     th:if="${'$'}{paginationInfo != null and paginationInfo.totalRecordCount > 0}">
    <a th:if="${'$'}{paginationInfo.currentPageNo > 1}"
       th:href="@{${urlPrefix}List.do(pageIndex=1,searchCondition=${'$'}{searchVO.searchCondition},searchKeyword=${'$'}{searchVO.searchKeyword})}"
       class="krds-btn secondary small"><i class="bi bi-chevron-double-left"></i></a>
    <a th:if="${'$'}{paginationInfo.currentPageNo > 1}"
       th:href="@{${urlPrefix}List.do(pageIndex=${'$'}{paginationInfo.currentPageNo - 1},searchCondition=${'$'}{searchVO.searchCondition},searchKeyword=${'$'}{searchVO.searchKeyword})}"
       class="krds-btn secondary small"><i class="bi bi-chevron-left"></i></a>
    <a th:each="pageNo : ${'$'}{#numbers.sequence(paginationInfo.firstPageNoOnPageList, paginationInfo.lastPageNoOnPageList)}"
       th:href="@{${urlPrefix}List.do(pageIndex=${'$'}{pageNo},searchCondition=${'$'}{searchVO.searchCondition},searchKeyword=${'$'}{searchVO.searchKeyword})}"
       th:text="${'$'}{pageNo}"
       th:classappend="${'$'}{pageNo == paginationInfo.currentPageNo} ? 'active'"
       class="krds-btn secondary small">1</a>
    <a th:if="${'$'}{paginationInfo.currentPageNo < paginationInfo.totalPageCount}"
       th:href="@{${urlPrefix}List.do(pageIndex=${'$'}{paginationInfo.currentPageNo + 1},searchCondition=${'$'}{searchVO.searchCondition},searchKeyword=${'$'}{searchVO.searchKeyword})}"
       class="krds-btn secondary small"><i class="bi bi-chevron-right"></i></a>
    <a th:if="${'$'}{paginationInfo.currentPageNo < paginationInfo.totalPageCount}"
       th:href="@{${urlPrefix}List.do(pageIndex=${'$'}{paginationInfo.totalPageCount},searchCondition=${'$'}{searchVO.searchCondition},searchKeyword=${'$'}{searchVO.searchKeyword})}"
       class="krds-btn secondary small"><i class="bi bi-chevron-double-right"></i></a>
</div>
</th:block>

<th:block layout:fragment="scripts">
<script>
document.querySelectorAll('tr[data-href]').forEach(function(row) {
    row.addEventListener('click', function() {
        window.location.href = this.dataset.href;
    });
});
</script>
</th:block>
</html>
