package com.code.action;

import java.util.Map;

/**
 * 单独生成 Flyway / Liquibase 数据库版本迁移脚本 Action（v4.0 M3）
 *
 * @author syrobin
 */
public class FlywayMigrationAction extends BaseAnAction {

    @Override
    protected void configureTargets(Map<String, Object> parameterMap) {
        parameterMap.put("flywayMigration", "y");
    }

    @Override
    protected String getTaskTitle() {
        return "Generating Flyway SQL Migration Script...";
    }

    @Override
    protected String getSuccessMessage() {
        return "Flyway 数据库版本迁移脚本生成完成！";
    }
}
