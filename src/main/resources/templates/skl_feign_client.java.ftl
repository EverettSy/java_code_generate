package ${packageName}.feign;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
<#if responseWrapperClass?? && responseWrapperClass?has_content>
import ${responseWrapperClass};
</#if>
import ${packageName}.entity.bo.${ClassName}BO;
import ${packageName}.entity.query.${ClassName}Query;
import ${packageName}.entity.vo.${ClassName}VO;
<#if docStyle?? && docStyle == "SWAGGER2">
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
<#elseif docStyle?? && docStyle == "OPENAPI3">
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
</#if>
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ${functionName} 远程调用 Feign Client 接口
 *
 * @author ${author}
 * @since ${date}
 */
<#if docStyle?? && docStyle == "SWAGGER2">
@Api(tags = "${functionName} FeignClient")
<#elseif docStyle?? && docStyle == "OPENAPI3">
@Tag(name = "${functionName} FeignClient", description = "${functionName}远程调用接口")
</#if>
@FeignClient(
        name = "${(feignServiceName?has_content)?then(feignServiceName, (moduleName?has_content)?then(moduleName + '-service', className + '-service'))}",
        contextId = "${className}FeignClient",
        path = "/${className}"
)
public interface ${ClassName}FeignClient {
<#assign hasWrap = (responseWrapperSimpleName?? && responseWrapperSimpleName?has_content)>
<#assign wrapName = responseWrapperSimpleName!"">

    /**
     * ${functionName}分页查询
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}分页查询")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}分页查询")
</#if>
    @GetMapping("/select")
    <#if hasWrap>${wrapName}<Page<${ClassName}VO>><#else>Page<${ClassName}VO></#if> select(@SpringQueryMap ${ClassName}Query ${className}Query,
                                @RequestParam(value = "current", required = false) Integer current,
                                @RequestParam(value = "size", required = false) Integer size);

    /**
     * ${functionName}集合查询
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}集合查询")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}集合查询")
</#if>
    @GetMapping("/selectList")
    <#if hasWrap>${wrapName}<List<${ClassName}VO>><#else>List<${ClassName}VO></#if> selectList(@SpringQueryMap ${ClassName}Query ${className}Query);
<#if treeTable?? && treeTable>

    /**
     * ${functionName}树形结构查询
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}树形结构查询")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}树形结构查询")
</#if>
    @GetMapping("/tree")
    <#if hasWrap>${wrapName}<List<${ClassName}VO>><#else>List<${ClassName}VO></#if> tree(@SpringQueryMap ${ClassName}Query ${className}Query);
</#if>

    /**
     * ${functionName}单条查询
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}单条查询")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}单条查询")
</#if>
    @GetMapping("/selectOne")
    <#if hasWrap>${wrapName}<${ClassName}VO><#else>${ClassName}VO</#if> selectOne(@SpringQueryMap ${ClassName}Query ${className}Query);

    /**
     * ${functionName}总数统计
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}总数统计")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}总数统计")
</#if>
    @GetMapping("/count")
    <#if hasWrap>${wrapName}<Long><#else>Long</#if> count(@SpringQueryMap ${ClassName}Query ${className}Query);

    /**
     * ${functionName}新增
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}新增")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}新增")
</#if>
    @PostMapping("/save")
    <#if hasWrap>${wrapName}<String><#else>String</#if> save(@Validated @RequestBody ${ClassName}BO ${className}BO);

    /**
     * ${functionName}修改
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}修改")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}修改")
</#if>
    @PutMapping("/update")
    <#if hasWrap>${wrapName}<Boolean><#else>Boolean</#if> update(@Validated @RequestBody ${ClassName}BO ${className}BO);

    /**
     * ${functionName}删除
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}删除")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}删除")
</#if>
    @DeleteMapping("/remove")
    <#if hasWrap>${wrapName}<Boolean><#else>Boolean</#if> remove(@RequestBody ${ClassName}BO ${className}BO);
}
