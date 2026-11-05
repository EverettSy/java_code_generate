package com.code.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 离线 DDL SQL 文件或粘贴脚本代码生成 Action（P3 阶段新增）
 *
 * 支持：
 * 1. 在 Project 视图中右键任意 .sql 文件，自动读取文件中的 CREATE TABLE 语句并打开离线 DDL 生成向导
 * 2. 在任意目录右键选择该菜单，直接进入离线 DDL 解析模式生成全套后端代码
 *
 * @author syrobin
 */
public class DdlFileGenerateAction extends BaseAnAction {

    private String preloadedSqlContent;

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        preloadedSqlContent = null;
        VirtualFile selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (selectedFile != null && !selectedFile.isDirectory() && "sql".equalsIgnoreCase(selectedFile.getExtension())) {
            try {
                preloadedSqlContent = new String(selectedFile.contentsToByteArray(), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        }
        super.actionPerformed(e);
    }

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

        if (preloadedSqlContent != null && !preloadedSqlContent.isBlank()) {
            params.put("preloadedDdlScript", preloadedSqlContent);
        }
    }

    @NotNull
    @Override
    protected String getTaskTitle() {
        return "Generating Code from Offline DDL SQL...";
    }

    @NotNull
    @Override
    protected String getSuccessMessage() {
        return "离线 DDL SQL 代码生成完成";
    }
}
