package ${packageName}.entity.query;

<#if docStyle?? && docStyle == "SWAGGER2">
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
<#elseif docStyle?? && docStyle == "OPENAPI3">
import io.swagger.v3.oas.annotations.media.Schema;
</#if>
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
<#if enumsList?? && (enumsList?size > 0)>
import ${packageName}.entity.enums.*;
</#if>

/**
 * ${functionName} 查询参数对象 (Query)
 *
 * @author ${author}
 * @since ${date}
 */
@Data
@Accessors(chain = true)
<#if docStyle?? && docStyle == "SWAGGER2">
@ApiModel(value = "${ClassName}Query对象", description = "${functionName}查询参数")
<#elseif docStyle?? && docStyle == "OPENAPI3">
@Schema(name = "${ClassName}Query", description = "${functionName}查询参数")
</#if>
public class ${ClassName}Query implements Serializable {

    private static final long serialVersionUID = 1L;
<#list list as column>

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

    /**
     * 追加 SQL 查询条件
     */
    private String query;

    /**
     * 排序字段
     */
    private String collationFields = "create_date_time";

    /**
     * 是否正序排列
     */
    private Boolean collation = false;

    /**
     * 指定查询字段列表
     */
    private String fields;
<#if beanQuery??>
<#list beanQuery as bq>

    /**
     * ${bq.description!}
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiModelProperty(value = "${bq.description!}")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Schema(description = "${bq.description!}")
</#if>
    private ${bq.format} ${bq.name};
</#list>
</#if>
}
