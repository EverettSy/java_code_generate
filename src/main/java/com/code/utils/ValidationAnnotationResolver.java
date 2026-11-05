package com.code.utils;

import com.code.utils.entity.Table;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 字段校验注解智能推断器（P3 阶段：根据数据库列约束与字段名语义自动生成 Bean Validation 注解）
 *
 * 支持：
 * 1. 自动适配 Spring Boot 2 (javax.validation.constraints.*) 与 Spring Boot 3 (jakarta.validation.constraints.*)
 * 2. 基于非空约束 (NOT NULL) 自动推断 @NotBlank / @NotNull
 * 3. 基于字符串长度 (VARCHAR(N)) 自动推断 @Size(max = N)
 * 4. 基于字段语义（email、mobile/phone、amount/price/fee）自动推断 @Email、@Pattern、@PositiveOrZero
 */
public class ValidationAnnotationResolver {

    /**
     * 为单表所有列填充智能推断的校验注解列表
     */
    public static void enrichValidationAnnotations(List<Table> columns, boolean enableValidation) {
        if (columns == null) {
            return;
        }
        for (Table col : columns) {
            List<String> annotations = new ArrayList<>();
            if (enableValidation) {
                String label = extractCleanLabel(col.getColumnComment(), col.getColumnName());
                String lowerName = StringUtils.defaultString(col.getColumnName()).toLowerCase(Locale.ROOT);
                String javaType = StringUtils.defaultString(col.getDataType(), "String");
                boolean isPrimaryOrAudit = Boolean.TRUE.equals(col.getPrimaryKey())
                        || lowerName.equals("id")
                        || lowerName.contains("create")
                        || lowerName.contains("modify")
                        || lowerName.contains("update")
                        || lowerName.equals("isdelete")
                        || lowerName.equals("deleted")
                        || lowerName.equals("version");

                // 1. 非空约束推断（排除主键与审计字段）
                if (Boolean.FALSE.equals(col.getNullable()) && !isPrimaryOrAudit) {
                    if ("String".equals(javaType)) {
                        annotations.add("@NotBlank(message = \"" + label + "不能为空\")");
                    } else {
                        annotations.add("@NotNull(message = \"" + label + "不能为空\")");
                    }
                }

                // 2. 字符串最大长度约束推断
                if ("String".equals(javaType) && col.getMaxLength() != null && col.getMaxLength() > 0 && col.getMaxLength() <= 4000) {
                    annotations.add("@Size(max = " + col.getMaxLength() + ", message = \"" + label + "长度不能超过" + col.getMaxLength() + "个字符\")");
                }

                // 3. 字段语义推断（邮箱、手机号、金额/价格）
                if ("String".equals(javaType)) {
                    if (lowerName.contains("email") || lowerName.endsWith("mail")) {
                        annotations.add("@Email(message = \"" + label + "格式不正确\")");
                    } else if (lowerName.contains("mobile") || lowerName.contains("phone") || lowerName.equals("tel")) {
                        annotations.add("@Pattern(regexp = \"^1[3-9]\\\\d{9}$\", message = \"" + label + "格式不正确\")");
                    }
                } else if ("BigDecimal".equals(javaType) || "Double".equals(javaType) || "Float".equals(javaType)) {
                    if (lowerName.contains("amount") || lowerName.contains("price")
                            || lowerName.contains("money") || lowerName.contains("fee")
                            || lowerName.contains("cost")) {
                        annotations.add("@PositiveOrZero(message = \"" + label + "不能为负数\")");
                    }
                }
            }
            col.setValidationAnnotations(annotations);
        }
    }

    /**
     * 从字段注释中提取简短中文名称（去除括号内的枚举说明与换行）
     */
    private static String extractCleanLabel(String rawComment, String fallbackName) {
        if (StringUtils.isBlank(rawComment)) {
            return fallbackName;
        }
        String cleaned = rawComment.replaceAll("[\\r\\n\"\\\\]", " ").trim();
        int idx1 = cleaned.indexOf('（');
        int idx2 = cleaned.indexOf('(');
        int cutIdx = -1;
        if (idx1 > 0 && idx2 > 0) {
            cutIdx = Math.min(idx1, idx2);
        } else if (idx1 > 0) {
            cutIdx = idx1;
        } else if (idx2 > 0) {
            cutIdx = idx2;
        }
        if (cutIdx > 0) {
            cleaned = cleaned.substring(0, cutIdx).trim();
        }
        return StringUtils.defaultIfBlank(cleaned, fallbackName);
    }
}
