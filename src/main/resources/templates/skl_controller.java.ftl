package ${packageName}.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
<#if responseWrapperClass?? && responseWrapperClass?has_content>
import ${responseWrapperClass};
</#if>
import ${packageName}.entity.bo.${ClassName}BO;
<#if excelEnabled?? && excelEnabled>
import ${packageName}.entity.excel.${ClassName}ExcelDTO;
</#if>
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
<#if excelEnabled?? && excelEnabled>
import org.springframework.beans.BeanUtils;
</#if>
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

<#if excelEnabled?? && excelEnabled>
import java.util.ArrayList;
import java.util.Collections;
</#if>
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
<#assign hasWrap = (responseWrapperSimpleName?? && responseWrapperSimpleName?has_content)>
<#assign wrapName = responseWrapperSimpleName!"">
<#assign okMethod = (responseWrapperSuccessMethod?? && responseWrapperSuccessMethod?has_content)?then(responseWrapperSuccessMethod, "ok")>

    /**
     * 分页查询
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}分页查询")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}分页查询")
</#if>
    @GetMapping("/select")
    public <#if hasWrap>${wrapName}<Page<${ClassName}VO>><#else>Page<${ClassName}VO></#if> select(${ClassName}Query ${className}Query, Integer current, Integer size) {
        Page page = new Page(current != null ? current : 1, size != null ? size : 10);
        return <#if hasWrap>${wrapName}.${okMethod}(${className}Service.pageEnhance(page, ${className}Query))<#else>${className}Service.pageEnhance(page, ${className}Query)</#if>;
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
    public <#if hasWrap>${wrapName}<List<${ClassName}VO>><#else>List<${ClassName}VO></#if> selectList(${ClassName}Query ${className}Query) {
        return <#if hasWrap>${wrapName}.${okMethod}(${className}Service.listEnhance(${className}Query))<#else>${className}Service.listEnhance(${className}Query)</#if>;
    }
<#if treeTable?? && treeTable>

    /**
     * 树形结构查询
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}树形结构查询")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}树形结构查询")
</#if>
    @GetMapping("/tree")
    public <#if hasWrap>${wrapName}<List<${ClassName}VO>><#else>List<${ClassName}VO></#if> tree(${ClassName}Query ${className}Query) {
        return <#if hasWrap>${wrapName}.${okMethod}(${className}Service.treeEnhance(${className}Query))<#else>${className}Service.treeEnhance(${className}Query)</#if>;
    }
</#if>

    /**
     * 单条查询
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}单条查询")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}单条查询")
</#if>
    @GetMapping("/selectOne")
    public <#if hasWrap>${wrapName}<${ClassName}VO><#else>${ClassName}VO</#if> selectOne(${ClassName}Query ${className}Query) {
        return <#if hasWrap>${wrapName}.${okMethod}(${className}Service.getOneEnhance(${className}Query))<#else>${className}Service.getOneEnhance(${className}Query)</#if>;
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
    public <#if hasWrap>${wrapName}<Long><#else>Long</#if> count(${ClassName}Query ${className}Query) {
        return <#if hasWrap>${wrapName}.${okMethod}(${className}Service.countEnhance(${className}Query))<#else>${className}Service.countEnhance(${className}Query)</#if>;
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
    public <#if hasWrap>${wrapName}<String><#else>String</#if> save(@Validated ${ClassName}BO ${className}BO) {
        return <#if hasWrap>${wrapName}.${okMethod}(${className}Service.saveEnhance(${className}BO))<#else>${className}Service.saveEnhance(${className}BO)</#if>;
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
    public <#if hasWrap>${wrapName}<Boolean><#else>Boolean</#if> update(@Validated ${ClassName}BO ${className}BO) {
        return <#if hasWrap>${wrapName}.${okMethod}(${className}Service.updateEnhance(${className}BO))<#else>${className}Service.updateEnhance(${className}BO)</#if>;
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
    public <#if hasWrap>${wrapName}<Boolean><#else>Boolean</#if> remove(${ClassName}BO ${className}BO) {
        return <#if hasWrap>${wrapName}.${okMethod}(${className}Service.removeEnhance(${className}BO))<#else>${className}Service.removeEnhance(${className}BO)</#if>;
    }
<#if excelEnabled?? && excelEnabled>

    /**
     * 导出 Excel 数据
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}导出Excel")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}导出Excel")
</#if>
    @GetMapping("/exportExcel")
    public <#if hasWrap>${wrapName}<List<${ClassName}ExcelDTO>><#else>List<${ClassName}ExcelDTO></#if> exportExcel(${ClassName}Query ${className}Query) {
        List<${ClassName}VO> voList = ${className}Service.listEnhance(${className}Query);
        if (voList == null || voList.isEmpty()) {
            return <#if hasWrap>${wrapName}.${okMethod}(Collections.emptyList())<#else>Collections.emptyList()</#if>;
        }
        List<${ClassName}ExcelDTO> excelList = new ArrayList<>(voList.size());
        for (${ClassName}VO vo : voList) {
            ${ClassName}ExcelDTO dto = new ${ClassName}ExcelDTO();
            BeanUtils.copyProperties(vo, dto);
            excelList.add(dto);
        }
        return <#if hasWrap>${wrapName}.${okMethod}(excelList)<#else>excelList</#if>;
    }

    /**
     * 批量导入 Excel 数据
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiOperation(value = "${functionName}批量导入Excel")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Operation(summary = "${functionName}批量导入Excel")
</#if>
    @PostMapping("/importExcel")
    public <#if hasWrap>${wrapName}<Integer><#else>Integer</#if> importExcel(@RequestBody List<${ClassName}ExcelDTO> excelList) {
        if (excelList == null || excelList.isEmpty()) {
            return <#if hasWrap>${wrapName}.${okMethod}(0)<#else>0</#if>;
        }
        int successCount = 0;
        for (${ClassName}ExcelDTO dto : excelList) {
            ${ClassName}BO bo = new ${ClassName}BO();
            BeanUtils.copyProperties(dto, bo);
            ${className}Service.saveEnhance(bo);
            successCount++;
        }
        return <#if hasWrap>${wrapName}.${okMethod}(successCount)<#else>successCount</#if>;
    }
</#if>
}
