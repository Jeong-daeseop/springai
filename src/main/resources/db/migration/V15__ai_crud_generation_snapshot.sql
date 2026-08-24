-- Scope·Ownership·Revision 체인의 Base 스냅샷 저장소. GenerationOwnershipManifest를 그대로 JSON으로
-- 저장하고 PRIMARY KEY(OPERATION_ID, REVISION)로 compare-and-set을 얻는다
-- (AI_THYMELEAF_PROJECT_OPERATION과 동일한 패턴, V2__ai_thymeleaf_project_operation.sql 참고).

CREATE TABLE AI_CRUD_GENERATION_SNAPSHOT (
    OPERATION_ID   VARCHAR(64) NOT NULL,
    REVISION       INT         NOT NULL,
    SNAPSHOT_JSON  LONGTEXT    NOT NULL,
    CREATED_AT     DATETIME    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (OPERATION_ID, REVISION)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
