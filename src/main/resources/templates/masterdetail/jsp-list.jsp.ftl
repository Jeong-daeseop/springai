<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${master.domainKr} 목록</title>
</head>
<body>
<h1>${master.domainKr} 목록</h1>
<form action="${urlPrefix}List.do" method="get">
    <input type="text" name="searchKeyword" value="<c:out value="${'$'}{searchVO.searchKeyword}"/>">
    <button type="submit">검색</button>
    <a href="${urlPrefix}RegistView.do">등록</a>
</form>
<table border="1">
    <thead>
    <tr>
<#list master.listFields as f>
        <th>${f.comment}</th>
</#list>
    </tr>
    </thead>
    <tbody>
    <c:forEach var="item" items="${'$'}{resultList}">
        <tr>
<#list master.listFields as f>
<#if f.javaName == master.pk.javaName>
            <td><a href="${urlPrefix}Detail.do?${master.pk.javaName}=${'$'}{item.${master.pk.javaName}}"><c:out value="${'$'}{item.${f.javaName}}"/></a></td>
<#else>
            <td><c:out value="${'$'}{item.${f.javaName}}"/></td>
</#if>
</#list>
        </tr>
    </c:forEach>
    </tbody>
</table>
</body>
</html>
