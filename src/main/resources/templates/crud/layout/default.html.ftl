<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">

    <title layout:title-pattern="$CONTENT_TITLE - eGovFrame">eGovFrame</title>

    <!-- Bootstrap 5 -->
    <link rel="stylesheet"
          href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css">
    <!-- Bootstrap Icons -->
    <link rel="stylesheet"
          href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
    <!-- KRDS (WAR static resource path) -->
    <link rel="stylesheet" th:href="@{/resources/css/krds.min.css}">

    <!-- eGov layout custom style -->
    <link rel="stylesheet" th:href="@{/resources/css/egov-layout.css(v=202606230650)}">

    <!-- Page-specific head slot -->
    <th:block layout:fragment="head"></th:block>
</head>
<body>

<!-- Top header -->
<header id="topHeader">
    <!-- Sidebar toggle (mobile) -->
    <button class="btn btn-sm btn-outline-secondary d-md-none border-0"
            id="sidebarToggle" type="button" aria-label="메뉴 열기">
        <i class="bi bi-list fs-5"></i>
    </button>

    <!-- Brand -->
    <a th:href="@{/}" class="brand">
        <i class="bi bi-grid-3x3-gap-fill me-1"></i>eGovFrame
    </a>

    <!-- Header right menu -->
    <ul class="nav ms-auto align-items-center">
        <li class="nav-item d-none d-md-block">
            <span class="nav-link text-muted small">
                <i class="bi bi-person-circle me-1"></i>
                <span th:text="${'$'}{#authentication?.name ?: '관리자'}">관리자</span>
            </span>
        </li>
        <li class="nav-item">
            <a th:href="@{/logout}" class="nav-link">
                <i class="bi bi-box-arrow-right me-1"></i>로그아웃
            </a>
        </li>
    </ul>
</header>

<!-- Left sidebar -->
<nav id="sidebar">
    <div class="sidebar-label">사용자 관리</div>
    <ul class="nav flex-column">
        <li class="nav-item">
            <a th:href="@{/cop/emp/list.do}"
               th:classappend="${'$'}{#httpServletRequest != null and #httpServletRequest.requestURI != null and #httpServletRequest.requestURI.contains('/cop/emp/')} ? 'active' : ''"
               class="nav-link">
                <i class="bi bi-people"></i>직원정보
            </a>
        </li>
    </ul>

    <div class="sidebar-label mt-2">시스템</div>
    <ul class="nav flex-column">
        <li class="nav-item">
            <a th:href="@{/}" class="nav-link">
                <i class="bi bi-house"></i>대시보드
            </a>
        </li>
    </ul>
</nav>

<!-- Main content -->
<main id="mainContent">
    <!-- Page content injection -->
    <div layout:fragment="content"></div>
</main>

<!-- Bootstrap JS -->
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>

<!-- Sidebar toggle (mobile) -->
<script>
document.getElementById('sidebarToggle')?.addEventListener('click', function () {
    document.getElementById('sidebar').classList.toggle('show');
});
</script>

<!-- Page-specific script slot -->
<th:block layout:fragment="scripts"></th:block>

</body>
</html>
