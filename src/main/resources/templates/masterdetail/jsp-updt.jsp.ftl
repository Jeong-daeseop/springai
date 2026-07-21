<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${master.domainKr} 수정</title>
</head>
<body>
<h1>${master.domainKr} 수정</h1>
<form:form modelAttribute="${master.domainLc}VO" action="${urlPrefix}Updt.do" method="post">
<#list master.pkFields as p>
    <form:hidden path="${p.javaName}"/>
</#list>
<#list master.nonPkFields as f>
<#if !master.formFields?seq_contains(f)>
    <form:hidden path="${f.javaName}"/>
</#if>
</#list>
    <table border="1">
<#list master.pkFields as p>
        <tr>
            <th>${p.comment}</th>
            <td><c:out value="${'$'}{${master.domainLc}VO.${p.javaName}}"/></td>
        </tr>
</#list>
<#list master.formFields as f>
        <tr>
            <th>${f.comment}</th>
            <td>
                <form:input path="${f.javaName}"/>
                <form:errors path="${f.javaName}"/>
            </td>
        </tr>
</#list>
    </table>
    <button type="submit">저장</button>
    <a href="${urlPrefix}Detail.do?${master.pk.javaName}=${'$'}{${master.domainLc}VO.${master.pk.javaName}}">취소</a>
</form:form>
</body>
</html>
