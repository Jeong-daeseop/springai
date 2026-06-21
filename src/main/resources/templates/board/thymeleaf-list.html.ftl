<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8"/>
    <title>${domainKr} 목록</title>
</head>
<body>
<main>
<h1>${domainKr} 목록</h1>

<form id="searchForm" th:action="@{${urlPrefix}List.do}" method="get">
    <input type="hidden" name="bbsId" th:value="${r"${searchVO.bbsId}"}"/>
    <input type="hidden" name="pageIndex" th:value="${r"${searchVO.pageIndex}"}"/>
    <select name="searchCondition">
        <option value="1">제목</option>
        <option value="2">내용</option>
        <option value="3">작성자</option>
    </select>
    <input type="text" name="searchKeyword" th:value="${r"${searchVO.searchKeyword}"}"/>
    <button type="submit">검색</button>
</form>

<table>
    <thead>
        <tr>
            <th>번호</th>
<#list listFields as f>
            <th>${f.comment}</th>
</#list>
            <th>등록일</th>
        </tr>
    </thead>
    <tbody>
        <tr th:each="item, status : ${r"${resultList}"}">
            <td th:text="${r"${status.count}"}"></td>
<#list listFields as f>
            <td th:text="${r"${item."}${f.javaName}${r"}"}"></td>
</#list>
            <td th:text="${r"${item.frstRegistPnttm}"}"></td>
        </tr>
    </tbody>
</table>

<div th:if="${r"${paginationInfo != null}"}">
    <span>총 <span th:text="${r"${paginationInfo.totalRecordCount}"}"></span>건</span>
</div>

<a th:href="@{${urlPrefix}RegistView.do(bbsId=${r"${searchVO.bbsId}"})}">등록</a>
</main>
</body>
</html>
