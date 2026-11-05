package com.code.action;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * 枚举类代码生成 Action（包含指定字段枚举过滤逻辑 fixedEnums）
 *
 * @author syrobin
 */
public class EnumsAction extends BaseAnAction {

    @Override
    protected void configureTargets(@NotNull Map<String, Object> params) {
        params.put("enums", "y");
    }

    @Override
    protected boolean shouldPromptFixedEnums() {
        return true;
    }

    @NotNull
    @Override
    protected String getTaskTitle() {
        return "Generating Enum Code...";
    }

    @NotNull
    @Override
    protected String getSuccessMessage() {
        return "枚举代码生成成功";
    }
}
