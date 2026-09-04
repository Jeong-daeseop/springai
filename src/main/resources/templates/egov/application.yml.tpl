spring:
  application:
    name: ${artifactId}
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}

  datasource:
    # 환경변수로 주입하세요 (예: export DB_URL=jdbc:mysql://host:3306/dbname)
    url: jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul
    username: ${DB_USERNAME:ebt}
    password: ${DB_PASSWORD:ebt01}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: ${DB_POOL_MAX:10}
      minimum-idle: ${DB_POOL_MIN:2}
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

mybatis:
  mapper-locations: classpath*:egovframework/mapper/**/*.xml
  type-aliases-package: ${packageName}
  configuration:
    map-underscore-to-camel-case: true
    default-fetch-size: 100
    default-statement-timeout: 30

server:
  port: ${serverPortExpr}
  servlet:
    encoding:
      charset: UTF-8
      enabled: true
      force: true

logging:
  level:
    root: INFO
    egovframework: DEBUG
    org.mybatis: ${MYBATIS_LOG_LEVEL:DEBUG}   # 운영 배포 시 INFO로 변경

---
# ── local 프로파일 (개발 환경) ──────────────────────────────
spring:
  config:
    activate:
      on-profile: local
  datasource:
    url: jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul
    username: ebt
    password: ebt01

logging:
  level:
    egovframework: DEBUG
    org.mybatis: DEBUG

---
# ── prod 프로파일 (운영 환경) ───────────────────────────────
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: ${DB_POOL_MAX:30}
      minimum-idle: ${DB_POOL_MIN:5}

logging:
  level:
    egovframework: INFO
    org.mybatis: INFO
