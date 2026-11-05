package com.code.utils.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created with IntelliJ IDEA.
 * User:    syrobin
 * Date:    2016/2/1
 * Time:    14:40
 * details: 数据库字段元数据信息（P3：扩展非空约束、长度、主键标识与智能校验注解列表，兼容 JDK 21~25+）
 * @author syrobin
 */
public class Table {
    /**
     * 实体属性名（小驼峰）
     */
    private String columnName;
    /**
     * Java 数据类型
     */
    private String dataType;
    /**
     * 数据库字段注释
     */
    private String columnComment;
    /**
     * 首字母大写属性名
     */
    private String columnNameUpper;
    /**
     * 数据库原始列名
     */
    private String typeName;
    /**
     * 是否允许为空（默认 true）
     */
    private Boolean nullable = Boolean.TRUE;
    /**
     * 字符最大长度（仅字符串类型有效）
     */
    private Integer maxLength;
    /**
     * 是否为主键列
     */
    private Boolean primaryKey = Boolean.FALSE;
    /**
     * P3 智能推断的 Bean Validation 注解列表（如 @NotBlank、@Size、@Email 等）
     */
    private List<String> validationAnnotations = new ArrayList<>();

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getColumnComment() {
        return columnComment;
    }

    public void setColumnComment(String columnComment) {
        this.columnComment = columnComment;
    }

    public String getColumnNameUpper() {
        return columnNameUpper;
    }

    public void setColumnNameUpper(String columnNameUpper) {
        this.columnNameUpper = columnNameUpper;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public Boolean getNullable() {
        return nullable;
    }

    public void setNullable(Boolean nullable) {
        this.nullable = nullable;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public Boolean getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(Boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public List<String> getValidationAnnotations() {
        return validationAnnotations;
    }

    public void setValidationAnnotations(List<String> validationAnnotations) {
        this.validationAnnotations = validationAnnotations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Table)) return false;
        Table table = (Table) o;
        return Objects.equals(columnName, table.columnName)
                && Objects.equals(dataType, table.dataType)
                && Objects.equals(columnComment, table.columnComment)
                && Objects.equals(columnNameUpper, table.columnNameUpper)
                && Objects.equals(typeName, table.typeName)
                && Objects.equals(nullable, table.nullable)
                && Objects.equals(maxLength, table.maxLength)
                && Objects.equals(primaryKey, table.primaryKey)
                && Objects.equals(validationAnnotations, table.validationAnnotations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(columnName, dataType, columnComment, columnNameUpper, typeName, nullable, maxLength, primaryKey, validationAnnotations);
    }

    @Override
    public String toString() {
        return "Table{" +
                "columnName='" + columnName + '\'' +
                ", dataType='" + dataType + '\'' +
                ", columnComment='" + columnComment + '\'' +
                ", columnNameUpper='" + columnNameUpper + '\'' +
                ", typeName='" + typeName + '\'' +
                ", nullable=" + nullable +
                ", maxLength=" + maxLength +
                ", primaryKey=" + primaryKey +
                ", validationAnnotations=" + validationAnnotations +
                '}';
    }
}
