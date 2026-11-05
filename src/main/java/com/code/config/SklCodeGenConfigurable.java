package com.code.config;

import com.code.ui.SolidGlassTheme;
import com.code.ui.SolidGlassTheme.*;
import com.code.utils.CodeGenerateUtils;
import com.code.utils.IncrementalCodeMerger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * IDE Settings 模板中心与全局策略配置页（克制低噪点 Solid Glass 风格）
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
            "增量合并（保留手写字段与方法，仅追加新字段）",
            "Diff 对比确认（弹出左右分栏对比确认后写入）",
            "直接覆盖（直接替换同名文件）"
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

    private final Project project;
    private final CodeGenSettingsState settingsService;
    private final StatusGlyph statusGlyph = new StatusGlyph("模板中心就绪", GlyphState.READY);

    private ComboBox<String> writeStrategyCombo;
    private ComboBox<String> bootVersionCombo;
    private ComboBox<String> docStyleCombo;
    private JBCheckBox enableValidationCheckBox;

    private ComboBox<String> templateSelectorCombo;
    private JBTextArea templateEditorArea;
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
                "Syrobin Code Generate · 全局策略与模板中心",
                "v2.2.0",
                statusGlyph
        );

        writeStrategyCombo = new ComboBox<>(WRITE_STRATEGY_LABELS);
        bootVersionCombo = new ComboBox<>(BOOT_LABELS);
        docStyleCombo = new ComboBox<>(DOC_LABELS);
        enableValidationCheckBox = new JBCheckBox("默认开启字段校验注解推断 (@NotBlank / @NotNull / @Size / @Email / @Pattern)", true);
        enableValidationCheckBox.setOpaque(false);

        JPanel rawStrategyForm = FormBuilder.createFormBuilder()
                .addLabeledComponent("默认文件写入方式:", writeStrategyCombo)
                .addLabeledComponent("Spring Boot 版本预设:", bootVersionCombo)
                .addLabeledComponent("默认文档注解风格:", docStyleCombo)
                .addComponent(enableValidationCheckBox)
                .getPanel();
        rawStrategyForm.setOpaque(false);

        SolidGlassPanel strategyCard = new SolidGlassPanel(new BorderLayout(), Thickness.MEDIUM);
        strategyCard.add(rawStrategyForm, BorderLayout.CENTER);

        SolidGlassPanel templateCenterCard = new SolidGlassPanel(new BorderLayout(6, 6), Thickness.MEDIUM);

        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.setOpaque(false);
        templateSelectorCombo = new ComboBox<>(CodeGenSettingsState.TEMPLATE_NAMES);
        templateSelectorCombo.addActionListener(e -> {
            saveCurrentEditorToWorkingMap();
            String selected = (String) templateSelectorCombo.getSelectedItem();
            if (selected != null) {
                loadTemplateIntoEditor(selected);
                statusGlyph.setStatus("当前模板：" + selected, GlyphState.READY);
            }
        });

        JPanel btnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnGroup.setOpaque(false);
        GlassButton resetCurrentBtn = new GlassButton("恢复当前模板为内置默认");
        resetCurrentBtn.addActionListener(e -> {
            if (currentEditingTemplateName != null) {
                String builtIn = CodeGenSettingsState.loadBuiltInTemplate(currentEditingTemplateName);
                workingTemplates.remove(currentEditingTemplateName);
                templateEditorArea.setText(builtIn);
                templateEditorArea.setCaretPosition(0);
                statusGlyph.setStatus("已重置：" + currentEditingTemplateName, GlyphState.READY);
            }
        });

        GlassButton exportToProjectBtn = new GlassButton("导出全部模板到工程 resources/templates", true);
        exportToProjectBtn.addActionListener(e -> exportTemplatesToProject());

        btnGroup.add(resetCurrentBtn);
        btnGroup.add(exportToProjectBtn);

        JPanel selectorLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        selectorLeft.setOpaque(false);
        selectorLeft.add(new JBLabel("选择 FreeMarker 模板:"));
        selectorLeft.add(templateSelectorCombo);

        toolbar.add(selectorLeft, BorderLayout.WEST);
        toolbar.add(btnGroup, BorderLayout.EAST);

        templateEditorArea = new JBTextArea(18, 80);
        templateEditorArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        templateEditorArea.setTabSize(4);
        templateEditorArea.setBackground(SolidGlassTheme.inputBg());
        templateEditorArea.setForeground(SolidGlassTheme.textPrimary());
        templateEditorArea.setCaretColor(SolidGlassTheme.focusBorderColor());

        JBLabel varsHint = new JBLabel(
                "<html><b>可用模板变量：</b> " +
                        "<code>${packageName}</code>、<code>${ClassName}</code>、<code>${className}</code>、" +
                        "<code>${tableName}</code>、<code>${functionName}</code>、<code>${bootVersion}</code> (2/3)、" +
                        "<code>${docStyle}</code>、<code>${enableValidation?c}</code>、<code>${validationPkg}</code>、<code>${list}</code></html>"
        );
        varsHint.setForeground(SolidGlassTheme.textSecondary());

        JBScrollPane editorScroll = new JBScrollPane(templateEditorArea);
        SolidGlassTheme.styleScrollPane(editorScroll);

        templateCenterCard.add(toolbar, BorderLayout.NORTH);
        templateCenterCard.add(editorScroll, BorderLayout.CENTER);
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
            statusGlyph.setStatus("已导出 11 套模板", GlyphState.READY);
            Messages.showInfoMessage(project, "已成功将 11 个 FreeMarker 模板导出至：\n" + targetDir, "导出成功");
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
