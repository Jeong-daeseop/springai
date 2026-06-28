<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title layout:title-pattern="$CONTENT_TITLE - eGovFrame">eGovFrame</title>
    <link rel="stylesheet" th:href="@{/resources/css/krds.min.css}">
    <link rel="stylesheet" th:href="@{/resources/css/egov-layout.css}">
</head>
<body>
<div class="container">
    <main layout:fragment="content"></main>
</div>
<script th:src="@{/resources/js/krds.min.js}"></script>
</body>
</html>
