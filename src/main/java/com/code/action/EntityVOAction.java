package com.code.action;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Entity VO（视图对象）代码生成 Action
 *
 * @author syrobin
 */
public class EntityVOAction extends BaseAnAction {

    @Override
    protected void configureTargets(@NotNull Map<String, Object> params) {
        params.put("entityVO", "y");
    }

    @NotNull
    @Override
    protected String getTaskTitle() {
        return "Generating Entity VO Code...";
    }

    @NotNull
    @Override
    protected String getSuccessMessage() {
        return "Entity VO 代码生成成功";
    }
}
