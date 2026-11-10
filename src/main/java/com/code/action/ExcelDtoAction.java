package com.code.action;

import java.util.Map;

/**
 * 单独生成 EasyExcel 导入导出模型类 Action（v4.0 M2）
 *
 * @author syrobin
 */
public class ExcelDtoAction extends BaseAnAction {

    @Override
    protected void configureTargets(Map<String, Object> parameterMap) {
        parameterMap.put("excelDto", "y");
    }

    @Override
    protected String getTaskTitle() {
        return "Generating EasyExcel Import/Export DTO...";
    }

    @Override
    protected String getSuccessMessage() {
        return "EasyExcel 导入导出模型生成完成！";
    }
}
