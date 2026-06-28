<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${master.domainKr} 등록</title>
</head>
<body>
<h1>${master.domainKr} 등록</h1>
<form:form modelAttribute="${master.domainLc}VO" action="${urlPrefix}Regist.do" method="post">
    <table border="1">
<#list master.fields as f>
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
    <a href="${urlPrefix}List.do">취소</a>
</form:form>
</body>
</html>
