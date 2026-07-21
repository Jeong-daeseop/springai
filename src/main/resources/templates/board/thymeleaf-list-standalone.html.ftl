<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <title>${displayName} 목록</title>
    <link rel="stylesheet" th:href="@{/resources/css/styles.css}">
</head>
<body>
<div class="egov-standalone-shell egov-crud-page">
<#include "thymeleaf-list-body.html.ftl">
</div>
<script th:src="@{/resources/js/krds.min.js}"></script>
<script>
document.querySelectorAll('tr[data-row-link="true"][data-href]').forEach(function(row) {
    row.addEventListener('click', function() {
        window.location.href = this.dataset.href;
    });
});
</script>
</body>
</html>
