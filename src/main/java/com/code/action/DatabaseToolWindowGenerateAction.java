package com.code.action;

import com.code.config.CodeGenSettingsState;
import com.code.ui.CodeGenerateDialog;
import com.code.utils.CodeGenerate;
import com.code.utils.LocalHistoryHelper;
import com.code.utils.MyNotifier;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

/**
 * IDEA Database ToolWindow 右键直连生成 Action（v3.0 M2 + M3：集成 LocalHistory 批次快照回滚）
 *
 * @author syrobin
 */
public class DatabaseToolWindowGenerateAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null || StringUtils.isBlank(project.getBasePath())) {
            return;
        }

        String basePath = project.getBasePath();
        String normalizedPath = basePath.endsWith("/") ? basePath : basePath + "/";

        List<String> selectedTableNames = extractSelectedDatabaseTableNames(e);
        String detectedSchema = extractSelectedDatabaseSchema(e);

        CodeGenSettingsState settings = CodeGenSettingsState.getInstance(project);
        CodeGenSettingsState.ConfigState st = settings.getState();
        if (st != null) {
            if (!selectedTableNames.isEmpty()) {
                st.lastTableNames = String.join(",", selectedTableNames);
            }
            if (StringUtils.isNotBlank(detectedSchema)) {
                st.database = detectedSchema;
            }
        }

        Map<String, Object> params = new HashMap<>();
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

        ApplicationManager.getApplication().invokeLater(() -> {
            CodeGenerateDialog dialog = new CodeGenerateDialog(
                    project,
                    normalizedPath,
                    params,
                    "Syrobin Code Generate — Database 视图直连生成"
            );
            dialog.setOnConfirm(() -> runDatabaseGenerationTask(project, normalizedPath, params, dialog));
            dialog.show();
        });
    }

    private void runDatabaseGenerationTask(@NotNull Project project,
                                           @NotNull String path,
                                           @NotNull Map<String, Object> params,
                                           @NotNull CodeGenerateDialog dialog) {
        String author = dialog.getAuthor();
        String moduleName = dialog.getModuleName();
        String tableNames = dialog.getTableNames();

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating Code from Database Selection...", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                LocalHistoryHelper.startGenerationBatch(project, tableNames);
                try {
                    indicator.setIndeterminate(true);
                    for (String raw : tableNames.split(",")) {
                        String t = raw.trim();
                        if (StringUtils.isNotBlank(t)) {
                            CodeGenerate.foundCode(project, t, moduleName, author, params, path);
                        }
                    }
                    LocalHistoryHelper.finishGenerationBatch(project);
                    VirtualFile rootRefreshDir = VfsUtil.findFileByIoFile(new File(path), true);
                    if (rootRefreshDir != null) {
                        VfsUtil.markDirtyAndRefresh(true, true, true, rootRefreshDir);
                    }
                    MyNotifier.notifyInformation(project, "Database 选中表代码生成成功！（可在生成向导顶栏一键回滚）");
                } catch (Exception ex) {
                    MyNotifier.notifyError(project, "代码生成异常：" + ex.getMessage());
                } finally {
                    LocalHistoryHelper.finishGenerationBatch(project);
                }
            }
        });
    }

    @NotNull
    public static List<String> extractSelectedDatabaseTableNames(@NotNull AnActionEvent e) {
        Object[] items = collectEventElements(e);
        return extractTableNamesFromObjects(items);
    }

    @NotNull
    public static List<String> extractTableNamesFromObjects(@NotNull Object[] items) {
        Set<String> tableNames = new LinkedHashSet<>();
        for (Object item : items) {
            if (item == null) {
                continue;
            }
            String className = item.getClass().getName().toLowerCase(Locale.ROOT);
            boolean looksLikeDbTable = className.contains("table") || isDasTableKind(item);
            if (looksLikeDbTable) {
                String name = invokeStringNoArg(item, "getName");
                if (StringUtils.isNotBlank(name)) {
                    tableNames.add(name.trim());
                }
            }
        }
        return new ArrayList<>(tableNames);
    }

    @NotNull
    public static String extractSelectedDatabaseSchema(@NotNull AnActionEvent e) {
        Object[] items = collectEventElements(e);
        for (Object item : items) {
            if (item == null) {
                continue;
            }
            try {
                Method getParent = item.getClass().getMethod("getDasParent");
                Object parent = getParent.invoke(item);
                if (parent != null) {
                    String schemaName = invokeStringNoArg(parent, "getName");
                    if (StringUtils.isNotBlank(schemaName)) {
                        return schemaName.trim();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return "";
    }

    private static Object[] collectEventElements(@NotNull AnActionEvent e) {
        List<Object> list = new ArrayList<>();
        try {
            Object[] selectedItems = e.getData(PlatformCoreDataKeys.SELECTED_ITEMS);
            if (selectedItems != null) {
                Collections.addAll(list, selectedItems);
            }
        } catch (Throwable ignored) {
        }
        try {
            Object[] psiElements = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
            if (psiElements != null) {
                Collections.addAll(list, psiElements);
            }
        } catch (Throwable ignored) {
        }
        try {
            Object singlePsi = e.getData(CommonDataKeys.PSI_ELEMENT);
            if (singlePsi != null) {
                list.add(singlePsi);
            }
        } catch (Throwable ignored) {
        }
        return list.toArray();
    }

    private static boolean isDasTableKind(Object obj) {
        try {
            Method getKind = obj.getClass().getMethod("getKind");
            Object kind = getKind.invoke(obj);
            return kind != null && "TABLE".equalsIgnoreCase(String.valueOf(kind));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String invokeStringNoArg(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            Object val = m.invoke(target);
            return val != null ? String.valueOf(val) : "";
        } catch (Throwable ignored) {
            return "";
        }
    }
}
