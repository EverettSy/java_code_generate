package ${packageName}.entity.vo;

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
 * ${functionName} 视图渲染对象 (VO)
 *
 * @author ${author}
 * @since ${date}
 */
@Data
@Accessors(chain = true)
<#if docStyle?? && docStyle == "SWAGGER2">
@ApiModel(value = "${ClassName}VO对象", description = "${functionName}VO")
<#elseif docStyle?? && docStyle == "OPENAPI3">
@Schema(name = "${ClassName}VO", description = "${functionName}VO")
</#if>
public class ${ClassName}VO implements Serializable {

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
}
