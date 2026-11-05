package com.code.utils.entity;

import java.util.Objects;

/**
 * @ClassName EnumsEntityName
 * @Description 枚举实体类
 * @Author syrobin
 * @Time 2021/8/13 2:27 下午
 */
public class EnumsEntityName {
    /**
     * 小写名称
     */
    private String smallName;
    /**
     * 大写名称
     */
    private String bigName;

    public String getSmallName() {
        return smallName;
    }

    public void setSmallName(String smallName) {
        this.smallName = smallName;
    }

    public String getBigName() {
        return bigName;
    }

    public void setBigName(String bigName) {
        this.bigName = bigName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EnumsEntityName)) return false;
        EnumsEntityName that = (EnumsEntityName) o;
        return Objects.equals(smallName, that.smallName) && Objects.equals(bigName, that.bigName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(smallName, bigName);
    }

    @Override
    public String toString() {
        return "EnumsEntityName{" +
                "smallName='" + smallName + '\'' +
                ", bigName='" + bigName + '\'' +
                '}';
    }
}
