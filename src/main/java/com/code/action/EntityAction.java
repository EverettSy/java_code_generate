package com.code.action;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Entity 实体类代码生成 Action
 *
 * @author syrobin
 */
public class EntityAction extends BaseAnAction {

    @Override
    protected void configureTargets(@NotNull Map<String, Object> params) {
        params.put("entity", "y");
    }

    @NotNull
    @Override
    protected String getTaskTitle() {
        return "Generating Entity Code...";
    }

    @NotNull
    @Override
    protected String getSuccessMessage() {
        return "Entity 代码生成成功";
    }
}
