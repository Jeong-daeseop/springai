<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="${packageName}.service.impl.${master.domain}Mapper">

    <resultMap id="${master.domainLc}Map" type="${packageName}.service.${master.domain}VO">
<#list master.fields as f>
<#if f.pk>
        <id property="${f.javaName}" column="${f.columnName}"/>
<#else>
        <result property="${f.javaName}" column="${f.columnName}"/>
</#if>
</#list>
    </resultMap>

    <sql id="searchCondition">
        <where>
            <if test="searchKeyword != null and searchKeyword != ''">
                AND ${master.pk.columnName} LIKE CONCAT('%', #{searchKeyword}, '%')
            </if>
        </where>
    </sql>

    <select id="select${master.domain}List" parameterType="${packageName}.service.${master.domain}VO"
            resultMap="${master.domainLc}Map">
        SELECT <#list master.fields as f>${f.columnName}<#sep>, </#list>
        FROM ${master.tableName}
        <include refid="searchCondition"/>
        ORDER BY ${master.pk.columnName} DESC
        LIMIT #{paginationInfo.firstRecordIndex}, #{paginationInfo.recordCountPerPage}
    </select>

    <select id="select${master.domain}ListTotCnt" parameterType="${packageName}.service.${master.domain}VO"
            resultType="int">
        SELECT COUNT(*)
        FROM ${master.tableName}
        <include refid="searchCondition"/>
    </select>

    <select id="select${master.domain}" parameterType="${packageName}.service.${master.domain}VO"
            resultMap="${master.domainLc}Map">
        SELECT <#list master.fields as f>${f.columnName}<#sep>, </#list>
        FROM ${master.tableName}
        WHERE ${master.pk.columnName} = #{${master.pk.javaName}}
    </select>

    <insert id="insert${master.domain}" parameterType="${packageName}.service.${master.domain}VO">
        INSERT INTO ${master.tableName} (
            <#list master.fields as f>${f.columnName}<#sep>, </#list>
        ) VALUES (
            <#list master.fields as f>#{${f.javaName}<#if f.jdbcType??>, jdbcType=${f.jdbcType}</#if>}<#sep>, </#list>
        )
    </insert>

    <update id="update${master.domain}" parameterType="${packageName}.service.${master.domain}VO">
        UPDATE ${master.tableName}
        <set>
<#list master.nonPkFields as f>
            ${f.columnName} = #{${f.javaName}<#if f.jdbcType??>, jdbcType=${f.jdbcType}</#if>}<#sep>,</#sep>
</#list>
        </set>
        WHERE ${master.pk.columnName} = #{${master.pk.javaName}}
    </update>

    <delete id="delete${master.domain}" parameterType="${packageName}.service.${master.domain}VO">
        DELETE FROM ${master.tableName}
        WHERE ${master.pk.columnName} = #{${master.pk.javaName}}
    </delete>

</mapper>
