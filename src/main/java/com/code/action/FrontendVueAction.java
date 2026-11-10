package com.code.action;

import java.util.Map;

/**
 * 单独生成 Vue3 + Element Plus 前端 CRUD 页面 Action（v4.0 M2）
 *
 * @author syrobin
 */
public class FrontendVueAction extends BaseAnAction {

    @Override
    protected void configureTargets(Map<String, Object> parameterMap) {
        parameterMap.put("frontendApi", "y");
        parameterMap.put("frontendVue", "y");
    }

    @Override
    protected String getTaskTitle() {
        return "Generating Vue 3 + Element Plus CRUD Page...";
    }

    @Override
    protected String getSuccessMessage() {
        return "Vue3 + Element Plus 前端页面与 TS API 生成完成！";
    }
}
