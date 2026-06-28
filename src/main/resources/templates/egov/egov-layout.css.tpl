:root {
    --egov-primary:   #1a73e8;
    --egov-sidebar-w: 220px;
    --egov-header-h:  52px;
}

body {
    font-size: 0.875rem;
    background-color: #f4f6f9;
    color: #333;
}

/* ── 상단 헤더 ── */
#topHeader {
    height: var(--egov-header-h);
    background: #fff;
    border-bottom: 1px solid #e0e0e0;
    position: fixed;
    top: 0; left: 0; right: 0;
    z-index: 1030;
    display: flex;
    align-items: center;
    padding: 0 1rem;
    gap: 1rem;
}
#topHeader .brand {
    font-size: 1.8rem !important;
    font-weight: 700;
    color: var(--egov-primary);
    text-decoration: none;
    white-space: nowrap;
}
#topHeader .nav-link {
    font-size: 1.3rem !important;
    color: #555;
    padding: 0.25rem 0.5rem;
}
#topHeader .nav-link:hover { color: var(--egov-primary); }

/* ── 좌측 사이드바 ── */
#sidebar {
    width: var(--egov-sidebar-w);
    position: fixed;
    top: var(--egov-header-h);
    left: 0;
    bottom: 0;
    background: #fff;
    border-right: 1px solid #e0e0e0;
    overflow-y: auto;
    z-index: 1020;
    transition: transform .25s ease;
}
#sidebar .sidebar-label {
    font-size: 1.1rem !important;
    font-weight: 600;
    color: #9e9e9e;
    letter-spacing: .07em;
    padding: 1rem 1rem 0.25rem;
    text-transform: uppercase;
}
#sidebar .nav-link {
    font-size: 1.3rem !important;
    color: #444;
    padding: 0.4rem 1rem;
    border-radius: 0;
    display: flex;
    align-items: center;
    gap: 0.45rem;
}
#sidebar .nav-link:hover,
#sidebar .nav-link.active {
    background: #e8f0fe;
    color: var(--egov-primary);
}
#sidebar .nav-link i { font-size: 1.4rem; }

/* ── 메인 콘텐츠 ── */
#mainContent {
    margin-top: var(--egov-header-h);
    margin-left: var(--egov-sidebar-w);
    min-height: calc(100vh - var(--egov-header-h));
    transition: margin-left .25s ease;
}
#mainContent h5 {
    font-size: 1.55rem;
}
#mainContent .breadcrumb,
#mainContent .breadcrumb a {
    font-size: 1.2rem;
}
#mainContent .krds-input,
#mainContent .krds-form-select,
#mainContent .krds-btn.small {
    font-size: 1.4rem;
}
#mainContent .krds-btn.small {
    height: 3.6rem;
    min-height: 3.6rem;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding-top: 0;
    padding-bottom: 0;
    line-height: 1;
}
#mainContent form .krds-input,
#mainContent form .krds-form-select,
#mainContent form .krds-btn.small {
    height: 4.4rem;
    min-height: 4.4rem;
}
#mainContent form .krds-btn.small {
    height: 3.6rem;
    min-height: 3.6rem;
}
#mainContent .card-header .small,
#mainContent .krds-pagination,
#mainContent .krds-pagination .page-link,
#mainContent .krds-pagination .page-navi {
    font-size: 1.2rem;
}
#mainContent .tbl th,
#mainContent .tbl td {
    font-size: 1.35rem;
}

/* ── 반응형: 모바일 ── */
@media (max-width: 768px) {
    #sidebar { transform: translateX(-100%); }
    #sidebar.show { transform: translateX(0); }
    #mainContent { margin-left: 0; }
}

/* ── 카드 ── */
.card { border-radius: 0.5rem; }
.card-header { border-radius: 0.5rem 0.5rem 0 0 !important; }

/* ── 테이블 ── */
.table th { font-size: 0.78rem; }
.table td { font-size: 0.82rem; }

/* ── 페이지네이션 ── */
.pagination .page-link { font-size: 0.78rem; }
