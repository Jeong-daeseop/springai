<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <title layout:title-pattern="$CONTENT_TITLE - eGovFrame">eGovFrame</title>
    <link rel="stylesheet" th:href="@{/resources/css/styles.css}">
    <style>
        * { box-sizing: border-box; }
        html { font-size: 16px; }
        body {
            margin: 0;
            font-family: "Pretendard GOV", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
            color: #1e2124;
            background: #fff;
            line-height: 1.5;
        }
        a { color: inherit; text-decoration: none; }
        button, input, select, textarea { font: inherit; }
        input:focus, select:focus, textarea:focus, button:focus-visible {
            outline: 2px solid #256ef4;
            outline-offset: -1px;
        }
        input::placeholder, textarea::placeholder { color: #8a949e; }
        .lnb-link { display:block; padding:8px 12px; color:#58616a; border-radius:2px; }
        .lnb-link.lnb-active { color:#256ef4; font-weight:700; background:#eef3fe; }
        .lnb-link:hover { background:#f4f5f6; }
        .gnb-main-trigger.gnb-active { font-weight:800 !important; border-bottom:2px solid #083891; }
        @media (max-width: 900px) {
            [data-layout-shell] { display: block !important; padding: 24px 16px 48px !important; }
            [data-layout-sidebar] { width: auto !important; margin-bottom: 24px; }
            [data-layout-header-inner] { flex-wrap: wrap; }
            [data-layout-header-inner] .krds-main-menu { width: 100%; overflow-x: auto; }
        }
    </style>
    <th:block layout:fragment="head"></th:block>
</head>
<body>
<th:block th:replace="~{layout/gnb :: gnb}"></th:block>
<main data-layout-shell
      style="max-width:1200px;margin:0 auto;padding:32px 24px 60px;display:flex;align-items:flex-start;gap:32px;">
    <th:block th:replace="~{layout/lnb :: lnb}"></th:block>
    <section style="flex:1;min-width:0;">
        <th:block layout:fragment="content"></th:block>
    </section>
</main>
<th:block th:replace="~{layout/footer :: footer}"></th:block>
<script th:src="@{/resources/js/krds.min.js}"></script>
<th:block layout:fragment="scripts"></th:block>
</body>
</html>
