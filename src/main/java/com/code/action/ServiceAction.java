package com.code.action;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Service 代码生成 Action（生成 Service 接口与 ServiceImpl 实现类）
 *
 * @author syrobin
 */
public class ServiceAction extends BaseAnAction {

    @Override
    protected void configureTargets(@NotNull Map<String, Object> params) {
        params.put("service", "y");
    }

    @NotNull
    @Override
    protected String getTaskTitle() {
        return "Generating Service Code...";
    }

    @NotNull
    @Override
    protected String getSuccessMessage() {
        return "Service 代码生成成功";
    }
}
