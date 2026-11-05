package com.code.action;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Entity Query（查询参数对象）代码生成 Action
 *
 * @author syrobin
 */
public class EntityQueryAction extends BaseAnAction {

    @Override
    protected void configureTargets(@NotNull Map<String, Object> params) {
        params.put("entityQuery", "y");
    }

    @NotNull
    @Override
    protected String getTaskTitle() {
        return "Generating Entity Query Code...";
    }

    @NotNull
    @Override
    protected String getSuccessMessage() {
        return "Entity Query 代码生成成功";
    }
}
