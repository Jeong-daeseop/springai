<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="${packageName}.service.impl.${domain}Mapper">

    <resultMap id="${domainLc}Map" type="${packageName}.service.${domain}VO">
<#list fields as f>
        <result property="${f.javaName}" column="${f.columnName}"/>
</#list>
        <result property="bbsNm" column="BBS_NM"/>
    </resultMap>

    <!-- 검색 조건 -->
    <sql id="searchCondition">
        <where>
            AND b.BBS_ID = ${r"#"}{bbsId}
            AND b.USE_AT = 'Y'
            <if test="searchKeyword != null and searchKeyword != ''">
                <choose>
                    <when test="searchCondition == '1'">AND b.NTT_SJ LIKE CONCAT('%', ${r"#"}{searchKeyword}, '%')</when>
                    <when test="searchCondition == '2'">AND b.NTT_CN LIKE CONCAT('%', ${r"#"}{searchKeyword}, '%')</when>
                    <otherwise>AND b.NTCR_NM LIKE CONCAT('%', ${r"#"}{searchKeyword}, '%')</otherwise>
                </choose>
            </if>
        </where>
    </sql>

    <!-- 목록 조회 (게시판 마스터 JOIN) -->
    <select id="select${domain}List" parameterType="${packageName}.service.${domain}VO"
            resultMap="${domainLc}Map">
        SELECT b.*, m.BBS_NM
        FROM ${tableName} b
        LEFT JOIN ${masterTableName} m ON b.BBS_ID = m.BBS_ID
        <include refid="searchCondition"/>
        ORDER BY <#if noticeAtExists>b.NOTICE_AT DESC, </#if>b.SORT_ORDR DESC, b.NTT_ID DESC
        LIMIT ${r"#"}{paginationInfo.firstRecordIndex}, ${r"#"}{paginationInfo.recordCountPerPage}
    </select>

    <!-- 목록 건수 -->
    <select id="select${domain}ListTotCnt" parameterType="${packageName}.service.${domain}VO"
            resultType="int">
        SELECT COUNT(*)
        FROM ${tableName} b
        <include refid="searchCondition"/>
    </select>

    <!-- 단건 조회 -->
    <select id="select${domain}" parameterType="${packageName}.service.${domain}VO"
            resultMap="${domainLc}Map">
        SELECT b.*, m.BBS_NM
        FROM ${tableName} b
        LEFT JOIN ${masterTableName} m ON b.BBS_ID = m.BBS_ID
        WHERE b.BBS_ID = ${r"#"}{${bbsId.javaName}}
          AND b.NTT_ID = ${r"#"}{${nttId.javaName}}
    </select>

    <!-- 등록 (BBS_ID·NTT_ID 포함) -->
    <insert id="insert${domain}" parameterType="${packageName}.service.${domain}VO">
        INSERT INTO ${tableName} (
            <#list insertFields as f>${f.columnName}<#sep>, </#list>
        ) VALUES (
            <#list insertFields as f>${r"#"}{${f.javaName}<#if f.jdbcType??>, jdbcType=${f.jdbcType}</#if>}<#sep>, </#list>
        )
    </insert>

    <!-- 수정 -->
    <update id="update${domain}" parameterType="${packageName}.service.${domain}VO">
        UPDATE ${tableName}
        <set>
<#list formFields as f>
            ${f.columnName} = ${r"#"}{${f.javaName}<#if f.jdbcType??>, jdbcType=${f.jdbcType}</#if>}<#sep>,</#sep>
</#list>
        </set>
        WHERE BBS_ID = ${r"#"}{${bbsId.javaName}}
          AND NTT_ID = ${r"#"}{${nttId.javaName}}
    </update>

    <!-- 논리삭제 -->
    <update id="delete${domain}" parameterType="${packageName}.service.${domain}VO">
        UPDATE ${tableName}
           SET USE_AT = 'N'
         WHERE BBS_ID = ${r"#"}{${bbsId.javaName}}
           AND NTT_ID = ${r"#"}{${nttId.javaName}}
    </update>

    <!-- 조회수 증가 -->
    <update id="updateReadCount" parameterType="${packageName}.service.${domain}VO">
        UPDATE ${tableName}
           SET RDCNT = COALESCE(RDCNT, 0) + 1
         WHERE BBS_ID = ${r"#"}{${bbsId.javaName}}
           AND NTT_ID = ${r"#"}{${nttId.javaName}}
    </update>

    <!-- 게시판 사용 여부 조회 -->
    <select id="selectBoardUseAt" parameterType="${packageName}.service.${domain}VO"
            resultType="String">
        SELECT USE_AT
          FROM ${useTableName!"COMTNBBSUSE"}
         WHERE BBS_ID = ${r"#"}{bbsId}
    </select>

</mapper>
