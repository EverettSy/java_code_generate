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
<#if subTableRelations?? && (subTableRelations?size > 0)>
import java.util.List;
</#if>
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
<#if subTableRelations?? && (subTableRelations?size > 0)>
<#list subTableRelations as rel>

    /**
     * ${rel.relationComment! (rel.subClassName + "子表集合")} (关联子表: ${rel.subTableName}.${rel.foreignKeyColumn})
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiModelProperty(value = "${rel.relationComment! (rel.subClassName + "子表集合")}")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Schema(description = "${rel.relationComment! (rel.subClassName + "子表集合")}")
</#if>
    private List<${rel.subClassName}BO> ${rel.subClassVarName}List;
</#list>
</#if>
}
