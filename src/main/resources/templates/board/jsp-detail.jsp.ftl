<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html>
<head><title>${domainKr} 상세</title></head>
<body>
<h1>${domainKr} 상세</h1>
<table>
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
<p>첨부파일 ID: <c:out value="${r"${result.atchFileId}"}"/></p>
</#if>

<a href="${urlPrefix}List.do">목록</a>
<a href="${urlPrefix}UpdtView.do?${bbsId.javaName}=${r"${result."}${bbsId.javaName}${r"}&"}${nttId.javaName}=${r"${result."}${nttId.javaName}${r"}"}">수정</a>
<form method="post" action="${urlPrefix}Delete.do" style="display:inline">
    <input type="hidden" name="${bbsId.javaName}" value="${r"${result."}${bbsId.javaName}${r"}"}"/>
    <input type="hidden" name="${nttId.javaName}" value="${r"${result."}${nttId.javaName}${r"}"}"/>
    <button type="submit" onclick="return confirm('삭제하시겠습니까?')">삭제</button>
</form>
</body>
</html>
