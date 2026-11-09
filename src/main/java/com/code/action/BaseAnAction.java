package com.code.action;

import com.code.ui.CodeGenerateDialog;
import com.code.utils.CodeGenerate;
import com.code.utils.LocalHistoryHelper;
import com.code.utils.MyNotifier;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
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
import java.util.HashMap;
import java.util.Map;

/**
 * 基础 Action 父类（v3.0 M3：集成 LocalHistory 批次快照标记与非模态向导）
 *
 * @author syrobin
 */
public abstract class BaseAnAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    protected Project getProject(AnActionEvent e) {
        return e.getProject();
    }

    protected PsiDirectory getTargetDirectory(AnActionEvent e) {
        IdeView ideView = e.getData(LangDataKeys.IDE_VIEW);
        if (ideView == null) {
            return null;
        }
        return ideView.getOrChooseDirectory();
    }

    protected abstract void configureTargets(@NotNull Map<String, Object> params);

    @NotNull
    protected abstract String getTaskTitle();

    @NotNull
    protected String getDialogTitle(@NotNull AnActionEvent e) {
        String actionText = e.getPresentation().getText();
        if (StringUtils.isNotBlank(actionText)) {
            return "Syrobin Code Generate — " + actionText;
        }
        return "Syrobin Code Generate — 代码生成配置";
    }

    @NotNull
    protected abstract String getSuccessMessage();

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

        Map<String, Object> params = new HashMap<>();
        params.put("controller", "n");
        params.put("feignClient", "n");
        params.put("entity", "n");
        params.put("entityBO", "n");
        params.put("entityVO", "n");
        params.put("entityQuery", "n");
        params.put("enums", "n");
        params.put("service", "n");
        params.put("serviceResults", "n");
        params.put("serviceQuery", "n");
        params.put("mapper", "n");
        params.put("mapperXml", "n");
        params.put("converter", "n");
        params.put("frontendApi", "n");

        configureTargets(params);

        String staticDialogTitle = getDialogTitle(e);

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
                        LocalHistoryHelper.startGenerationBatch(project, tableNames);
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

                            LocalHistoryHelper.finishGenerationBatch(project);

                            VirtualFile rootRefreshDir = VfsUtil.findFileByIoFile(new java.io.File(path), true);
                            if (rootRefreshDir != null) {
                                VfsUtil.markDirtyAndRefresh(true, true, true, rootRefreshDir);
                            } else {
                                VfsUtil.markDirtyAndRefresh(true, true, true, targetVirtualDir);
                            }

                            MyNotifier.notifyInformation(project, getSuccessMessage() + "（可在生成向导顶栏一键回滚）");

                        } catch (FileNotFoundException ex) {
                            MyNotifier.notifyError(project, "未找到对应配置文件或模板：" + ex.getMessage());
                        } catch (IOException ex) {
                            MyNotifier.notifyError(project, "文件读写失败：" + ex.getMessage());
                        } catch (Exception ex) {
                            MyNotifier.notifyError(project, "代码生成异常：" + ex.getMessage());
                            ex.printStackTrace();
                        } finally {
                            LocalHistoryHelper.finishGenerationBatch(project);
                        }
                    }
                }
        );
    }
}
