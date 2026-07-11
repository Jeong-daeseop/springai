<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <title>${master.domainKr} 상세</title>
    <link rel="stylesheet" th:href="@{/resources/css/styles.css}">
</head>
<body>
<div class="egov-standalone-shell">
<#include "thymeleaf-detail-body.html.ftl">
</div>
<script th:src="@{/resources/js/krds.min.js}"></script>
</body>
</html>
