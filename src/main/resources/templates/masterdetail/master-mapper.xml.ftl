<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="${packageName}.service.impl.${master.domain}Mapper">

    <resultMap id="${master.domainLc}Map" type="${packageName}.service.${master.domain}VO">
        <id property="${master.pk.javaName}" column="${master.pk.columnName}"/>
<#list master.nonPkFields as f>
        <result property="${f.javaName}" column="${f.columnName}"/>
</#list>
<#list queryContract.displayFields() as f>
        <result property="${f.javaName}" column="${f.columnName}"/>
</#list>
    </resultMap>

    <sql id="searchCondition">
        <where>
            <if test="searchKeyword != null and searchKeyword != ''">
                AND <#if queryContract.hasJoins()>t.</#if>${master.pk.columnName} LIKE CONCAT('%', #{searchKeyword}, '%')
            </if>
        </where>
    </sql>

    <select id="select${master.domain}List" parameterType="${packageName}.service.${master.domain}VO"
            resultMap="${master.domainLc}Map">
        SELECT <#list master.fields as f><#if queryContract.hasJoins()>t.</#if>${f.columnName}<#sep>, </#list><#list queryContract.projections as p>, ${p.selectExpression}</#list>
        FROM ${master.tableName}<#if queryContract.hasJoins()> t</#if>
<#list queryContract.joins as join>
        ${join.joinType} JOIN ${join.schema}.${join.table} ${join.alias} ON ${join.onExpression}
</#list>
        <include refid="searchCondition"/>
        ORDER BY <#if queryContract.hasJoins()>t.</#if>${master.pk.columnName} DESC
        LIMIT #{paginationInfo.firstRecordIndex}, #{paginationInfo.recordCountPerPage}
    </select>

    <select id="select${master.domain}ListTotCnt" parameterType="${packageName}.service.${master.domain}VO"
            resultType="int">
        SELECT COUNT(*)
        FROM ${master.tableName}<#if queryContract.hasJoins()> t</#if>
        <include refid="searchCondition"/>
    </select>

    <select id="select${master.domain}" parameterType="${packageName}.service.${master.domain}VO"
            resultMap="${master.domainLc}Map">
        SELECT <#list master.fields as f><#if queryContract.hasJoins()>t.</#if>${f.columnName}<#sep>, </#list><#list queryContract.projections as p>, ${p.selectExpression}</#list>
        FROM ${master.tableName}<#if queryContract.hasJoins()> t</#if>
<#list queryContract.joins as join>
        ${join.joinType} JOIN ${join.schema}.${join.table} ${join.alias} ON ${join.onExpression}
</#list>
        WHERE <#if queryContract.hasJoins()>t.</#if>${master.pk.columnName} = #{${master.pk.javaName}}
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

    <delete id="delete${master.domain}Bulk">
        DELETE FROM ${master.tableName}
        WHERE ${master.pk.columnName} IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
    </delete>

</mapper>
