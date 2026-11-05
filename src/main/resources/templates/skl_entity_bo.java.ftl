package ${packageName}.entity.bo;

<#if docStyle?? && docStyle == "SWAGGER2">
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
<#elseif docStyle?? && docStyle == "OPENAPI3">
import io.swagger.v3.oas.annotations.media.Schema;
</#if>
<#if enableValidation?? && enableValidation>
import ${validationPkg! "javax.validation.constraints"}.*;
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
 * ${functionName} 业务传输对象 (BO)
 *
 * @author ${author}
 * @since ${date}
 */
@Data
@Accessors(chain = true)
<#if docStyle?? && docStyle == "SWAGGER2">
@ApiModel(value = "${ClassName}BO对象", description = "${functionName}BO")
<#elseif docStyle?? && docStyle == "OPENAPI3">
@Schema(name = "${ClassName}BO", description = "${functionName}BO")
</#if>
public class ${ClassName}BO implements Serializable {

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
<#if column.validationAnnotations??>
<#list column.validationAnnotations as valAnn>
    ${valAnn}
</#list>
</#if>
    private ${column.dataType} ${column.columnName};
</#list>
}
