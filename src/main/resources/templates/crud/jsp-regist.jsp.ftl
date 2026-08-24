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
    <title>${domainKr} 등록</title>
    <link rel="stylesheet" href="<%=contextPath%>/resources/css/styles.css">
</head>
<body>
<div class="container">
    <h2 class="page-title">${domainKr} 등록</h2>

    <form:form modelAttribute="${domainLc}VO"
               action="${'$'}{pageContext.request.contextPath}${route.resolvedRegistPath()}"
               method="post">

        <div class="fieldset">
<#list pkFields as f>
            <div class="form-group">
                <div class="form-tit">
                    <label for="${f.javaName}">${f.comment}</label>
                </div>
                <div class="form-conts">
                    <form:input path="${f.javaName}" id="${f.javaName}" cssClass="krds-input"
                                <#if f.maxLength??>maxlength="${f.maxLength?c}"</#if>
                                placeholder="${f.comment}을(를) 입력하세요"/>
                    <form:errors path="${f.javaName}" cssClass="form-hint-invalid" element="p"/>
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
            <a href="<c:url value='${route.resolvedListPath()}'/>" class="krds-btn secondary medium">취소</a>
            <button type="submit" class="krds-btn primary medium">저장</button>
        </div>
    </form:form>
</div>
<script src="<%=contextPath%>/resources/js/krds.min.js"></script>
<%-- @region:protected:customSection start --%>
<%-- 이 위치의 커스텀 마크업/스크립트는 재생성 시 보존됩니다. --%>
<%-- @region:protected:customSection end --%>
</body>
</html>
