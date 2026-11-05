package com.code.utils.entity;

import java.util.Objects;

/**
 * @ClassName BeanQuery
 * @Description 查询类
 * @Author syrobin
 * @Time 2021/8/10 3:26 下午
 */
public class BeanQuery {
    /**
     * 数据类型（0：String，1：Integer，2：Double，3：BigDecimal，4：LocalDateTime，5：Date，6：Long，7：byte[]）
     */
    private String type;
    /**
     * 名称
     */
    private String name;
    /**
     * 类型转换
     */
    private String format;
    /**
     * 描述
     */
    private String description;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BeanQuery)) return false;
        BeanQuery beanQuery = (BeanQuery) o;
        return Objects.equals(type, beanQuery.type)
                && Objects.equals(name, beanQuery.name)
                && Objects.equals(format, beanQuery.format)
                && Objects.equals(description, beanQuery.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name, format, description);
    }

    @Override
    public String toString() {
        return "BeanQuery{" +
                "type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", format='" + format + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
