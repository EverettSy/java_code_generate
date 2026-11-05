package com.code.action;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * QueryAll 代码生成 Action（生成 EntityQuery 与 ServiceQuery）
 *
 * @author syrobin
 */
public class QueryAllAction extends BaseAnAction {

    @Override
    protected void configureTargets(@NotNull Map<String, Object> params) {
        params.put("entityQuery", "y");
        params.put("serviceQuery", "y");
    }

    @NotNull
    @Override
    protected String getTaskTitle() {
        return "Generating Query Code...";
    }

    @NotNull
    @Override
    protected String getSuccessMessage() {
        return "Query 代码生成成功";
    }
}
