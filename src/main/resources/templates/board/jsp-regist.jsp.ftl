<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html>
<head><title>${domainKr} 등록</title></head>
<body>
<h1>${domainKr} 등록</h1>
<form method="post" action="${urlPrefix}Regist.do">
    <input type="hidden" name="${bbsId.javaName}" value="${r"${"}${domainLc}${r"VO."}${bbsId.javaName}${r"}"}"/>
    <table>
<#list formFields as f>
        <tr>
            <th>${f.comment}</th>
            <td><input type="text" name="${f.javaName}" value="${r"${"}${domainLc}${r"VO."}${f.javaName}${r"}"}"/></td>
        </tr>
</#list>
    </table>
    <a href="${urlPrefix}List.do">취소</a>
    <button type="submit">저장</button>
</form>
</body>
</html>
