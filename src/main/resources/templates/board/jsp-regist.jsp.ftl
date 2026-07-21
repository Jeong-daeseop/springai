<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<% String contextPath = request.getContextPath(); %>
<!DOCTYPE html>
<html>
<head>
    <title>${displayName} 등록</title>
    <link rel="stylesheet" href="<%=contextPath%>/resources/css/styles.css">
</head>
<body>
<h1>${displayName} 등록</h1>
<form method="post" action="${urlPrefix}Regist.do">
    <c:if test="${r"${not empty _csrf}"}">
        <input type="hidden" name="${r"${_csrf.parameterName}"}" value="${r"${_csrf.token}"}"/>
    </c:if>
    <input type="hidden" name="${bbsId.javaName}" value="${r"${"}${domainLc}${r"VO."}${bbsId.javaName}${r"}"}"/>
    <table>
        <caption>${displayName} 등록 입력 폼</caption>
<#list formFields as f>
        <tr>
            <th>${f.comment}</th>
            <td><input type="text" name="${f.javaName}" value="${r"${"}${domainLc}${r"VO."}${f.javaName}${r"}"}"/></td>
        </tr>
</#list>
    </table>
    <a href="${urlPrefix}List.do?bbsId=${r"${"}${domainLc}${r"VO."}${bbsId.javaName}${r"}"}">취소</a>
    <button type="submit">저장</button>
</form>
<script src="<%=contextPath%>/resources/js/krds.min.js"></script>
</body>
</html>
