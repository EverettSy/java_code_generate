package com.code.action;

import java.util.Map;

/**
 * 单独生成 IDEA HTTP Client (.http) 接口调试文件 Action（v4.0 M1）
 *
 * @author syrobin
 */
public class HttpClientAction extends BaseAnAction {

    @Override
    protected void configureTargets(Map<String, Object> parameterMap) {
        parameterMap.put("httpClient", "y");
    }

    @Override
    protected String getTaskTitle() {
        return "Generating HTTP Client Test File...";
    }

    @Override
    protected String getSuccessMessage() {
        return "HTTP Client (.http) 联调测试文件生成完成！";
    }
}
