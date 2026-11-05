package ${packageName}.service.query;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import ${packageName}.entity.${ClassName};
import ${packageName}.entity.query.${ClassName}Query;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * ${functionName} Service 查询增强类
 *
 * @author ${author}
 * @since ${date}
 */
@Slf4j
@Service
@Setter(onMethod_ = {@Autowired})
public class ${ClassName}ServiceQuery {

    /**
     * 增强查询条件组装
     */
    public QueryWrapper<${ClassName}> query(${ClassName}Query ${className}Query, QueryWrapper<${ClassName}> queryWrapper) {
        if (StringUtils.isNotBlank(${className}Query.getQuery())) {
            queryWrapper.apply(${className}Query.getQuery());
        }
<#if serviceQueryList??>
<#list serviceQueryList as sq>
<#if sq.type == "0">
        <#if sq.parameterType?? && sq.parameterType>
        if (StringUtils.isNotBlank(${className}Query.get${sq.parameter}())) {
            queryWrapper.eq("${sq.column}", ${className}Query.get${sq.parameter}());
        }
        <#else>
        if (${className}Query.get${sq.parameter}() != null) {
            queryWrapper.eq("${sq.column}", ${className}Query.get${sq.parameter}());
        }
        </#if>
<#elseif sq.type == "1">
        <#if sq.parameterType?? && sq.parameterType>
        if (StringUtils.isNotBlank(${className}Query.get${sq.parameter}())) {
            queryWrapper.likeRight("${sq.column}", ${className}Query.get${sq.parameter}());
        }
        <#else>
        if (${className}Query.get${sq.parameter}() != null) {
            queryWrapper.likeRight("${sq.column}", ${className}Query.get${sq.parameter}());
        }
        </#if>
<#elseif sq.type == "2">
        if (${className}Query.get${sq.parameter}() != null) {
            queryWrapper.isNull("${sq.column}");
        }
<#elseif sq.type == "3">
        if (${className}Query.get${sq.parameter}() != null) {
            queryWrapper.isNotNull("${sq.column}");
        }
<#elseif sq.type == "4">
        <#if sq.parameterType?? && sq.parameterType>
        if (StringUtils.isNotBlank(${className}Query.get${sq.parameter}())) {
            queryWrapper.inSql("${sq.column}", ${className}Query.get${sq.parameter}());
        }
        <#else>
        if (${className}Query.get${sq.parameter}() != null) {
            queryWrapper.in("${sq.column}", ${className}Query.get${sq.parameter}());
        }
        </#if>
<#elseif sq.type == "5">
        <#if sq.parameterType?? && sq.parameterType>
        if (StringUtils.isNotBlank(${className}Query.get${sq.parameter}())) {
            queryWrapper.notInSql("${sq.column}", ${className}Query.get${sq.parameter}());
        }
        <#else>
        if (${className}Query.get${sq.parameter}() != null) {
            queryWrapper.notIn("${sq.column}", ${className}Query.get${sq.parameter}());
        }
        </#if>
<#elseif sq.type == "6">
        if (${className}Query.get${sq.parameter}() != null) {
            queryWrapper.inSql("${sq.column}", ${sq.sql});
        }
<#elseif sq.type == "7">
        if (${className}Query.get${sq.parameter}() != null) {
            queryWrapper.notInSql("${sq.column}", ${sq.sql});
        }
<#elseif sq.type == "8">
        if (${className}Query.get${sq.start}() != null && ${className}Query.get${sq.end}() != null) {
            queryWrapper.between("${sq.column}", ${className}Query.get${sq.start}(), ${className}Query.get${sq.end}());
        }
<#elseif sq.type == "9">
        if (${className}Query.get${sq.start}() != null && ${className}Query.get${sq.end}() != null) {
            queryWrapper.notBetween("${sq.column}", ${className}Query.get${sq.start}(), ${className}Query.get${sq.end}());
        }
</#if>
</#list>
</#if>
        return queryWrapper;
    }
}
