package com.code.action;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Spring Cloud OpenFeign Client 接口生成 Action
 *
 * @author syrobin
 */
public class FeignClientAction extends BaseAnAction {

    @Override
    protected void configureTargets(@NotNull Map<String, Object> params) {
        params.put("feignClient", "y");
    }

    @NotNull
    @Override
    protected String getTaskTitle() {
        return "Generating Feign Client Code...";
    }

    @NotNull
    @Override
    protected String getSuccessMessage() {
        return "FeignClient 代码生成成功";
    }
}
