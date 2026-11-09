package com.code.utils.entity;

import com.code.utils.CodeGenerateUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * 主子表（One-to-Many）关联关系定义模型（v3.0 M3）
 *
 * 示例：主表 `user_order`（主键 `id`）关联子表 `user_order_item`（外键列 `order_id`）
 *
 * @author syrobin
 */
public class SubTableRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 子表数据库表名，例如 order_item */
    private String subTableName;

    /** 子表外键列名，例如 order_id */
    private String foreignKeyColumn;

    /** 主表关联主键属性名，默认 id */
    private String masterKeyProperty = "id";

    /** 关联说明，例如 订单明细列表 */
    private String relationComment = "";

    /** 子表实体大驼峰类名，例如 OrderItem */
    private String subClassName;

    /** 子表实体小驼峰变量名，例如 orderItem */
    private String subClassVarName;

    /** 子表外键大驼峰属性名，例如 OrderId */
    private String foreignKeyPropertyUpper;

    /** 主表关联键大驼峰属性名，例如 Id */
    private String masterKeyPropertyUpper = "Id";

    public SubTableRelation() {
    }

    public SubTableRelation(String subTableName, String foreignKeyColumn, String masterKeyProperty, String relationComment) {
        this.subTableName = StringUtils.trimToEmpty(subTableName);
        this.foreignKeyColumn = StringUtils.trimToEmpty(foreignKeyColumn);
        this.masterKeyProperty = StringUtils.defaultIfBlank(masterKeyProperty, "id").trim();
        this.relationComment = StringUtils.trimToEmpty(relationComment);
        recomputeDerivedNames("");
    }

    public void recomputeDerivedNames(String tablePrefixes) {
        if (StringUtils.isNotBlank(subTableName)) {
            this.subClassName = CodeGenerateUtils.toUpper(subTableName, tablePrefixes);
            this.subClassVarName = StringUtils.uncapitalize(this.subClassName);
        }
        if (StringUtils.isNotBlank(foreignKeyColumn)) {
            String fkCamel = StringUtils.uncapitalize(CodeGenerateUtils.toUpper(foreignKeyColumn.toLowerCase(Locale.ROOT)));
            this.foreignKeyPropertyUpper = StringUtils.capitalize(fkCamel);
        }
        if (StringUtils.isNotBlank(masterKeyProperty)) {
            String mkCamel = StringUtils.uncapitalize(CodeGenerateUtils.toUpper(masterKeyProperty));
            this.masterKeyProperty = mkCamel;
            this.masterKeyPropertyUpper = StringUtils.capitalize(mkCamel);
        } else {
            this.masterKeyProperty = "id";
            this.masterKeyPropertyUpper = "Id";
        }
    }

    public String getSubTableName() {
        return subTableName;
    }

    public void setSubTableName(String subTableName) {
        this.subTableName = subTableName;
        recomputeDerivedNames("");
    }

    public String getForeignKeyColumn() {
        return foreignKeyColumn;
    }

    public void setForeignKeyColumn(String foreignKeyColumn) {
        this.foreignKeyColumn = foreignKeyColumn;
        recomputeDerivedNames("");
    }

    public String getMasterKeyProperty() {
        return masterKeyProperty;
    }

    public void setMasterKeyProperty(String masterKeyProperty) {
        this.masterKeyProperty = masterKeyProperty;
        recomputeDerivedNames("");
    }

    public String getRelationComment() {
        return relationComment;
    }

    public void setRelationComment(String relationComment) {
        this.relationComment = relationComment;
    }

    public String getSubClassName() {
        if (StringUtils.isBlank(subClassName) && StringUtils.isNotBlank(subTableName)) {
            recomputeDerivedNames("");
        }
        return subClassName;
    }

    public void setSubClassName(String subClassName) {
        this.subClassName = subClassName;
    }

    public String getSubClassVarName() {
        if (StringUtils.isBlank(subClassVarName) && StringUtils.isNotBlank(subTableName)) {
            recomputeDerivedNames("");
        }
        return subClassVarName;
    }

    public void setSubClassVarName(String subClassVarName) {
        this.subClassVarName = subClassVarName;
    }

    public String getForeignKeyPropertyUpper() {
        if (StringUtils.isBlank(foreignKeyPropertyUpper) && StringUtils.isNotBlank(foreignKeyColumn)) {
            recomputeDerivedNames("");
        }
        return foreignKeyPropertyUpper;
    }

    public void setForeignKeyPropertyUpper(String foreignKeyPropertyUpper) {
        this.foreignKeyPropertyUpper = foreignKeyPropertyUpper;
    }

    public String getMasterKeyPropertyUpper() {
        if (StringUtils.isBlank(masterKeyPropertyUpper)) {
            recomputeDerivedNames("");
        }
        return masterKeyPropertyUpper;
    }

    public void setMasterKeyPropertyUpper(String masterKeyPropertyUpper) {
        this.masterKeyPropertyUpper = masterKeyPropertyUpper;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SubTableRelation that)) return false;
        return Objects.equals(subTableName, that.subTableName)
                && Objects.equals(foreignKeyColumn, that.foreignKeyColumn)
                && Objects.equals(masterKeyProperty, that.masterKeyProperty)
                && Objects.equals(relationComment, that.relationComment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subTableName, foreignKeyColumn, masterKeyProperty, relationComment);
    }
}
