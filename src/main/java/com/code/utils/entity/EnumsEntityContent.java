package com.code.utils.entity;

import java.util.Objects;

/**
 * @ClassName EnumsEntityContent
 * @Description 枚举实体类
 * @Author syrobin
 * @Time 2021/8/13 2:27 下午
 */
public class EnumsEntityContent {
    /**
     * 状态
     */
    private String state;
    /**
     * 备注
     */
    private String note;

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EnumsEntityContent)) return false;
        EnumsEntityContent that = (EnumsEntityContent) o;
        return Objects.equals(state, that.state) && Objects.equals(note, that.note);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, note);
    }

    @Override
    public String toString() {
        return "EnumsEntityContent{" +
                "state='" + state + '\'' +
                ", note='" + note + '\'' +
                '}';
    }
}
