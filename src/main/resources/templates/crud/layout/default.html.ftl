<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title layout:title-pattern="$CONTENT_TITLE - eGovFrame">eGovFrame</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
    <link rel="stylesheet" th:href="@{/resources/css/krds.min.css}">
    <th:block layout:fragment="head"></th:block>
</head>
<body>
<!-- 상단 헤더 -->
<header id="topHeader" class="navbar navbar-expand-lg navbar-dark bg-primary fixed-top" style="height:52px;">
    <div class="container-fluid">
        <a class="navbar-brand fw-bold" th:href="@{/}">eGovFrame</a>
        <div class="ms-auto d-flex align-items-center gap-2">
            <span class="text-white small">사용자</span>
            <a th:href="@{/logout}" class="btn btn-outline-light btn-sm">로그아웃</a>
        </div>
    </div>
</header>

<div class="d-flex" style="margin-top:52px;">
    <!-- 좌측 사이드바 -->
    <nav id="sidebar" class="bg-dark text-white flex-shrink-0" style="width:220px; min-height:calc(100vh - 52px);">
        <ul class="nav flex-column py-3">
            <li class="nav-item">
                <a class="nav-link text-white-50 px-3 py-1" href="#">메뉴</a>
            </li>
        </ul>
    </nav>

    <!-- 메인 콘텐츠 -->
    <main id="mainContent" class="flex-grow-1 p-4">
        <th:block layout:fragment="content">
            <!-- 페이지 콘텐츠 -->
        </th:block>
    </main>
</div>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
<script th:src="@{/resources/js/krds.min.js}"></script>
<th:block layout:fragment="scripts"></th:block>
</body>
</html>
