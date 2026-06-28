<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<head>
    <title>${domainKr} 등록</title>
</head>
<th:block layout:fragment="content">
<h1>${domainKr} 등록</h1>
<form th:action="@{${urlPrefix}Regist.do}" method="post">
    <input type="hidden" th:name="${bbsId.javaName}" th:value="${r"${"}${domainLc}${r"VO."}${bbsId.javaName}${r"}"}"/>
    <table>
<#list formFields as f>
        <tr>
            <th>${f.comment}</th>
            <td><input type="text" th:name="${f.javaName}" th:value="${r"${"}${domainLc}${r"VO."}${f.javaName}${r"}"}"/></td>
        </tr>
</#list>
    </table>
    <a th:href="@{${urlPrefix}List.do(bbsId=${r"${"}${domainLc}${r"VO."}${bbsId.javaName}${r"}"})}">취소</a>
    <button type="submit">저장</button>
</form>
</th:block>
</html>
