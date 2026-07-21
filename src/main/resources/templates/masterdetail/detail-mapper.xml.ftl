<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="${packageName}.service.impl.${detail.domain}Mapper">

    <resultMap id="${detail.domainLc}Map" type="${packageName}.service.${detail.domain}VO">
        <id property="${detail.pk.javaName}" column="${detail.pk.columnName}"/>
<#list detail.nonPkFields as f>
        <result property="${f.javaName}" column="${f.columnName}"/>
</#list>
    </resultMap>

    <select id="select${detail.domain}List" parameterType="String"
            resultMap="${detail.domainLc}Map">
        SELECT <#list detail.fields as f>${f.columnName}<#sep>, </#list>
        FROM ${detail.tableName}
        WHERE ${fkColumn} = #{${fkField}}
        ORDER BY ${detail.pk.columnName} DESC
    </select>

    <select id="select${detail.domain}" parameterType="${packageName}.service.${detail.domain}VO"
            resultMap="${detail.domainLc}Map">
        SELECT <#list detail.fields as f>${f.columnName}<#sep>, </#list>
        FROM ${detail.tableName}
        WHERE ${detail.pk.columnName} = #{${detail.pk.javaName}}
    </select>

    <insert id="insert${detail.domain}" parameterType="${packageName}.service.${detail.domain}VO">
        INSERT INTO ${detail.tableName} (
            <#list detail.fields as f>${f.columnName}<#sep>, </#list>
        ) VALUES (
            <#list detail.fields as f>#{${f.javaName}<#if f.jdbcType??>, jdbcType=${f.jdbcType}</#if>}<#sep>, </#list>
        )
    </insert>

    <update id="update${detail.domain}" parameterType="${packageName}.service.${detail.domain}VO">
        UPDATE ${detail.tableName}
        <set>
<#list detail.nonPkFields as f>
            ${f.columnName} = #{${f.javaName}<#if f.jdbcType??>, jdbcType=${f.jdbcType}</#if>}<#sep>,</#sep>
</#list>
        </set>
        WHERE ${detail.pk.columnName} = #{${detail.pk.javaName}}
    </update>

    <delete id="delete${detail.domain}" parameterType="${packageName}.service.${detail.domain}VO">
        DELETE FROM ${detail.tableName}
        WHERE ${detail.pk.columnName} = #{${detail.pk.javaName}}
    </delete>

    <delete id="delete${detail.domain}ByMasterIds">
        DELETE FROM ${detail.tableName}
        WHERE ${fkColumn} IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
    </delete>

</mapper>
