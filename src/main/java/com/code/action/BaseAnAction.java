package com.code.action;

import com.code.ui.CodeGenerateDialog;
import com.code.utils.CodeGenerate;
import com.code.utils.MyNotifier;
import com.google.common.collect.Maps;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

/**
 * 基础 Action 父类（区分配置态静态窗口标题与执行态后台进度标题，非阻塞式打开向导窗口）
 *
 * @author syrobin
 */
public abstract class BaseAnAction extends AnAction {

    /**
     * 获取当前 Project
     */
    protected Project getProject(AnActionEvent e) {
        return e.getProject();
    }

    /**
     * 获取用户当前选中的目录
     */
    protected PsiDirectory getTargetDirectory(AnActionEvent e) {
        IdeView ideView = e.getData(LangDataKeys.IDE_VIEW);
        if (ideView == null) {
            return null;
        }
        return ideView.getOrChooseDirectory();
    }

    /**
     * 子类实现：配置当前 Action 默认预勾选的生成模块开关
     */
    protected abstract void configureTargets(@NotNull Map<String, Object> params);

    /**
     * 子类实现：点击「开始生成」后在后台进度条中显示的执行态标题
     */
    @NotNull
    protected abstract String getTaskTitle();

    /**
     * 配置态静态窗口标题（避免在尚未开始执行时显示 "Generating ..."）
     */
    @NotNull
    protected String getDialogTitle(@NotNull AnActionEvent e) {
        String actionText = e.getPresentation().getText();
        if (StringUtils.isNotBlank(actionText)) {
            return "Syrobin Code Generate — " + actionText;
        }
        return "Syrobin Code Generate — 代码生成配置";
    }

    /**
     * 子类实现：生成成功后的提示文案
     */
    @NotNull
    protected abstract String getSuccessMessage();

    /**
     * 子类可选覆盖：是否默认聚焦枚举过滤
     */
    protected boolean shouldPromptFixedEnums() {
        return false;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = getProject(e);
        if (project == null) {
            return;
        }

        PsiDirectory psiDirectory = getTargetDirectory(e);
        if (psiDirectory == null) {
            MyNotifier.notifyError(project, "未选中目标目录");
            return;
        }

        VirtualFile targetVirtualDir = psiDirectory.getVirtualFile();
        String fullDirPath = targetVirtualDir.getPath();
        String path;
        if (fullDirPath.contains("src/main/")) {
            path = fullDirPath.split("src/main/")[0];
        } else {
            path = fullDirPath.endsWith("/") ? fullDirPath : fullDirPath + "/";
        }

        if (StringUtils.isBlank(path)) {
            MyNotifier.notifyError(project, "目录信息未找到，请检查生成地址");
            return;
        }

        // 1. 初始化默认生成层开关，由具体 Action 预勾选对应模块
        Map<String, Object> params = Maps.newHashMap();
        params.put("controller", "n");
        params.put("entity", "n");
        params.put("entityBO", "n");
        params.put("entityVO", "n");
        params.put("entityQuery", "n");
        params.put("enums", "n");
        params.put("service", "n");
        params.put("serviceResults", "n");
        params.put("serviceQuery", "n");
        params.put("mapper", "n");

        configureTargets(params);

        String staticDialogTitle = getDialogTitle(e);

        // 2. 通过 invokeLater 跳出当前 Action 的 WriteIntentReadAction 栈帧，并以静态配置标题打开向导窗口
        ApplicationManager.getApplication().invokeLater(() -> {
            CodeGenerateDialog dialog = new CodeGenerateDialog(project, path, params, staticDialogTitle);
            dialog.setOnConfirm(() -> startBackgroundGeneration(project, path, targetVirtualDir, params, dialog));
            dialog.show();
        });
    }

    private void startBackgroundGeneration(@NotNull Project project,
                                          @NotNull String path,
                                          @NotNull VirtualFile targetVirtualDir,
                                          @NotNull Map<String, Object> params,
                                          @NotNull CodeGenerateDialog dialog) {
        String author = dialog.getAuthor();
        String moduleName = dialog.getModuleName();
        String tableNames = dialog.getTableNames();

        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, getTaskTitle(), false) {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        try {
                            indicator.setIndeterminate(true);

                            String[] tables = tableNames.split(",");
                            for (String table : tables) {
                                String trimmedTable = table.trim();
                                if (StringUtils.isNotBlank(trimmedTable)) {
                                    CodeGenerate.foundCode(
                                            project,
                                            trimmedTable,
                                            moduleName,
                                            author,
                                            params,
                                            path
                                    );
                                }
                            }

                            // 自动刷新工程虚拟文件系统
                            VirtualFile rootRefreshDir = VfsUtil.findFileByIoFile(new java.io.File(path), true);
                            if (rootRefreshDir != null) {
                                VfsUtil.markDirtyAndRefresh(true, true, true, rootRefreshDir);
                            } else {
                                VfsUtil.markDirtyAndRefresh(true, true, true, targetVirtualDir);
                            }

                            MyNotifier.notifyInformation(project, getSuccessMessage());

                        } catch (FileNotFoundException ex) {
                            MyNotifier.notifyError(project, "未找到对应配置文件或模板：" + ex.getMessage());
                        } catch (IOException ex) {
                            MyNotifier.notifyError(project, "文件读写失败：" + ex.getMessage());
                        } catch (Exception ex) {
                            MyNotifier.notifyError(project, "代码生成异常：" + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }
                }
        );
    }
}
