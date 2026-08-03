-- ARCH-0501~0507: 공통 Artifact 계약. Figma Bundle, Web Capture, Thymeleaf Preview,
-- Validation Report 등 서로 다른 도메인 산출물을 하나의 catalog로 추적한다.
-- CONTENT_HASH UNIQUE로 동일 내용 재수집을 멱등 처리한다(WORM, content-addressed).
-- AI_ARTIFACT_LINK는 도메인 무관 operationId 문자열과 Artifact의 다대다 연결이다.
-- 신규 테이블이므로 V2(Thymeleaf Operation)와 마찬가지로 Flyway로만 관리하고
-- 기존 9개 legacy repository 같은 @PostConstruct DDL 이중 안전망을 두지 않는다.

CREATE TABLE AI_ARTIFACT (
    ARTIFACT_ID      VARCHAR(64)  NOT NULL,
    ARTIFACT_TYPE    VARCHAR(64)  NOT NULL,
    MEDIA_TYPE       VARCHAR(128) NOT NULL,
    SIZE_BYTES       BIGINT       NOT NULL,
    CONTENT_HASH     VARCHAR(64)  NOT NULL,
    SOURCE_REVISION  VARCHAR(128),
    STORAGE_URI      VARCHAR(512) NOT NULL,
    STATUS           VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    CREATED_AT       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ARTIFACT_ID),
    UNIQUE KEY UK_ARTIFACT_CONTENT_HASH (CONTENT_HASH)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE AI_ARTIFACT_LINK (
    OPERATION_ID     VARCHAR(64) NOT NULL,
    OPERATION_TYPE   VARCHAR(64) NOT NULL,
    ARTIFACT_ID      VARCHAR(64) NOT NULL,
    LINKED_AT        DATETIME    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (OPERATION_ID, ARTIFACT_ID),
    KEY IX_ARTIFACT_LINK_ARTIFACT (ARTIFACT_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
