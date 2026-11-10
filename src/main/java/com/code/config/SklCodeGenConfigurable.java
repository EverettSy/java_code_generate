package com.code.config;

import com.code.ui.SolidGlassTheme;
import com.code.ui.SolidGlassTheme.GlassButton;
import com.code.ui.SolidGlassTheme.GlyphState;
import com.code.ui.SolidGlassTheme.SolidGlassPanel;
import com.code.ui.SolidGlassTheme.StatusGlyph;
import com.code.ui.SolidGlassTheme.Thickness;
import com.code.utils.CodeGenerate;
import com.code.utils.CodeGenerateUtils;
import com.code.utils.IncrementalCodeMerger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * IDE Settings 模板工作台与全局策略配置页（v4.0 M1：左右分栏实时 FreeMarker 渲染沙箱 + 统一响应包装配置 + 16 套模板工作台）
 * 挂载路径：Settings / Preferences -> Tools -> Syrobin Code Generate
 *
 * @author syrobin
 */
public class SklCodeGenConfigurable implements Configurable {

    private static final String[] WRITE_STRATEGY_CODES = {
            IncrementalCodeMerger.WriteStrategy.INCREMENTAL_MERGE.name(),
            IncrementalCodeMerger.WriteStrategy.DIFF_PREVIEW.name(),
            IncrementalCodeMerger.WriteStrategy.OVERWRITE.name()
    };

    private static final String[] WRITE_STRATEGY_LABELS = {
            "增量合并 (PSI AST，保留手写字段与方法)",
            "Diff 对比确认 (弹出左右分栏对比确认后写入)",
            "直接覆盖 (直接替换同名文件)"
    };

    private static final String[] BOOT_CODES = {"2", "3"};
    private static final String[] BOOT_LABELS = {
            "Spring Boot 2.x (javax.*)",
            "Spring Boot 3.x (jakarta.*)"
    };

    private static final String[] DOC_CODES = {"NONE", "SWAGGER2", "OPENAPI3"};
    private static final String[] DOC_LABELS = {
            "无文档注解",
            "Swagger 2 (@ApiModel / @ApiModelProperty)",
            "SpringDoc OpenAPI 3 (@Schema / @Tag)"
    };

    private static final String[] MAPPER_STRATEGY_CODES = {"BEAN_UTILS", "MAPSTRUCT", "HUTOOL"};
    private static final String[] MAPPER_STRATEGY_LABELS = {
            "Spring BeanUtils.copyProperties (默认零依赖)",
            "MapStruct 编译期强类型转换 (@Mapper Converter)",
            "Hutool Convert.convert"
    };

    private final Project project;
    private final CodeGenSettingsState settingsService;
    private final StatusGlyph statusGlyph = new StatusGlyph("实时预览就绪", GlyphState.READY);

    private ComboBox<String> writeStrategyCombo;
    private ComboBox<String> bootVersionCombo;
    private ComboBox<String> docStyleCombo;
    private ComboBox<String> mapperStrategyCombo;
    private JBTextField tablePrefixesField;
    private JBTextField baseEntityField;
    private JBTextField feignServiceNameField;
    private JBTextField responseWrapperField;
    private JBCheckBox enableValidationCheckBox;

    private ComboBox<String> templateSelectorCombo;
    private JBTextArea templateEditorArea;
    private JBTextArea livePreviewArea;
    private String currentEditingTemplateName;
    private final Map<String, String> workingTemplates = new HashMap<>();

    public SklCodeGenConfigurable(@NotNull Project project) {
        this.project = project;
        this.settingsService = CodeGenSettingsState.getInstance(project);
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Syrobin Code Generate";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(SolidGlassTheme.canvasBg());
        root.setBorder(JBUI.Borders.empty(8));

        JPanel header = SolidGlassTheme.createCompactHeader(
                "Syrobin Code Generate · 全局策略与实时模板工作台",
                SolidGlassTheme.versionBadge(),
                statusGlyph
        );

        writeStrategyCombo = new ComboBox<>(WRITE_STRATEGY_LABELS);
        bootVersionCombo = new ComboBox<>(BOOT_LABELS);
        docStyleCombo = new ComboBox<>(DOC_LABELS);
        mapperStrategyCombo = new ComboBox<>(MAPPER_STRATEGY_LABELS);
        tablePrefixesField = new JBTextField();
        baseEntityField = new JBTextField();
        feignServiceNameField = new JBTextField();
        responseWrapperField = new JBTextField();
        tablePrefixesField.setBackground(SolidGlassTheme.inputBg());
        tablePrefixesField.setForeground(SolidGlassTheme.textPrimary());
        tablePrefixesField.getEmptyText().setText("如 t_,sys_,biz_");
        baseEntityField.setBackground(SolidGlassTheme.inputBg());
        baseEntityField.setForeground(SolidGlassTheme.textPrimary());
        baseEntityField.getEmptyText().setText("如 com.example.common.entity.BaseEntity");
        feignServiceNameField.setBackground(SolidGlassTheme.inputBg());
        feignServiceNameField.setForeground(SolidGlassTheme.textPrimary());
        feignServiceNameField.getEmptyText().setText("可选：如 order-service（留空默认按 ${moduleName}-service 生成）");
        responseWrapperField.setBackground(SolidGlassTheme.inputBg());
        responseWrapperField.setForeground(SolidGlassTheme.textPrimary());
        responseWrapperField.getEmptyText().setText("可选：如 com.example.common.Result（留空返回裸类型）");

        enableValidationCheckBox = new JBCheckBox("默认开启字段校验注解推断 (@NotBlank / @NotNull / @Size / @Email / @Pattern)", true);
        enableValidationCheckBox.setOpaque(false);
        enableValidationCheckBox.setForeground(SolidGlassTheme.textPrimary());

        writeStrategyCombo.addActionListener(e -> updateLiveSandboxPreview());
        bootVersionCombo.addActionListener(e -> updateLiveSandboxPreview());
        docStyleCombo.addActionListener(e -> updateLiveSandboxPreview());
        mapperStrategyCombo.addActionListener(e -> updateLiveSandboxPreview());
        enableValidationCheckBox.addActionListener(e -> updateLiveSandboxPreview());
        baseEntityField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                updateLiveSandboxPreview();
            }
        });

        SolidGlassPanel strategyCard = new SolidGlassPanel(new GridBagLayout(), Thickness.MEDIUM);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(3, 6, 3, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        addPair(strategyCard, gbc, 0, "默认写入方式:", writeStrategyCombo, "Spring Boot 预设:", bootVersionCombo);
        addPair(strategyCard, gbc, 1, "默认文档注解:", docStyleCombo, "对象转换策略:", mapperStrategyCombo);
        addPair(strategyCard, gbc, 2, "默认剥离前缀:", tablePrefixesField, "默认实体基类:", baseEntityField);
        addPair(strategyCard, gbc, 3, "Feign 服务名:", feignServiceNameField, "统一响应包装:", responseWrapperField);
        addPair(strategyCard, gbc, 4, "字段校验注解:", enableValidationCheckBox, "", new JLabel(""));

        SolidGlassPanel templateCenterCard = new SolidGlassPanel(new BorderLayout(6, 6), Thickness.MEDIUM);

        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.setOpaque(false);
        templateSelectorCombo = new ComboBox<>(CodeGenSettingsState.TEMPLATE_NAMES);
        templateSelectorCombo.addActionListener(e -> {
            saveCurrentEditorToWorkingMap();
            String selected = (String) templateSelectorCombo.getSelectedItem();
            if (selected != null) {
                loadTemplateIntoEditor(selected);
            }
        });

        JPanel btnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnGroup.setOpaque(false);
        GlassButton resetCurrentBtn = new GlassButton("恢复当前模板默认");
        resetCurrentBtn.addActionListener(e -> {
            if (currentEditingTemplateName != null) {
                String builtIn = CodeGenSettingsState.loadBuiltInTemplate(currentEditingTemplateName);
                workingTemplates.remove(currentEditingTemplateName);
                templateEditorArea.setText(builtIn);
                templateEditorArea.setCaretPosition(0);
                updateLiveSandboxPreview();
                statusGlyph.setStatus("已重置：" + currentEditingTemplateName, GlyphState.READY);
            }
        });

        GlassButton exportToProjectBtn = new GlassButton("导出全部 20 套模板到工程", true);
        exportToProjectBtn.addActionListener(e -> exportTemplatesToProject());

        btnGroup.add(resetCurrentBtn);
        btnGroup.add(exportToProjectBtn);

        JPanel selectorLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        selectorLeft.setOpaque(false);
        JBLabel selLabel = new JBLabel("选择 FreeMarker 模板 (共 20 套):");
        selLabel.setForeground(SolidGlassTheme.textPrimary());
        selectorLeft.add(selLabel);
        selectorLeft.add(templateSelectorCombo);

        toolbar.add(selectorLeft, BorderLayout.WEST);
        toolbar.add(btnGroup, BorderLayout.EAST);

        templateEditorArea = new JBTextArea(18, 48);
        templateEditorArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        templateEditorArea.setTabSize(4);
        templateEditorArea.setBackground(SolidGlassTheme.inputBg());
        templateEditorArea.setForeground(SolidGlassTheme.textPrimary());
        templateEditorArea.setCaretColor(SolidGlassTheme.focusBorderColor());
        templateEditorArea.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                updateLiveSandboxPreview();
            }
        });

        livePreviewArea = new JBTextArea(18, 48);
        livePreviewArea.setEditable(false);
        livePreviewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        livePreviewArea.setTabSize(4);
        livePreviewArea.setBackground(SolidGlassTheme.inputBg());
        livePreviewArea.setForeground(SolidGlassTheme.textSecondary());

        JPanel leftEditorBox = new JPanel(new BorderLayout(0, 4));
        leftEditorBox.setOpaque(false);
        JBLabel leftTitle = new JBLabel("FreeMarker 模板源码编辑 (.ftl)");
        leftTitle.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(11.5f)));
        leftTitle.setForeground(SolidGlassTheme.textPrimary());
        JBScrollPane editorScroll = new JBScrollPane(templateEditorArea);
        SolidGlassTheme.styleScrollPane(editorScroll);
        leftEditorBox.add(leftTitle, BorderLayout.NORTH);
        leftEditorBox.add(editorScroll, BorderLayout.CENTER);

        JPanel rightPreviewBox = new JPanel(new BorderLayout(0, 4));
        rightPreviewBox.setOpaque(false);
        JBLabel rightTitle = new JBLabel("实时渲染沙箱预览 (基于样例表 user_order)");
        rightTitle.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(11.5f)));
        rightTitle.setForeground(SolidGlassTheme.selectedFg());
        JBScrollPane previewScroll = new JBScrollPane(livePreviewArea);
        SolidGlassTheme.styleScrollPane(previewScroll);
        rightPreviewBox.add(rightTitle, BorderLayout.NORTH);
        rightPreviewBox.add(previewScroll, BorderLayout.CENTER);

        JSplitPane workbenchSplit = SolidGlassTheme.createCleanSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                leftEditorBox,
                rightPreviewBox,
                440,
                0.5
        );

        JBLabel varsHint = new JBLabel(
                "<html><b>可用模板变量：</b> " +
                        "<code>${packageName}</code>、<code>${ClassName}</code>、<code>${className}</code>、" +
                        "<code>${tableName}</code>、<code>${functionName}</code>、<code>${bootVersion}</code>、" +
                        "<code>${docStyle}</code>、<code>${mapperStrategy}</code>、<code>${responseWrapperSimpleName}</code>、<code>${treeTable}</code>、<code>${list}</code></html>"
        );
        varsHint.setForeground(SolidGlassTheme.textSecondary());

        templateCenterCard.add(toolbar, BorderLayout.NORTH);
        templateCenterCard.add(workbenchSplit, BorderLayout.CENTER);
        templateCenterCard.add(varsHint, BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);
        body.add(strategyCard, BorderLayout.NORTH);
        body.add(templateCenterCard, BorderLayout.CENTER);

        root.add(header, BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER);

        reset();
        return root;
    }

    private void addPair(JPanel panel, GridBagConstraints gbc, int row,
                         String l1Text, JComponent c1, String l2Text, JComponent c2) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0;
        JBLabel l1 = new JBLabel(l1Text);
        l1.setForeground(SolidGlassTheme.textPrimary());
        panel.add(l1, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.48;
        panel.add(c1, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        JBLabel l2 = new JBLabel(l2Text);
        l2.setForeground(SolidGlassTheme.textPrimary());
        l2.setBorder(JBUI.Borders.emptyLeft(8));
        panel.add(l2, gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.52;
        panel.add(c2, gbc);
    }

    private void updateLiveSandboxPreview() {
        if (currentEditingTemplateName == null || templateEditorArea == null || livePreviewArea == null) {
            return;
        }
        String tplContent = templateEditorArea.getText();
        try {
            String rendered = CodeGenerate.renderSamplePreview(
                    currentEditingTemplateName,
                    tplContent,
                    getSelectedCode(bootVersionCombo, BOOT_CODES),
                    getSelectedCode(docStyleCombo, DOC_CODES),
                    getSelectedCode(mapperStrategyCombo, MAPPER_STRATEGY_CODES),
                    enableValidationCheckBox.isSelected(),
                    baseEntityField.getText().trim()
            );
            livePreviewArea.setForeground(SolidGlassTheme.textPrimary());
            livePreviewArea.setText(rendered);
            livePreviewArea.setCaretPosition(0);
            statusGlyph.setStatus("实时渲染成功 · " + currentEditingTemplateName, GlyphState.READY);
        } catch (Exception ex) {
            livePreviewArea.setForeground(SolidGlassTheme.errorFg());
            livePreviewArea.setText("// FreeMarker 模板语法或变量异常：\n" + ex.getMessage());
            livePreviewArea.setCaretPosition(0);
            statusGlyph.setStatus("模板语法错误", GlyphState.ERROR);
        }
    }

    private void saveCurrentEditorToWorkingMap() {
        if (currentEditingTemplateName != null && templateEditorArea != null) {
            String edited = templateEditorArea.getText();
            String builtIn = CodeGenSettingsState.loadBuiltInTemplate(currentEditingTemplateName);
            if (!Objects.equals(edited.trim(), builtIn.trim())) {
                workingTemplates.put(currentEditingTemplateName, edited);
            } else {
                workingTemplates.remove(currentEditingTemplateName);
            }
        }
    }

    private void loadTemplateIntoEditor(String templateName) {
        currentEditingTemplateName = templateName;
        String content = workingTemplates.get(templateName);
        if (StringUtils.isBlank(content)) {
            content = CodeGenSettingsState.loadBuiltInTemplate(templateName);
        }
        templateEditorArea.setText(content);
        templateEditorArea.setCaretPosition(0);
        updateLiveSandboxPreview();
    }

    private void exportTemplatesToProject() {
        saveCurrentEditorToWorkingMap();
        String basePath = project.getBasePath();
        if (StringUtils.isBlank(basePath)) {
            Messages.showWarningDialog(project, "无法获取当前项目根目录路径。", "提示");
            return;
        }
        String targetDir = basePath + "/src/main/resources/templates/";
        try {
            for (String tplName : CodeGenSettingsState.TEMPLATE_NAMES) {
                String content = workingTemplates.getOrDefault(tplName, CodeGenSettingsState.loadBuiltInTemplate(tplName));
                CodeGenerateUtils.writeFile(content, targetDir + tplName);
            }
            VirtualFile vDir = VfsUtil.findFileByIoFile(new File(targetDir), true);
            if (vDir != null) {
                VfsUtil.markDirtyAndRefresh(true, true, true, vDir);
            }
            statusGlyph.setStatus("已导出 " + CodeGenSettingsState.TEMPLATE_NAMES.length + " 套模板", GlyphState.READY);
            Messages.showInfoMessage(project, "已成功将 " + CodeGenSettingsState.TEMPLATE_NAMES.length + " 个 FreeMarker 模板导出至：\n" + targetDir, "导出成功");
        } catch (Exception ex) {
            statusGlyph.setStatus("导出失败", GlyphState.ERROR);
            Messages.showErrorDialog(project, "导出模板失败：" + ex.getMessage(), "错误");
        }
    }

    @Override
    public boolean isModified() {
        saveCurrentEditorToWorkingMap();
        CodeGenSettingsState.ConfigState st = settingsService.getState();
        if (st == null) {
            return false;
        }
        if (!Objects.equals(getSelectedCode(writeStrategyCombo, WRITE_STRATEGY_CODES), st.writeStrategy)) {
            return true;
        }
        if (!Objects.equals(getSelectedCode(bootVersionCombo, BOOT_CODES), st.bootVersion)) {
            return true;
        }
        if (!Objects.equals(getSelectedCode(docStyleCombo, DOC_CODES), st.docStyle)) {
            return true;
        }
        if (!Objects.equals(getSelectedCode(mapperStrategyCombo, MAPPER_STRATEGY_CODES), st.mapperStrategy)) {
            return true;
        }
        if (!Objects.equals(tablePrefixesField.getText().trim(), StringUtils.defaultString(st.tablePrefixes))) {
            return true;
        }
        if (!Objects.equals(baseEntityField.getText().trim(), StringUtils.defaultString(st.baseEntityClass))) {
            return true;
        }
        if (!Objects.equals(feignServiceNameField.getText().trim(), StringUtils.defaultString(st.feignServiceName))) {
            return true;
        }
        if (!Objects.equals(responseWrapperField.getText().trim(), StringUtils.defaultString(st.responseWrapperClass))) {
            return true;
        }
        if (enableValidationCheckBox.isSelected() != st.enableValidation) {
            return true;
        }
        return !Objects.equals(workingTemplates, st.customTemplates);
    }

    @Override
    public void apply() {
        saveCurrentEditorToWorkingMap();
        CodeGenSettingsState.ConfigState st = settingsService.getState();
        if (st != null) {
            st.writeStrategy = getSelectedCode(writeStrategyCombo, WRITE_STRATEGY_CODES);
            st.bootVersion = getSelectedCode(bootVersionCombo, BOOT_CODES);
            st.docStyle = getSelectedCode(docStyleCombo, DOC_CODES);
            st.mapperStrategy = getSelectedCode(mapperStrategyCombo, MAPPER_STRATEGY_CODES);
            st.tablePrefixes = tablePrefixesField.getText().trim();
            st.baseEntityClass = baseEntityField.getText().trim();
            st.feignServiceName = feignServiceNameField.getText().trim();
            st.responseWrapperClass = responseWrapperField.getText().trim();
            st.enableValidation = enableValidationCheckBox.isSelected();
            st.customTemplates.clear();
            st.customTemplates.putAll(workingTemplates);
            statusGlyph.setStatus("全局设置已保存", GlyphState.READY);
        }
    }

    @Override
    public void reset() {
        CodeGenSettingsState.ConfigState st = settingsService.getState();
        if (st != null) {
            selectByCode(writeStrategyCombo, WRITE_STRATEGY_CODES, st.writeStrategy);
            selectByCode(bootVersionCombo, BOOT_CODES, st.bootVersion);
            selectByCode(docStyleCombo, DOC_CODES, st.docStyle);
            selectByCode(mapperStrategyCombo, MAPPER_STRATEGY_CODES, st.mapperStrategy);
            tablePrefixesField.setText(StringUtils.defaultString(st.tablePrefixes));
            baseEntityField.setText(StringUtils.defaultString(st.baseEntityClass));
            feignServiceNameField.setText(StringUtils.defaultString(st.feignServiceName));
            responseWrapperField.setText(StringUtils.defaultString(st.responseWrapperClass));
            enableValidationCheckBox.setSelected(st.enableValidation);
            workingTemplates.clear();
            if (st.customTemplates != null) {
                workingTemplates.putAll(st.customTemplates);
            }
        }
        String selected = (String) templateSelectorCombo.getSelectedItem();
        if (selected == null) {
            selected = CodeGenSettingsState.TEMPLATE_NAMES[0];
        }
        loadTemplateIntoEditor(selected);
    }

    private String getSelectedCode(ComboBox<String> combo, String[] codes) {
        int idx = combo.getSelectedIndex();
        return (idx >= 0 && idx < codes.length) ? codes[idx] : codes[0];
    }

    private void selectByCode(ComboBox<String> combo, String[] codes, String code) {
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equalsIgnoreCase(code)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }
}
