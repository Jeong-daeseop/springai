<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html>
<head><title>${domainKr} 수정</title></head>
<body>
<h1>${domainKr} 수정</h1>
<form method="post" action="${urlPrefix}Updt.do">
    <input type="hidden" name="${bbsId.javaName}" value="${r"${"}${domainLc}${r"VO."}${bbsId.javaName}${r"}"}"/>
    <input type="hidden" name="${nttId.javaName}" value="${r"${"}${domainLc}${r"VO."}${nttId.javaName}${r"}"}"/>
    <table>
<#list formFields as f>
        <tr>
            <th>${f.comment}</th>
            <td><input type="text" name="${f.javaName}" value="${r"${"}${domainLc}${r"VO."}${f.javaName}${r"}"}"/></td>
        </tr>
</#list>
    </table>
    <a href="${urlPrefix}Detail.do?${bbsId.javaName}=${r"${"}${domainLc}${r"VO."}${bbsId.javaName}${r"}&"}${nttId.javaName}=${r"${"}${domainLc}${r"VO."}${nttId.javaName}${r"}"}">취소</a>
    <button type="submit">저장</button>
</form>
</body>
</html>
