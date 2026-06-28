<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<head>
    <title>${domainKr} 상세</title>
</head>
<th:block layout:fragment="content">
<h1>${domainKr} 상세</h1>
<table>
    <tbody>
<#list fields as f>
        <tr>
            <th>${f.comment}</th>
            <td th:text="${r"${result."}${f.javaName}${r"}"}"></td>
        </tr>
</#list>
    </tbody>
</table>

<#if hasFile>
<h3>첨부파일</h3>
<p>첨부파일 ID: <span th:text="${r"${result.atchFileId}"}"></span></p>
</#if>

<a th:href="@{${urlPrefix}List.do(bbsId=${r"${result."}${bbsId.javaName}${r"}"})}">목록</a>
<a th:href="@{${urlPrefix}UpdtView.do(${bbsId.javaName}=${r"${result."}${bbsId.javaName}${r"}"},${nttId.javaName}=${r"${result."}${nttId.javaName}${r"}"})}">수정</a>
<form th:action="@{${urlPrefix}Delete.do}" method="post" style="display:inline">
    <input type="hidden" th:name="${bbsId.javaName}" th:value="${r"${result."}${bbsId.javaName}${r"}"}"/>
    <input type="hidden" th:name="${nttId.javaName}" th:value="${r"${result."}${nttId.javaName}${r"}"}"/>
    <button type="submit" onclick="return confirm('삭제하시겠습니까?')">삭제</button>
</form>
</th:block>
</html>
