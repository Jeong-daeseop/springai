<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<head>
    <title>${master.domainKr} 목록</title>
</head>
<th:block layout:fragment="content">
<div th:if="${'$'}{message}" class="alert alert-success" role="alert">
    <span th:text="${'$'}{message}"></span>
</div>

<div class="d-flex justify-content-between align-items-center mb-3">
    <h4 class="mb-0">${master.domainKr} 목록</h4>
    <a th:href="@{${urlPrefix}RegistView.do}" class="krds-btn primary medium">등록</a>
</div>

<form th:action="@{${urlPrefix}List.do}" method="get" class="search-form mb-3">
    <input type="text" name="searchKeyword" th:value="${'$'}{searchVO.searchKeyword}" placeholder="검색어">
    <button type="submit" class="krds-btn secondary medium">검색</button>
</form>

<div class="krds-table-wrap">
    <table class="tbl col">
        <caption>${master.domainKr} 목록</caption>
        <thead>
        <tr>
<#list master.listFields as f>
            <th scope="col">${f.comment}</th>
</#list>
        </tr>
        </thead>
        <tbody>
        <tr th:each="item : ${'$'}{resultList}">
<#list master.listFields as f>
<#if f.javaName == master.pk.javaName>
            <td><a th:href="@{${urlPrefix}Detail.do(${master.pk.javaName}=${'$'}{item.${master.pk.javaName}})}" th:text="${'$'}{item.${f.javaName}}"></a></td>
<#else>
            <td th:text="${'$'}{item.${f.javaName}}"></td>
</#if>
</#list>
        </tr>
        <tr th:if="${'$'}{#lists.isEmpty(resultList)}">
            <td colspan="${master.listFields?size}">조회된 데이터가 없습니다.</td>
        </tr>
        </tbody>
    </table>
</div>
</th:block>
</html>
