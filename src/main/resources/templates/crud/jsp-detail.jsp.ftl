<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%
    String contextPath = request.getContextPath();
%>
<!DOCTYPE html>
<html>
<head>
    <title>${domainKr} 상세</title>
</head>
<body>
<div class="container">
    <h2>${domainKr} 상세</h2>

    <table>
        <tbody>
<#list fields as f>
        <tr>
            <th>${f.comment}</th>
            <td><c:out value="${'$'}{result.${f.javaName}}"/></td>
        </tr>
</#list>
        </tbody>
    </table>

    <!-- 버튼 -->
    <div>
        <a href="<c:url value='${urlPrefix}UpdtView.do'/>?${pk.javaName}=${'$'}{result.${pk.javaName}}">수정</a>
        <a href="<c:url value='${urlPrefix}List.do'/>">목록</a>
        <form name="deleteForm" action="<c:url value='${urlPrefix}Delete.do'/>" method="post">
            <input type="hidden" name="${pk.javaName}" value="${'$'}{result.${pk.javaName}}"/>
            <button type="button" onclick="if(confirm('삭제하시겠습니까?')) document.deleteForm.submit();">삭제</button>
        </form>
    </div>
</div>
</body>
</html>
