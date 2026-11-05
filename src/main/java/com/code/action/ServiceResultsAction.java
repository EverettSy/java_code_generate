package com.code.action;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * ServiceResults 代码生成 Action
 *
 * @author syrobin
 */
public class ServiceResultsAction extends BaseAnAction {

    @Override
    protected void configureTargets(@NotNull Map<String, Object> params) {
        params.put("serviceResults", "y");
    }

    @NotNull
    @Override
    protected String getTaskTitle() {
        return "Generating ServiceResults Code...";
    }

    @NotNull
    @Override
    protected String getSuccessMessage() {
        return "ServiceResults 代码生成成功";
    }
}
