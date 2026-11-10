package ${packageName}.entity.excel;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;
<#if docStyle?? && docStyle == "SWAGGER2">
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
<#elseif docStyle?? && docStyle == "OPENAPI3">
import io.swagger.v3.oas.annotations.media.Schema;
</#if>
import lombok.Data;

/**
 * ${functionName} Excel 导入导出模型
 *
 * @author ${author}
 * @since ${date}
 */
@Data
@ExcelIgnoreUnannotated
@HeadRowHeight(20)
@ContentRowHeight(18)
@ColumnWidth(20)
<#if docStyle?? && docStyle == "SWAGGER2">
@ApiModel(value = "${ClassName}ExcelDTO", description = "${functionName}Excel导入导出模型")
<#elseif docStyle?? && docStyle == "OPENAPI3">
@Schema(name = "${ClassName}ExcelDTO", description = "${functionName}Excel导入导出模型")
</#if>
public class ${ClassName}ExcelDTO implements Serializable {

    private static final long serialVersionUID = 1L;
<#list list as column>

    /**
     * ${column.columnComment}
     */
<#if docStyle?? && docStyle == "SWAGGER2">
    @ApiModelProperty(value = "${column.columnComment}")
<#elseif docStyle?? && docStyle == "OPENAPI3">
    @Schema(description = "${column.columnComment}")
</#if>
    @ExcelProperty(value = "${(column.columnComment?has_content)?then(column.columnComment, column.columnName)}", index = ${column_index})
<#if column.dataType == 'LocalDateTime'>
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ColumnWidth(24)
    private ${column.dataType} ${column.columnName};
<#else>
    private ${column.dataType} ${column.columnName};
</#if>
</#list>
}
