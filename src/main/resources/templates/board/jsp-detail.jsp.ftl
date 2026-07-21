<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<% String contextPath = request.getContextPath(); %>
<!DOCTYPE html>
<html>
<head>
    <title>${displayName} 상세</title>
    <link rel="stylesheet" href="<%=contextPath%>/resources/css/styles.css">
</head>
<body>
<h1>${displayName} 상세</h1>
<table>
    <caption>${displayName} 상세 정보</caption>
    <tbody>
<#list fields as f>
        <tr>
            <th>${f.comment}</th>
            <td><c:out value="${r"${result."}${f.javaName}${r"}"}"/></td>
        </tr>
</#list>
    </tbody>
</table>

<#if hasFile>
<h3>첨부파일</h3>
<ul>
    <c:forEach var="file" items="${r"${fileList}"}">
        <li><a href="<c:url value='/cmm/fms/FileDown.do'><c:param name='atchFileId' value='${r"${file.atchFileId}"}'/><c:param name='fileSn' value='${r"${file.fileSn}"}'/></c:url>"><c:out value="${r"${file.originalFileName}"}"/></a> (<c:out value="${r"${file.fileSize}"}"/> bytes)</li>
    </c:forEach>
    <c:if test="${r"${empty fileList}"}"><li>첨부파일 없음</li></c:if>
</ul>
</#if>

<a href="${urlPrefix}List.do?bbsId=${r"${result."}${bbsId.javaName}${r"}"}">목록</a>
<a href="${urlPrefix}UpdtView.do?${bbsId.javaName}=${r"${result."}${bbsId.javaName}${r"}&"}${nttId.javaName}=${r"${result."}${nttId.javaName}${r"}"}">수정</a>
<form method="post" action="${urlPrefix}Delete.do" class="egov-jsp-inline-form">
    <c:if test="${r"${not empty _csrf}"}">
        <input type="hidden" name="${r"${_csrf.parameterName}"}" value="${r"${_csrf.token}"}"/>
    </c:if>
    <input type="hidden" name="${bbsId.javaName}" value="${r"${result."}${bbsId.javaName}${r"}"}"/>
    <input type="hidden" name="${nttId.javaName}" value="${r"${result."}${nttId.javaName}${r"}"}"/>
    <button type="submit" onclick="return confirm('삭제하시겠습니까?')">삭제</button>
</form>
<script src="<%=contextPath%>/resources/js/krds.min.js"></script>
</body>
</html>
