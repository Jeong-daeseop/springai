<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{${layoutView}}">
<head>
    <title>${master.domainKr} 등록</title>
</head>
<th:block layout:fragment="content">
<#include "thymeleaf-regist-body.html.ftl">
</th:block>
</html>
