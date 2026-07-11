<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%
    String contextPath = request.getContextPath();
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${domainKr} 상세</title>
    <link rel="stylesheet" href="<%=contextPath%>/resources/css/styles.css">
</head>
<body>
<div class="container">
    <h2 class="page-title">${domainKr} 상세</h2>

    <c:if test="${'$'}{not empty message}">
    <div class="alert alert-success">
        <c:out value="${'$'}{message}"/>
    </div>
    </c:if>

    <div class="krds-table-wrap">
        <table class="tbl col">
            <caption>${domainKr} 상세 정보</caption>
            <colgroup>
                <col class="egov-detail-label-col">
                <col>
            </colgroup>
            <tbody>
<#list fields as f>
            <tr>
                <th scope="row">${f.comment}</th>
                <td><c:out value="${'$'}{result.${f.javaName}}"/></td>
            </tr>
</#list>
            </tbody>
        </table>
    </div>

    <!-- 버튼 -->
    <div class="btn-area">
        <a href="<c:url value='${urlPrefix}List.do'/>" class="krds-btn secondary medium">목록</a>
        <a href="<c:url value='${urlPrefix}UpdtView.do'/>?<#list pkFields as p>${p.javaName}=${'$'}{result.${p.javaName}}<#sep>&</#sep></#list>"
           class="krds-btn primary medium">수정</a>
        <form name="deleteForm" action="<c:url value='${urlPrefix}Delete.do'/>" method="post" class="egov-jsp-inline-form">
<#list pkFields as p>
            <input type="hidden" name="${p.javaName}" value="${'$'}{result.${p.javaName}}"/>
</#list>
            <button type="button" class="krds-btn tertiary medium"
                    onclick="if(confirm('삭제하시겠습니까?')) document.deleteForm.submit();">삭제</button>
        </form>
    </div>
</div>
<script src="<%=contextPath%>/resources/js/krds.min.js"></script>
</body>
</html>
