<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="${packageName}.mapper.${ClassName}Mapper">

    <!-- 通用查询映射结果 -->
    <resultMap id="BaseResultMap" type="${packageName}.entity.${ClassName}">
<#list list as column>
<#if column.primaryKey?? && column.primaryKey>
        <id column="${column.typeName}" property="${column.columnName}" />
<#else>
        <result column="${column.typeName}" property="${column.columnName}" />
</#if>
</#list>
    </resultMap>

    <!-- 通用查询结果列 -->
    <sql id="Base_Column_List">
        <#list list as column>${column.typeName}<#if column_has_next>, </#if></#list>
    </sql>

</mapper>
