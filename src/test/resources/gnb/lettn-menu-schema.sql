-- generateThymeleafLayout() 이 생성하는 GnbMenuMapper.xml 의 SELECT 대상 테이블.
-- Testcontainers MySQL 기동 시 1회 실행된다.

CREATE TABLE LETTNPROGRMLIST (
    PROGRM_FILE_NM   VARCHAR(60)  NOT NULL PRIMARY KEY,
    PROGRM_KOREAN_NM VARCHAR(60),
    PROGRM_STRE_PATH VARCHAR(120),
    URL              VARCHAR(120)
);

CREATE TABLE LETTNMENUINFO (
    MENU_NO        BIGINT      NOT NULL PRIMARY KEY,
    UPPER_MENU_NO  BIGINT,
    MENU_NM        VARCHAR(60),
    MENU_ORDR      INT,
    PROGRM_FILE_NM VARCHAR(60)
);

INSERT INTO LETTNPROGRMLIST (PROGRM_FILE_NM, PROGRM_KOREAN_NM, PROGRM_STRE_PATH, URL) VALUES
    ('EgovEmpList', '직원 목록',  '/emp', '/emp/list.do'),
    ('EgovBbsList', '게시판 목록', '/bbs', '/bbs/list.do');

-- 최상위(UPPER_MENU_NO = 0) 2건 + 각 1개 자식
INSERT INTO LETTNMENUINFO (MENU_NO, UPPER_MENU_NO, MENU_NM, MENU_ORDR, PROGRM_FILE_NM) VALUES
    (1000, 0,    '직원관리',   1, 'EgovEmpList'),
    (2000, 0,    '게시판관리', 2, 'EgovBbsList'),
    (1100, 1000, '직원 목록',  1, 'EgovEmpList'),
    (2100, 2000, '게시판 목록', 1, 'EgovBbsList');
