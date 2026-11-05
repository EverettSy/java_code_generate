package com.code.action;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Controller 层代码生成 Action
 *
 * @author syrobin
 */
public class ControllerAction extends BaseAnAction {

    @Override
    protected void configureTargets(@NotNull Map<String, Object> params) {
        params.put("controller", "y");
    }

    @NotNull
    @Override
    protected String getTaskTitle() {
        return "Generating Controller Code...";
    }

    @NotNull
    @Override
    protected String getSuccessMessage() {
        return "Controller 代码生成成功";
    }
}
