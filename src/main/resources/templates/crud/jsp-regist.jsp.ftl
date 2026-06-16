<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c"    uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form"%>
<!DOCTYPE html>
<html>
<head>
    <title>${domainKr} 등록</title>
    <style>.error-msg { color: red; font-size: 0.85em; }</style>
</head>
<body>
<div class="container">
    <h2>${domainKr} 등록</h2>

    <form:form modelAttribute="${domainLc}VO"
               action="${'$'}{pageContext.request.contextPath}${urlPrefix}Regist.do"
               method="post">

        <table>
            <tbody>
<#list fields as f><#if f.pk>
            <tr>
                <th><label for="${f.javaName}">${f.comment}</label></th>
                <td>
                    <form:input path="${f.javaName}"<#if f.maxLength??> maxlength="${f.maxLength}"</#if> id="${f.javaName}"/>
                    <form:errors path="${f.javaName}" cssClass="error-msg"/>
                </td>
            </tr>
</#if></#list>
<#list nonPkFields as f>
            <tr>
                <th><label for="${f.javaName}">${f.comment}</label></th>
                <td>
                    <form:input path="${f.javaName}"<#if f.maxLength??> maxlength="${f.maxLength}"</#if> id="${f.javaName}"/>
                    <form:errors path="${f.javaName}" cssClass="error-msg"/>
                </td>
            </tr>
</#list>
            </tbody>
        </table>

        <div>
            <button type="submit">저장</button>
            <a href="<c:url value='${urlPrefix}List.do'/>">취소</a>
        </div>
    </form:form>
</div>
</body>
</html>
