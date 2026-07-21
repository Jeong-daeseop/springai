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
               action="${'$'}{pageContext.request.contextPath}${route.resolvedUpdtPath()}"
               method="post">
<#list pkFields as p>
        <form:hidden path="${p.javaName}"/>
</#list>
<#list nonPkFields as f>
<#if !formFields?seq_contains(f)>
        <form:hidden path="${f.javaName}"/>
</#if>
</#list>

        <div class="fieldset">
<#list pkFields as p>
            <div class="form-group">
                <div class="form-tit">
                    <label>${p.comment}</label>
                </div>
                <div class="form-conts">
                    <span><c:out value="${'$'}{${domainLc}VO.${p.javaName}}"/></span>
                </div>
            </div>
</#list>
<#if formColumnLayout == "TWO_COLUMN">
<#list formFields?chunk(2) as pair>
            <div class="form-row-two-col">
<#list pair as f>
                <div class="form-group">
                    <div class="form-tit">
                        <label for="${f.javaName}">${f.comment}</label>
                    </div>
                    <div class="form-conts">
                        <#if f.javaName?lower_case?contains('password')>
                        <form:password path="${f.javaName}" id="${f.javaName}" cssClass="krds-input"
                                    <#if f.maxLength??>maxlength="${f.maxLength?c}"</#if>
                                    placeholder="${f.comment}을(를) 입력하세요"/>
                        <#else>
                        <form:input path="${f.javaName}" id="${f.javaName}" cssClass="krds-input"
                                    <#if f.maxLength??>maxlength="${f.maxLength?c}"</#if>
                                    placeholder="${f.comment}을(를) 입력하세요"/>
                        </#if>
                        <form:errors path="${f.javaName}" cssClass="form-hint-invalid" element="p"/>
                    </div>
                </div>
</#list>
            </div>
</#list>
<#else>
<#list formFields as f>
            <div class="form-group">
                <div class="form-tit">
                    <label for="${f.javaName}">${f.comment}</label>
                </div>
                <div class="form-conts">
                    <#if f.javaName?lower_case?contains('password')>
                    <form:password path="${f.javaName}" id="${f.javaName}" cssClass="krds-input"
                                <#if f.maxLength??>maxlength="${f.maxLength?c}"</#if>
                                placeholder="${f.comment}을(를) 입력하세요"/>
                    <#else>
                    <form:input path="${f.javaName}" id="${f.javaName}" cssClass="krds-input"
                                <#if f.maxLength??>maxlength="${f.maxLength?c}"</#if>
                                placeholder="${f.comment}을(를) 입력하세요"/>
                    </#if>
                    <form:errors path="${f.javaName}" cssClass="form-hint-invalid" element="p"/>
                </div>
            </div>
</#list>
</#if>
        </div>

        <!-- 버튼 -->
        <div class="btn-area">
            <a href="<c:url value='${route.resolvedDetailPath()}'/>?<#list pkFields as p>${p.javaName}=${'$'}{${domainLc}VO.${p.javaName}}<#sep>&</#sep></#list>"
               class="krds-btn secondary medium">취소</a>
            <button type="submit" class="krds-btn primary medium">저장</button>
        </div>
    </form:form>
</div>
<script src="<%=contextPath%>/resources/js/krds.min.js"></script>
</body>
</html>
