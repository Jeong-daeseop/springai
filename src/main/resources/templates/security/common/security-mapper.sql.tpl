-- ============================================================
-- egov-security-mapper 참조 SQL
-- EgovReloadableFilterInvocationSecurityMetadataSource 가
-- 아래 SQL로 DB에서 직접 로드 (별도 XML 파일 불필요)
-- ============================================================

-- [1] URL 패턴 → 권한 매핑 조회 (서버 시작 시 자동 실행)
SELECT ri.ROLE_PTTRN, ar.AUTHOR_CODE
FROM   COMTNROLEINFO ri
JOIN   COMTNAUTHORROLERELATE ar ON ri.ROLE_CODE = ar.ROLE_CODE
ORDER  BY ri.ROLE_SORT ASC;

-- 결과 예시:
--   ROLE_PTTRN                         AUTHOR_CODE
--   \A/uat/uia/.*\.do.*\Z            IS_AUTHENTICATED_ANONYMOUSLY
--   \A/.*\.do.*\Z                    ROLE_ADMIN
--   \A/.*\.do.*\Z                    ROLE_USER
--   \A/uss/umt/.*\.do.*\Z            ROLE_ADMIN

-- [2] ROLE 계층 조회 (COMTNROLES_HIERARCHY)
SELECT PARNTS_ROLE, CHLDRN_ROLE
FROM   COMTNROLES_HIERARCHY;

-- 실제 데이터 (com DB):
--   PARNTS_ROLE                      CHLDRN_ROLE
--   IS_AUTHENTICATED_ANONYMOUSLY     IS_AUTHENTICATED_REMEMBERED
--   IS_AUTHENTICATED_FULLY           ROLE_USER
--   IS_AUTHENTICATED_REMEMBERED      IS_AUTHENTICATED_FULLY
--   ROLE_ANONYMOUS                   IS_AUTHENTICATED_ANONYMOUSLY
--   ROLE_USER                        ROLE_ADMIN

-- [3] 신규 URL 패턴 등록 시 사용 SQL (generateAuthInsertSql 참조)
-- COMTNROLEINFO INSERT → COMTNAUTHORROLERELATE INSERT
-- 등록 후 EgovSecurityContextRefresher.refreshSecurityContext() 호출 시
-- 서버 재기동 없이 즉시 반영됨

-- [4] 등록된 프로그램 목록 확인
SELECT PROGRM_FILE_NM, PROGRM_KOR_NM, URL
FROM   COMTNPROGRMLIST
ORDER  BY PROGRM_FILE_NM;

-- [5] 메뉴-프로그램 연결 확인
SELECT m.MENU_NO, m.MENU_NM, m.PROGRM_FILE_NM, p.URL
FROM   COMTNMENUINFO m
JOIN   COMTNPROGRMLIST p ON m.PROGRM_FILE_NM = p.PROGRM_FILE_NM
ORDER  BY m.MENU_NO;
