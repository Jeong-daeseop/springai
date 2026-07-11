<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="${packageName}.service.impl.${domain}Mapper">

    <resultMap id="${domainLc}Map" type="${packageName}.service.${domain}VO">
<#list pkFields as p>
        <id property="${p.javaName}" column="${p.columnName}"/>
</#list>
<#list nonPkFields as f>
        <result property="${f.javaName}" column="${f.columnName}"/>
</#list>
    </resultMap>

    <!-- 검색 조건 -->
    <sql id="searchCondition">
        <where>
            <if test="searchKeyword != null and searchKeyword != ''">
                <choose>
                    <when test="searchCondition == '1'">
                        AND ${pk.columnName} LIKE CONCAT('%', #{searchKeyword}, '%')
                    </when>
                    <otherwise>
                        AND ${pk.columnName} LIKE CONCAT('%', #{searchKeyword}, '%')
                    </otherwise>
                </choose>
            </if>
        </where>
    </sql>

    <!-- 목록 조회 -->
    <select id="select${domain}List" parameterType="${packageName}.service.${domain}VO"
            resultMap="${domainLc}Map">
        SELECT <#list fields as f>${f.columnName}<#sep>, </#list>
        FROM ${tableName}
        <include refid="searchCondition"/>
        ORDER BY ${pk.columnName} DESC
        LIMIT #{paginationInfo.firstRecordIndex}, #{paginationInfo.recordCountPerPage}
    </select>

    <!-- 전체 건수 -->
    <select id="select${domain}ListTotCnt" parameterType="${packageName}.service.${domain}VO"
            resultType="int">
        SELECT COUNT(*)
        FROM ${tableName}
        <include refid="searchCondition"/>
    </select>

    <!-- 단건 조회 -->
    <select id="select${domain}" parameterType="${packageName}.service.${domain}VO"
            resultMap="${domainLc}Map">
        SELECT <#list fields as f>${f.columnName}<#sep>, </#list>
        FROM ${tableName}
        WHERE <#list pkFields as p>${p.columnName} = #{${p.javaName}}<#sep> AND </#sep></#list>
    </select>

    <!-- 등록 -->
    <insert id="insert${domain}" parameterType="${packageName}.service.${domain}VO">
        INSERT INTO ${tableName} (
            <#list fields as f>${f.columnName}<#sep>, </#list>
        ) VALUES (
            <#list fields as f>#{${f.javaName}<#if f.jdbcType??>, jdbcType=${f.jdbcType}</#if>}<#sep>, </#list>
        )
    </insert>

    <!-- 수정 -->
    <update id="update${domain}" parameterType="${packageName}.service.${domain}VO">
        UPDATE ${tableName}
        <set>
<#list nonPkFields as f>
            ${f.columnName} = #{${f.javaName}<#if f.jdbcType??>, jdbcType=${f.jdbcType}</#if>}<#sep>,</#sep>
</#list>
        </set>
        WHERE <#list pkFields as p>${p.columnName} = #{${p.javaName}}<#sep> AND </#sep></#list>
    </update>

    <!-- 삭제 -->
    <delete id="delete${domain}" parameterType="${packageName}.service.${domain}VO">
        DELETE FROM ${tableName}
        WHERE <#list pkFields as p>${p.columnName} = #{${p.javaName}}<#sep> AND </#sep></#list>
    </delete>

</mapper>
