package com.code.action;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Mapper 代码生成 Action
 *
 * @author syrobin
 */
public class MapperAction extends BaseAnAction {

    @Override
    protected void configureTargets(@NotNull Map<String, Object> params) {
        params.put("mapper", "y");
    }

    @NotNull
    @Override
    protected String getTaskTitle() {
        return "Generating Mapper Code...";
    }

    @NotNull
    @Override
    protected String getSuccessMessage() {
        return "Mapper 代码生成成功";
    }
}
