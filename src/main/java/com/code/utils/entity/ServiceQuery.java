package com.code.utils.entity;

import java.util.Objects;

/**
 * @ClassName ServiceQuery
 * @Description 自定义查询条件
 * @Author syrobin
 * @Time 2021/8/10 11:14 上午
 */
public class ServiceQuery {
    /**
     * 自定义查询条件类型（0：等于，1：模糊查询，2：为空查询，3：不为空查询，4：in查询，5：notIn查询，6：inSql查询，7：notInSql查询，8：区间查询，9：非该区间查询）
     */
    private String type;
    /**
     * 数据库字段名
     */
    private String column;
    /**
     * 传值实体类字段名
     */
    private String parameter;
    /**
     * 传值实体类字段名-是否字符串类型
     */
    private Boolean parameterType;
    /**
     * sql值
     */
    private String sql;
    /**
     * 区间开始
     */
    private String start;
    /**
     * 区间结束
     */
    private String end;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public String getParameter() {
        return parameter;
    }

    public void setParameter(String parameter) {
        this.parameter = parameter;
    }

    public Boolean getParameterType() {
        return parameterType;
    }

    public void setParameterType(Boolean parameterType) {
        this.parameterType = parameterType;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceQuery)) return false;
        ServiceQuery that = (ServiceQuery) o;
        return Objects.equals(type, that.type)
                && Objects.equals(column, that.column)
                && Objects.equals(parameter, that.parameter)
                && Objects.equals(parameterType, that.parameterType)
                && Objects.equals(sql, that.sql)
                && Objects.equals(start, that.start)
                && Objects.equals(end, that.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, column, parameter, parameterType, sql, start, end);
    }

    @Override
    public String toString() {
        return "ServiceQuery{" +
                "type='" + type + '\'' +
                ", column='" + column + '\'' +
                ", parameter='" + parameter + '\'' +
                ", parameterType=" + parameterType +
                ", sql='" + sql + '\'' +
                ", start='" + start + '\'' +
                ", end='" + end + '\'' +
                '}';
    }
}
