package com.code.ui;

import com.code.config.CodeGenSettingsState;
import com.code.config.SklCodeGenConfigurable;
import com.code.i18n.CodeGenBundle;
import com.code.ui.SolidGlassTheme.GlassButton;
import com.code.ui.SolidGlassTheme.GlyphState;
import com.code.ui.SolidGlassTheme.SolidGlassPanel;
import com.code.ui.SolidGlassTheme.StatusGlyph;
import com.code.ui.SolidGlassTheme.Thickness;
import com.code.utils.CodeGenerate;
import com.code.utils.CodeGenerateUtils;
import com.code.utils.LocalHistoryHelper;
import com.code.utils.MyNotifier;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 常驻侧边栏工具窗口（v3.0 建设矩阵域 5.1：非侵入式 Syrobin CodeGen ToolWindow）
 *
 * 核心能力：
 * 1. 常驻 IDE 右侧边栏，无需每次在项目树寻找目录右键，一键拉起全栈向导、离线 DDL 模式、接口转 Feign Client 或 Settings 实时模板工作台。
 * 2. 实时展示当前工程的数据源/团队规范状态与 LocalHistory 最近生成批次快照，支持随时一键确认回滚。
 *
 * @author syrobin
 */
public class SyrobinToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JComponent panel = createToolWindowPanel(project);
        ContentManager contentManager = toolWindow.getContentManager();
        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        contentManager.addContent(content);
    }

    @NotNull
    public static JPanel createToolWindowPanel(@NotNull Project project) {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(SolidGlassTheme.canvasBg());
        root.setBorder(JBUI.Borders.empty(8));

        StatusGlyph statusGlyph = new StatusGlyph("就绪", GlyphState.READY);
        JPanel header = SolidGlassTheme.createCompactHeader("Syrobin CodeGen", SolidGlassTheme.versionBadge(), statusGlyph);

        // 1. 快捷操作面板（覆盖全栈向导、离线 DDL、已有接口转 Feign Client、Settings 模板沙箱、团队规范导出与一键回滚）
        SolidGlassPanel actionsCard = new SolidGlassPanel(new GridLayout(6, 1, 0, 6), Thickness.MEDIUM);
        GlassButton openWizardBtn = new GlassButton("打开全栈代码生成向导", true);
        GlassButton openOfflineDdlBtn = new GlassButton("打开离线 DDL 解析生成");
        GlassButton convertFeignBtn = new GlassButton("已有接口 / Controller 转 Feign Client");
        GlassButton openTemplateWorkbenchBtn = new GlassButton("打开实时模板沙箱工作台 (Settings)");
        GlassButton exportContractBtn = new GlassButton("导出团队共享规范 (.syrobin-codegen.json)");
        GlassButton rollbackBtn = new GlassButton("回滚最近一次生成批次");

        DefaultListModel<String> historyListModel = new DefaultListModel<>();
        JBList<String> historyList = new JBList<>(historyListModel);
        historyList.setBackground(SolidGlassTheme.inputBg());
        historyList.setForeground(SolidGlassTheme.textPrimary());
        historyList.getEmptyText().setText("暂无生成批次记录（执行代码生成后在此显示快照）");

        Runnable refreshStatus = () -> {
            historyListModel.clear();
            LocalHistoryHelper.BatchSnapshot snap = LocalHistoryHelper.peekLastBatch(project);
            boolean canRollback = LocalHistoryHelper.hasRollbackableBatch(project);
            rollbackBtn.setEnabled(canRollback);
            if (snap != null) {
                String timeStr = new SimpleDateFormat("HH:mm:ss").format(new Date(snap.getTimestamp()));
                historyListModel.addElement("[" + timeStr + "] 批次: " + snap.getSummary()
                        + " · 涉及 " + snap.getFileSnapshots().size() + " 个文件（可一键回滚）");
                statusGlyph.setStatus("可回滚 (" + snap.getFileSnapshots().size() + " 个文件)", GlyphState.READY);
            } else {
                statusGlyph.setStatus("工作台就绪", GlyphState.IDLE);
            }
        };

        openWizardBtn.addActionListener(e -> launchWizardFromToolWindow(project, false, false, refreshStatus));
        openOfflineDdlBtn.addActionListener(e -> launchWizardFromToolWindow(project, true, false, refreshStatus));
        convertFeignBtn.addActionListener(e -> launchWizardFromToolWindow(project, false, true, refreshStatus));
        openTemplateWorkbenchBtn.addActionListener(e ->
                ShowSettingsUtil.getInstance().showSettingsDialog(project, SklCodeGenConfigurable.class)
        );
        exportContractBtn.addActionListener(e -> {
            String basePath = project.getBasePath();
            if (StringUtils.isBlank(basePath)) {
                return;
            }
            try {
                File exported = CodeGenSettingsState.getInstance(project).exportTeamContract(basePath);
                statusGlyph.setStatus("已导出团队契约", GlyphState.READY);
                Messages.showInfoMessage(project, "已导出团队共享规范至：\n" + exported.getAbsolutePath(), "导出成功");
            } catch (Exception ex) {
                Messages.showErrorDialog(project, "导出失败：" + ex.getMessage(), "错误");
            }
        });
        rollbackBtn.addActionListener(e -> {
            LocalHistoryHelper.BatchSnapshot snap = LocalHistoryHelper.peekLastBatch(project);
            if (snap == null) {
                Messages.showInfoMessage(project, CodeGenBundle.message("rollback.empty.message"), "回滚提示");
                refreshStatus.run();
                return;
            }
            int confirm = Messages.showYesNoDialog(
                    project,
                    "确定要回滚最近一次生成批次 [" + snap.getSummary() + "] 涉及的 " + snap.getFileSnapshots().size() + " 个文件吗？\n" +
                            "（已有文件将恢复生成前内容，新增文件将被清理）",
                    "确认回滚上次生成",
                    "确认回滚",
                    "取消",
                    Messages.getWarningIcon()
            );
            if (confirm != Messages.YES) {
                return;
            }
            try {
                int count = LocalHistoryHelper.rollbackLastBatch(project);
                refreshStatus.run();
                Messages.showInfoMessage(
                        project,
                        CodeGenBundle.message("rollback.success.message", count, snap.getSummary()),
                        "回滚成功"
                );
            } catch (Exception ex) {
                Messages.showErrorDialog(project, "回滚失败：" + ex.getMessage(), "错误");
            }
        });

        actionsCard.add(openWizardBtn);
        actionsCard.add(openOfflineDdlBtn);
        actionsCard.add(convertFeignBtn);
        actionsCard.add(openTemplateWorkbenchBtn);
        actionsCard.add(exportContractBtn);
        actionsCard.add(rollbackBtn);

        // 2. LocalHistory 批次快照与项目规范概览卡片
        SolidGlassPanel historyCard = new SolidGlassPanel(new BorderLayout(0, 6), Thickness.MEDIUM);
        JPanel historyHeader = new JPanel(new BorderLayout());
        historyHeader.setOpaque(false);
        JLabel historyTitle = new JLabel("最近生成快照与 LocalHistory 记录");
        historyTitle.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(12f)));
        historyTitle.setForeground(SolidGlassTheme.textPrimary());
        GlassButton refreshBtn = new GlassButton("刷新");
        refreshBtn.addActionListener(e -> refreshStatus.run());
        historyHeader.add(historyTitle, BorderLayout.WEST);
        historyHeader.add(refreshBtn, BorderLayout.EAST);

        JBScrollPane scrollPane = new JBScrollPane(historyList);
        SolidGlassTheme.styleScrollPane(scrollPane);

        historyCard.add(historyHeader, BorderLayout.NORTH);
        historyCard.add(scrollPane, BorderLayout.CENTER);
        historyCard.add(SolidGlassTheme.createHelperLabel("提示：也可在 Project 视图、编辑器或 Database 视图中右键触发生成与 Feign 转换"), BorderLayout.SOUTH);

        JPanel centerBody = new JPanel(new BorderLayout(0, 8));
        centerBody.setOpaque(false);
        centerBody.add(actionsCard, BorderLayout.NORTH);
        centerBody.add(historyCard, BorderLayout.CENTER);

        root.add(header, BorderLayout.NORTH);
        root.add(centerBody, BorderLayout.CENTER);

        refreshStatus.run();
        return root;
    }

    private static void launchWizardFromToolWindow(@NotNull Project project,
                                                   boolean offlineDdlMode,
                                                   boolean openFeignConverterImmediately,
                                                   @NotNull Runnable onAfterGenerate) {
        String basePath = StringUtils.defaultIfBlank(project.getBasePath(), ".");
        String normalizedPath = CodeGenerateUtils.normalizeDirPath(basePath);

        Map<String, Object> params = new HashMap<>();
        params.put("controller", "y");
        params.put("feignClient", openFeignConverterImmediately ? "y" : "n");
        params.put("entity", "y");
        params.put("entityBO", "y");
        params.put("entityVO", "y");
        params.put("entityQuery", "y");
        params.put("enums", "y");
        params.put("service", "y");
        params.put("serviceResults", "y");
        params.put("serviceQuery", "y");
        params.put("mapper", "y");
        params.put("mapperXml", "n");
        params.put("converter", "n");
        params.put("frontendApi", "n");

        String dialogTitle = offlineDdlMode
                ? "Syrobin Code Generate — 离线 DDL 模式"
                : (openFeignConverterImmediately
                        ? "Syrobin Code Generate — Feign Client 生成与接口转换"
                        : "Syrobin Code Generate — 全栈代码生成向导");

        CodeGenerateDialog dialog = new CodeGenerateDialog(
                project,
                normalizedPath,
                params,
                dialogTitle
        );
        if (offlineDdlMode) {
            dialog.switchToOfflineDdlTab();
        }
        dialog.setOnConfirm(() -> {
            String author = dialog.getAuthor();
            String moduleName = dialog.getModuleName();
            String tableNames = dialog.getTableNames();
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating Full-Stack Code...", false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    LocalHistoryHelper.startGenerationBatch(project, tableNames);
                    try {
                        for (String raw : tableNames.split(",")) {
                            String t = raw.trim();
                            if (StringUtils.isNotBlank(t)) {
                                CodeGenerate.foundCode(project, t, moduleName, author, params, normalizedPath);
                            }
                        }
                        LocalHistoryHelper.finishGenerationBatch(project);
                        VirtualFile rootVf = VfsUtil.findFileByIoFile(new File(normalizedPath), true);
                        if (rootVf != null) {
                            VfsUtil.markDirtyAndRefresh(true, true, true, rootVf);
                        }
                        MyNotifier.notifyInformation(project, "代码生成完成！");
                    } catch (Exception ex) {
                        MyNotifier.notifyError(project, "代码生成异常：" + ex.getMessage());
                    } finally {
                        LocalHistoryHelper.finishGenerationBatch(project);
                        SwingUtilities.invokeLater(onAfterGenerate);
                    }
                }
            });
        });
        dialog.show();
        if (openFeignConverterImmediately) {
            SwingUtilities.invokeLater(() -> {
                dialog.openConvertExistingInterfaceToFeignDialog();
                onAfterGenerate.run();
            });
        }
    }


}
