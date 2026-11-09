package ${packageName}.entity;

import com.baomidou.mybatisplus.annotation.TableName;
<#if baseEntityClass?? && (baseEntityClass?length > 0) && baseEntityClass?contains(".")>
import ${baseEntityClass};
</#if>
<#if docStyle?? && docStyle == "SWAGGER2">
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
<#elseif docStyle?? && docStyle == "OPENAPI3">
import io.swagger.v3.oas.annotations.media.Schema;
</#if>
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
<#if enumsList?? && (enumsList?size > 0)>
import ${packageName}.entity.enums.*;
</#if>

/**
 * ${functionName} 实体类
 *
 * @author ${author}
 * @since ${date}
 */
@Data
@EqualsAndHashCode(callSuper = <#if baseEntitySimpleName?? && (baseEntitySimpleName?length > 0)>true<#else>false</#if>)
@Accessors(chain = true)
@TableName("${tableName}")
<#if docStyle?? && docStyle == "SWAGGER2">
@ApiModel(value = "${ClassName}对象", description = "${functionName}")
<#elseif docStyle?? && docStyle == "OPENAPI3">
@Schema(name = "${ClassName}", description = "${functionName}")
</#if>
public class ${ClassName}<#if baseEntitySimpleName?? && (baseEntitySimpleName?length > 0)> extends ${baseEntitySimpleName}</#if> implements Serializable {

    private static final long serialVersionUID = 1L;
<#list (entityColumns!list) as column>

    /**
     * ${column.columnComment!}
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiModelProperty(value = "${column.columnComment!}")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Schema(description = "${column.columnComment!}")
</#if>
    private ${column.dataType} ${column.columnName};
</#list>
}
