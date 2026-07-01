<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c"    uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form"%>
<%
    String contextPath = request.getContextPath();
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${domainKr} 수정</title>
    <link rel="stylesheet" href="<%=contextPath%>/resources/css/styles.css">
</head>
<body>
<div class="container">
    <h2 class="page-title">${domainKr} 수정</h2>

    <form:form modelAttribute="${domainLc}VO"
               action="${'$'}{pageContext.request.contextPath}${urlPrefix}Updt.do"
               method="post">
        <form:hidden path="${pk.javaName}"/>

        <div class="fieldset">
<#list nonPkFields as f>
            <div class="form-group">
                <div class="form-tit">
                    <label for="${f.javaName}">${f.comment}</label>
                </div>
                <div class="form-conts">
                    <form:input path="${f.javaName}" id="${f.javaName}" cssClass="krds-input"
                                <#if f.maxLength??>maxlength="${f.maxLength}"</#if>
                                placeholder="${f.comment}을(를) 입력하세요"/>
                    <form:errors path="${f.javaName}" cssClass="form-hint-invalid" element="p"/>
                </div>
            </div>
</#list>
        </div>

        <!-- 버튼 -->
        <div class="btn-area">
            <a href="<c:url value='${urlPrefix}Detail.do'/>?${pk.javaName}=${'$'}{${domainLc}VO.${pk.javaName}}"
               class="krds-btn secondary medium">취소</a>
            <button type="submit" class="krds-btn primary medium">저장</button>
        </div>
    </form:form>
</div>
<script src="<%=contextPath%>/resources/js/krds.min.js"></script>
</body>
</html>
