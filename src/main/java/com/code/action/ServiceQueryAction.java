package com.code.action;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * ServiceQuery 代码生成 Action
 *
 * @author syrobin
 */
public class ServiceQueryAction extends BaseAnAction {

    @Override
    protected void configureTargets(@NotNull Map<String, Object> params) {
        params.put("serviceQuery", "y");
    }

    @NotNull
    @Override
    protected String getTaskTitle() {
        return "Generating ServiceQuery Code...";
    }

    @NotNull
    @Override
    protected String getSuccessMessage() {
        return "ServiceQuery 代码生成成功";
    }
}
