package com.code.action;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * 一键生成全部代码 Action
 *
 * @author syrobin
 */
public class CodeAllAction extends BaseAnAction {

    @Override
    protected void configureTargets(@NotNull Map<String, Object> params) {
        params.put("controller", "y");
        params.put("entity", "y");
        params.put("entityBO", "y");
        params.put("entityVO", "y");
        params.put("entityQuery", "y");
        params.put("enums", "y");
        params.put("service", "y");
        params.put("serviceResults", "y");
        params.put("serviceQuery", "y");
        params.put("mapper", "y");
    }

    @NotNull
    @Override
    protected String getTaskTitle() {
        return "Generating All Code...";
    }

    @NotNull
    @Override
    protected String getSuccessMessage() {
        return "全部代码生成完成";
    }
}
