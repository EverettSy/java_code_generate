package ${packageName}.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ${packageName}.entity.bo.${ClassName}BO;
import ${packageName}.entity.query.${ClassName}Query;
import ${packageName}.entity.vo.${ClassName}VO;
import ${packageName}.service.${ClassName}Service;
<#if docStyle?? && docStyle == "SWAGGER2">
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
<#elseif docStyle?? && docStyle == "OPENAPI3">
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
</#if>
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ${functionName} 前端控制器
 *
 * @author ${author}
 * @since ${date}
 */
@Slf4j
@RestController
@RequestMapping("/${className}")
@Setter(onMethod_ = {@Autowired})
<#if docStyle?? && docStyle == "SWAGGER2">
@Api(tags = "${functionName}")
<#elseif docStyle?? && docStyle == "OPENAPI3">
@Tag(name = "${functionName}", description = "${functionName}接口")
</#if>
public class ${ClassName}Controller {

    private ${ClassName}Service ${className}Service;

    /**
     * 分页查询
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}分页查询")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}分页查询")
</#if>
    @GetMapping("/select")
    public Page<${ClassName}VO> select(${ClassName}Query ${className}Query, Integer current, Integer size) {
        Page page = new Page(current != null ? current : 1, size != null ? size : 10);
        return ${className}Service.pageEnhance(page, ${className}Query);
    }

    /**
     * 集合查询
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}集合查询")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}集合查询")
</#if>
    @GetMapping("/selectList")
    public List<${ClassName}VO> selectList(${ClassName}Query ${className}Query) {
        return ${className}Service.listEnhance(${className}Query);
    }

    /**
     * 单条查询
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}单条查询")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}单条查询")
</#if>
    @GetMapping("/selectOne")
    public ${ClassName}VO selectOne(${ClassName}Query ${className}Query) {
        return ${className}Service.getOneEnhance(${className}Query);
    }

    /**
     * 总数查询
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}总数统计")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}总数统计")
</#if>
    @GetMapping("/count")
    public Long count(${ClassName}Query ${className}Query) {
        return ${className}Service.countEnhance(${className}Query);
    }

    /**
     * 新增
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}新增")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}新增")
</#if>
    @PostMapping("/save")
    public String save(@Validated ${ClassName}BO ${className}BO) {
        return ${className}Service.saveEnhance(${className}BO);
    }

    /**
     * 修改
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}修改")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}修改")
</#if>
    @PutMapping("/update")
    public Boolean update(@Validated ${ClassName}BO ${className}BO) {
        return ${className}Service.updateEnhance(${className}BO);
    }

    /**
     * 删除
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}删除")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}删除")
</#if>
    @DeleteMapping("/remove")
    public Boolean remove(${ClassName}BO ${className}BO) {
        return ${className}Service.removeEnhance(${className}BO);
    }
}
