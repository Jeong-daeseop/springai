<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c"    uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form"%>
<!DOCTYPE html>
<html>
<head>
    <title>${domainKr} 수정</title>
    <style>.error-msg { color: red; font-size: 0.85em; }</style>
</head>
<body>
<div class="container">
    <h2>${domainKr} 수정</h2>

    <form:form modelAttribute="${domainLc}VO"
               action="${'$'}{pageContext.request.contextPath}${urlPrefix}Updt.do"
               method="post">
        <form:hidden path="${pk.javaName}"/>

        <table>
            <tbody>
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
            <a href="<c:url value='${urlPrefix}Detail.do'/>?${pk.javaName}=${'$'}{${domainLc}VO.${pk.javaName}}">취소</a>
        </div>
    </form:form>
</div>
</body>
</html>
