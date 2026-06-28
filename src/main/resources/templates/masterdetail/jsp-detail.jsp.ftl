<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${master.domainKr} 상세</title>
</head>
<body>
<h1>${master.domainKr} 상세</h1>
<table border="1">
<#list master.fields as f>
    <tr>
        <th>${f.comment}</th>
        <td><c:out value="${'$'}{result.${f.javaName}}"/></td>
    </tr>
</#list>
</table>

<h2>${detail.domainKr} 목록</h2>
<table border="1">
    <thead>
    <tr>
<#list detail.fields as f>
        <th>${f.comment}</th>
</#list>
    </tr>
    </thead>
    <tbody>
    <c:forEach var="detail" items="${'$'}{detailList}">
        <tr>
<#list detail.fields as f>
            <td><c:out value="${'$'}{detail.${f.javaName}}"/></td>
</#list>
        </tr>
    </c:forEach>
    </tbody>
</table>
<form action="${urlPrefix}Delete.do" method="post" onsubmit="return confirm('삭제하시겠습니까?');">
    <input type="hidden" name="${master.pk.javaName}" value="${'$'}{result.${master.pk.javaName}}">
    <button type="submit">삭제</button>
    <a href="${urlPrefix}List.do">목록</a>
</form>
</body>
</html>
