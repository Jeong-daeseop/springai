<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8"/>
    <title>${domainKr} 수정</title>
</head>
<body>
<main>
<h1>${domainKr} 수정</h1>
<form th:action="@{${urlPrefix}Updt.do}" method="post">
    <input type="hidden" th:name="${bbsId.javaName}" th:value="${r"${"}${domainLc}${r"VO."}${bbsId.javaName}${r"}"}"/>
    <input type="hidden" th:name="${nttId.javaName}" th:value="${r"${"}${domainLc}${r"VO."}${nttId.javaName}${r"}"}"/>
    <table>
<#list formFields as f>
        <tr>
            <th>${f.comment}</th>
            <td><input type="text" th:name="${f.javaName}" th:value="${r"${"}${domainLc}${r"VO."}${f.javaName}${r"}"}"/></td>
        </tr>
</#list>
    </table>
    <a th:href="@{${urlPrefix}Detail.do(${bbsId.javaName}=${r"${"}${domainLc}${r"VO."}${bbsId.javaName}${r"}"},${nttId.javaName}=${r"${"}${domainLc}${r"VO."}${nttId.javaName}${r"}"})}">취소</a>
    <button type="submit">저장</button>
</form>
</main>
</body>
</html>
