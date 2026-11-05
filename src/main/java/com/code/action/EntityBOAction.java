package com.code.action;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Entity BO（业务对象）代码生成 Action
 *
 * @author syrobin
 */
public class EntityBOAction extends BaseAnAction {

    @Override
    protected void configureTargets(@NotNull Map<String, Object> params) {
        params.put("entityBO", "y");
    }

    @NotNull
    @Override
    protected String getTaskTitle() {
        return "Generating Entity BO Code...";
    }

    @NotNull
    @Override
    protected String getSuccessMessage() {
        return "Entity BO 代码生成成功";
    }
}
