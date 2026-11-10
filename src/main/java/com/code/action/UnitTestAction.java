package com.code.action;

import java.util.Map;

/**
 * 单独生成 JUnit 5 + Mockito Service 单元测试类 Action（v4.0 M2）
 *
 * @author syrobin
 */
public class UnitTestAction extends BaseAnAction {

    @Override
    protected void configureTargets(Map<String, Object> parameterMap) {
        parameterMap.put("unitTest", "y");
    }

    @Override
    protected String getTaskTitle() {
        return "Generating JUnit 5 Service Unit Tests...";
    }

    @Override
    protected String getSuccessMessage() {
        return "JUnit 5 + Mockito 单元测试生成完成！";
    }
}
