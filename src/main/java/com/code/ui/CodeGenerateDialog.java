package com.code.ui;

import com.code.config.CodeGenSettingsState;
import com.code.ui.SolidGlassTheme.*;
import com.code.utils.CodeGenerateUtils;
import com.code.utils.DdlSqlParser;
import com.code.utils.EnumCommentParser;
import com.code.utils.IncrementalCodeMerger;
import com.code.utils.ValidationAnnotationResolver;
import com.code.utils.entity.BeanQuery;
import com.code.utils.entity.EnumsEntityContent;
import com.code.utils.entity.ServiceQuery;
import com.code.utils.entity.Table;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.sql.Connection;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 一体化代码生成向导窗口（三层深灰配色 + 状态解耦 + 零选表禁用保护 + DDL 语法高亮）
 *
 * 本轮四项重点改进：
 * 1. 零选表状态保护：未勾选参与生成的表时，底部严格显示「待生成：0 张表 · 0 个文件」，并同步禁用「开始生成」主按钮（预览表的 12 个文件仅显示在右侧预览区）
 * 2. Query 页明确作用表与空态指导：顶部显示「当前规则作用于表：xxx」，表格空态说明字段用途与添加方式（替代 Nothing to show）
 * 3. 来源类型与验证状态解耦：使用中性 SourceBadge 显示「来源：数据库连接 / 离线 DDL」，使用独立 StatusGlyph 显示「未验证 / 连接成功 / 解析成功 / 失败」，绿色仅用于已确认的成功状态
 * 4. 离线 DDL 操作解耦与语法高亮：「清空 SQL 内容」与「切换来源」拆分为独立操作；解析结果收敛为单行摘要条，释放空间给带克制语法高亮的 SQL 编辑器
 *
 * @author syrobin
 */
public class CodeGenerateDialog extends DialogWrapper {

    private static final String[] JAVA_TYPES = {
            "String", "Integer", "Double", "BigDecimal", "LocalDateTime", "Date", "Long", "byte[]"
    };

    private static final String[] QUERY_TYPES = {
            "0:等于", "1:模糊查询", "2:为空查询", "3:不为空查询",
            "4:in查询", "5:notIn查询", "6:inSql查询", "7:notInSql查询",
            "8:区间查询", "9:非该区间查询"
    };

    private static final String[] WRITE_STRATEGY_CODES = {
            IncrementalCodeMerger.WriteStrategy.INCREMENTAL_MERGE.name(),
            IncrementalCodeMerger.WriteStrategy.DIFF_PREVIEW.name(),
            IncrementalCodeMerger.WriteStrategy.OVERWRITE.name()
    };

    private static final String[] WRITE_STRATEGY_LABELS = {
            "增量合并",
            "Diff 对比确认",
            "直接覆盖"
    };

    private static final String[] DOC_STYLE_CODES = {"NONE", "SWAGGER2", "OPENAPI3"};
    private static final String[] DOC_STYLE_LABELS = {
            "无文档注解",
            "Swagger 2 (@ApiModel)",
            "OpenAPI 3 (@Schema)"
    };

    private static final String SAMPLE_DDL =
            "-- 示例：用户订单表 (支持离线解析字段、非空长度约束与括号枚举注释)\n" +
            "CREATE TABLE `user_order` (\n" +
            "  `id` bigint NOT NULL PRIMARY KEY COMMENT '主键ID',\n" +
            "  `order_no` varchar(64) NOT NULL COMMENT '订单编号',\n" +
            "  `customer_mobile` varchar(20) NOT NULL COMMENT '客户手机号',\n" +
            "  `customer_email` varchar(128) DEFAULT NULL COMMENT '客户邮箱',\n" +
            "  `order_amount` decimal(12,2) NOT NULL COMMENT '订单金额',\n" +
            "  `order_status` tinyint NOT NULL DEFAULT 0 COMMENT '订单状态（0：待支付，1：已支付，2：已取消）',\n" +
            "  `create_date_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户订单表';";

    public static class TableItem {
        private final String tableName;
        private final String comment;
        private boolean checked;

        public TableItem(String tableName, String comment, boolean checked) {
            this.tableName = tableName;
            this.comment = StringUtils.defaultString(comment);
            this.checked = checked;
        }

        public String getTableName() {
            return tableName;
        }

        public String getComment() {
            return comment;
        }

        public boolean isChecked() {
            return checked;
        }

        public void setChecked(boolean checked) {
            this.checked = checked;
        }

        public String getDisplayLabel() {
            return StringUtils.isNotBlank(comment) ? tableName + " (" + comment + ")" : tableName;
        }
    }

    /**
     * 数据来源模式枚举：明确区分当前使用的是数据库连接还是离线 DDL
     */
    private enum DataSourceMode {
        DATABASE,
        OFFLINE_DDL
    }

    private final Project project;
    private final String projectRootPath;
    private final Map<String, Object> params;
    private final CodeGenSettingsState settingsService;

    private JBTabbedPane mainTabbedPane;
    private DataSourceMode currentSourceMode = DataSourceMode.DATABASE;

    // 顶栏拆分的两个独立徽标：中性来源徽标 SourceBadge + 验证结果状态徽标 StatusGlyph
    private final SourceBadge sourceBadge = new SourceBadge("来源：数据库连接");
    private final StatusGlyph verificationBadge = new StatusGlyph("未验证连接", GlyphState.IDLE);

    // 基础配置输入框
    private final JBTextField authorField = new JBTextField();
    private final JBTextField packageField = new JBTextField();
    private final JBTextField moduleField = new JBTextField();
    private final JBTextField selectedTablesReadOnlyField = new JBTextField();
    private final JBTextField fixedEnumField = new JBTextField();

    // 写入方式、文档注解与校验注解控件
    private final ComboBox<String> writeStrategyCombo = new ComboBox<>(WRITE_STRATEGY_LABELS);
    private final JLabel writeStrategyHintLabel = SolidGlassTheme.createHelperLabel("");
    private final ComboBox<String> docStyleCombo = new ComboBox<>(DOC_STYLE_LABELS);
    private final JBCheckBox enableValidationCheckBox = new JBCheckBox("自动推断 (@NotBlank / @Size / @Email)", true);

    // 数据库连接输入框与连接页专属状态徽标
    private final JBTextField jdbcUrlField = new JBTextField();
    private final JBTextField databaseField = new JBTextField();
    private final JBTextField usernameField = new JBTextField();
    private final JBPasswordField passwordField = new JBPasswordField();
    private final StatusGlyph dbPageStatusBadge = new StatusGlyph("未验证连接", GlyphState.IDLE);

    // 生成目标标准复选框 (JBCheckBox) 与计数标签
    private final Map<String, JBCheckBox> targetCheckBoxes = new LinkedHashMap<>();
    private final JLabel targetSelectionCountLabel = new JLabel("生成选项（已选 10 / 10 项）");

    // 底部「开始生成」旁实时摘要与输出路径标签（严格仅统计已勾选待生成对象）
    private final JLabel footerSummaryLabel = new JLabel();
    private final JLabel footerOutputPathLabel = new JLabel();

    // 左侧：选择数据库表
    private final JLabel leftTablePanelTitleLabel = new JLabel("选择生成表（勾选参与生成，点击行预览）");
    private final GlassButton loadTablesBtn = new GlassButton("加载表清单");
    private final JBTextField tableSearchField = new JBTextField();
    private final Map<String, TableItem> masterTableMap = new LinkedHashMap<>();
    private final DefaultListModel<TableItem> tableListModel = new DefaultListModel<>();
    private final JBList<TableItem> tableJList = new JBList<>(tableListModel) {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int index = locationToIndex(event.getPoint());
            Rectangle bounds = index >= 0 ? getCellBounds(index, index) : null;
            return bounds != null && bounds.contains(event.getPoint())
                    ? getModel().getElementAt(index).getDisplayLabel() : null;
        }
    };
    private String activePreviewTableName = "";

    // 右侧：生成文件预览
    private final JLabel rightPreviewTitleLabel = new JLabel("生成文件预览");
    private final JLabel rightPreviewDetailsLabel = SolidGlassTheme.createHelperLabel("请选择预览表");
    private final JBTextField outputDirReadOnlyField = new JBTextField();
    private DefaultTableModel filePreviewTableModel;
    private JBTable filePreviewTable;
    private final JLabel fieldParseSummaryLabel = SolidGlassTheme.createHelperLabel("字段解析：尚未读取表字段元数据");

    // 已解析表的列元数据缓存
    private final Map<String, List<Table>> resolvedTableColumnsCache = new HashMap<>();

    // 页签二：Query 页当前作用表标签与表格模型
    private final JLabel queryPageTargetTableLabel = new JLabel("规则作用于：未选择表");
    private GlassButton reloadQueryCacheBtn;
    private String loadedQueryTableName = "";
    private final Map<String, List<BeanQuery>> beanQueryDrafts = new HashMap<>();
    private final Map<String, List<ServiceQuery>> serviceQueryDrafts = new HashMap<>();
    private DefaultTableModel beanQueryTableModel;
    private JBTable beanQueryTable;
    private DefaultTableModel serviceQueryTableModel;
    private JBTable serviceQueryTable;

    // 页签四：离线 DDL 语法高亮编辑器与单行解析状态摘要
    private final SqlSyntaxHighlightPane ddlSqlPane = new SqlSyntaxHighlightPane();
    private final StatusGlyph ddlSingleLineSummaryBadge = new StatusGlyph("待解析 DDL（粘贴 CREATE TABLE 语句后点击「解析 DDL」）", GlyphState.IDLE);
    private final Map<String, DdlSqlParser.ParsedTable> parsedDdlTables = new LinkedHashMap<>();
    private String lastParsedDdlScript;
    private boolean dbConnectionVerified = false;
    private int databaseConfigVersion;
    private int lastVerifiedDbTableCount = 0;
    private Runnable onConfirmCallback;

    public CodeGenerateDialog(@NotNull Project project,
                              @NotNull String projectRootPath,
                              @NotNull Map<String, Object> initialParams,
                              @NotNull String dialogTitle) {
        super(project, true, IdeModalityType.MODELESS);
        this.project = project;
        this.projectRootPath = projectRootPath;
        this.params = initialParams;
        this.settingsService = CodeGenSettingsState.getInstance(project);

        this.settingsService.importFromProjectProperties(projectRootPath, false);

        setModal(false);
        setTitle(dialogTitle);
        setOKButtonText("开始生成");
        setCancelButtonText("取消");
        init();
        loadInitialValues();
    }

    public void setOnConfirm(@Nullable Runnable onConfirmCallback) {
        this.onConfirmCallback = onConfirmCallback;
    }

    private void loadInitialValues() {
        CodeGenSettingsState.ConfigState state = settingsService.getState();
        String initialSavedTables = "";
        if (state != null) {
            authorField.setText(StringUtils.defaultString(state.author, "syrobin"));
            packageField.setText(StringUtils.defaultString(state.packageName, "com.example"));
            moduleField.setText(StringUtils.defaultString(state.lastModuleName, ""));
            initialSavedTables = StringUtils.defaultString(state.lastTableNames, "");

            jdbcUrlField.setText(StringUtils.defaultString(state.jdbcUrl));
            databaseField.setText(StringUtils.defaultString(state.database));
            usernameField.setText(StringUtils.defaultString(state.username, "root"));
            passwordField.setText(StringUtils.defaultString(state.retrieveCredential()));

            selectStrategyComboByCode(state.writeStrategy);
            selectDocStyleComboByCode(state.docStyle);
            enableValidationCheckBox.setSelected(state.enableValidation);

            if (StringUtils.isNotBlank(state.lastDdlScript)) {
                ddlSqlPane.setText(state.lastDdlScript);
                parseDdlAndPopulateTables(false);
            }
        }

        Object preloadedDdl = params.get("preloadedDdlScript");
        if (preloadedDdl instanceof String && StringUtils.isNotBlank((String) preloadedDdl)) {
            ddlSqlPane.setText((String) preloadedDdl);
            parseDdlAndPopulateTables(false);
        }

        if (masterTableMap.isEmpty() && StringUtils.isNotBlank(initialSavedTables)) {
            for (String raw : initialSavedTables.split(",")) {
                String t = raw.trim();
                if (StringUtils.isNotBlank(t)) {
                    masterTableMap.put(t, new TableItem(t, "", true));
                    if (StringUtils.isBlank(activePreviewTableName)) {
                        activePreviewTableName = t;
                    }
                }
            }
            filterTableList(tableSearchField.getText());
        }

        for (Map.Entry<String, JBCheckBox> entry : targetCheckBoxes.entrySet()) {
            boolean checked = StringUtils.equalsIgnoreCase(String.valueOf(params.get(entry.getKey())), "y");
            entry.getValue().setSelected(checked);
        }

        updateWriteStrategyHint();
        syncCheckedTablesToSummaryField();
        reloadQueryTablesForCurrentTable();
        refreshFileImpactPreview();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel rootCanvas = new JPanel(new BorderLayout(0, 8));
        rootCanvas.setBackground(SolidGlassTheme.canvasBg());
        rootCanvas.setBorder(JBUI.Borders.empty(8, 10, 4, 10));
        rootCanvas.setPreferredSize(new Dimension(910, 690));

        // 1. 顶栏：左侧品牌标题 + 右侧「中性来源徽标 SourceBadge + 独立验证状态 StatusGlyph + 切换来源按钮」
        JPanel sourceAreaRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        sourceAreaRight.setOpaque(false);
        sourceAreaRight.add(sourceBadge);
        sourceAreaRight.add(verificationBadge);

        GlassButton switchSourceBtn = new GlassButton("切换来源");
        switchSourceBtn.addActionListener(e -> {
            databaseConfigVersion++;
            if (currentSourceMode == DataSourceMode.DATABASE) {
                currentSourceMode = DataSourceMode.OFFLINE_DDL;
                if (mainTabbedPane != null) {
                    mainTabbedPane.setSelectedIndex(3);
                }
            } else {
                currentSourceMode = DataSourceMode.DATABASE;
                if (mainTabbedPane != null) {
                    mainTabbedPane.setSelectedIndex(2);
                }
            }
            updateSummaryAndSourceIndicators();
        });
        sourceAreaRight.add(switchSourceBtn);

        JPanel compactHeader = SolidGlassTheme.createCompactHeader(
                "Syrobin Code Generate",
                "v2.2.0",
                sourceAreaRight
        );

        // 2. 独立功能页签
        mainTabbedPane = new JBTabbedPane();
        mainTabbedPane.setOpaque(false);
        mainTabbedPane.addTab("生成配置与选表", createBasicAndTablePanel());
        mainTabbedPane.addTab("自定义 Query 规则", createQueryRulesPanel());
        mainTabbedPane.addTab("数据源连接", createDatabaseConfigPanel());
        mainTabbedPane.addTab("离线 DDL 解析", createOfflineDdlPanel());

        mainTabbedPane.addChangeListener(e -> {
            if (mainTabbedPane.getSelectedIndex() == 1) {
                reloadQueryTablesForCurrentTable();
            }
        });

        rootCanvas.add(compactHeader, BorderLayout.NORTH);
        rootCanvas.add(mainTabbedPane, BorderLayout.CENTER);

        return rootCanvas;
    }

    /**
     * 底部「开始生成」旁实时摘要（严格只统计已勾选参与生成的对象，绝不混入仅预览未勾选的表）
     */
    @Nullable
    @Override
    protected JPanel createSouthAdditionalPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 2));
        panel.setOpaque(false);
        panel.setBorder(JBUI.Borders.emptyLeft(4));

        footerSummaryLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(12f)));
        footerSummaryLabel.setForeground(SolidGlassTheme.textPrimary());

        footerOutputPathLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.PLAIN, JBUI.scaleFontSize(11.5f)));
        footerOutputPathLabel.setForeground(SolidGlassTheme.textMuted());

        panel.add(footerSummaryLabel, BorderLayout.NORTH);
        panel.add(footerOutputPathLabel, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * 页签一：双列紧凑表单 + 标准复选框网格 + 定制 #343A46 分隔条的左右表选择与预览区
     */
    private JComponent createBasicAndTablePanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(0, 8));
        mainPanel.setOpaque(false);
        mainPanel.setBorder(JBUI.Borders.empty(4, 0, 0, 0));

        DocumentAdapter liveSummaryListener = new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                updateSummaryAndSourceIndicators();
                refreshFileImpactPreview();
            }
        };
        authorField.getDocument().addDocumentListener(liveSummaryListener);
        packageField.getDocument().addDocumentListener(liveSummaryListener);
        moduleField.getDocument().addDocumentListener(liveSummaryListener);
        fixedEnumField.getDocument().addDocumentListener(liveSummaryListener);

        styleTextField(authorField);
        styleTextField(packageField);
        styleTextField(moduleField);
        styleTextField(selectedTablesReadOnlyField);
        styleTextField(fixedEnumField);

        moduleField.getEmptyText().setText("例如：article（输出包名 = 基础包名.模块名）");
        fixedEnumField.getEmptyText().setText("可选：例如 status");

        selectedTablesReadOnlyField.setEditable(false);
        selectedTablesReadOnlyField.getEmptyText().setText("尚未勾选表（请在下方左侧列表勾选）");

        JPanel selectedTablesBox = new JPanel(new BorderLayout(6, 0));
        selectedTablesBox.setOpaque(false);
        selectedTablesBox.add(selectedTablesReadOnlyField, BorderLayout.CENTER);
        GlassButton manualAddTableBtn = new GlassButton("+ 手动添加表");
        manualAddTableBtn.addActionListener(e -> promptManualAddTables());
        selectedTablesBox.add(manualAddTableBtn, BorderLayout.EAST);

        writeStrategyCombo.addActionListener(e -> {
            updateWriteStrategyHint();
            updateSummaryAndSourceIndicators();
            refreshFileImpactPreview();
        });

        enableValidationCheckBox.addActionListener(e -> refreshFileImpactPreview());

        // 1. 双列紧凑表单
        SolidGlassPanel formCard = new SolidGlassPanel(new GridBagLayout(), Thickness.MEDIUM);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(3, 6, 3, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        addGridPair(formCard, gbc, 0,
                "作者名称:", authorField,
                "模块名称:", moduleField);

        addGridPair(formCard, gbc, 1,
                "基础包名:", packageField,
                "已选生成表:", selectedTablesBox);

        JPanel enumBox = new JPanel(new BorderLayout(0, 2));
        enumBox.setOpaque(false);
        enumBox.add(fixedEnumField, BorderLayout.NORTH);
        enumBox.add(SolidGlassTheme.createHelperLabel("留空生成所有含括号键值注释的列；填写列名仅生成该列"), BorderLayout.SOUTH);

        JPanel writeStrategyBox = new JPanel(new BorderLayout(0, 2));
        writeStrategyBox.setOpaque(false);
        writeStrategyBox.add(writeStrategyCombo, BorderLayout.NORTH);
        writeStrategyBox.add(writeStrategyHintLabel, BorderLayout.SOUTH);

        addGridPair(formCard, gbc, 2,
                "限定枚举列:", enumBox,
                "文件写入方式:", writeStrategyBox);

        enableValidationCheckBox.setOpaque(false);
        enableValidationCheckBox.setForeground(SolidGlassTheme.textPrimary());
        addGridPair(formCard, gbc, 3,
                "文档注解:", docStyleCombo,
                "校验注解:", enableValidationCheckBox);

        // 2. 生成选项区
        SolidGlassPanel targetsCard = new SolidGlassPanel(new BorderLayout(0, 6), Thickness.MEDIUM);

        JPanel targetsHeader = new JPanel(new BorderLayout());
        targetsHeader.setOpaque(false);
        targetSelectionCountLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(12f)));
        targetSelectionCountLabel.setForeground(SolidGlassTheme.textPrimary());

        JPanel quickBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        quickBtnPanel.setOpaque(false);
        GlassButton selectAllBtn = new GlassButton("全选");
        selectAllBtn.addActionListener(e -> {
            targetCheckBoxes.values().forEach(cb -> cb.setSelected(true));
            updateSummaryAndSourceIndicators();
            refreshFileImpactPreview();
        });
        GlassButton clearAllBtn = new GlassButton("清空");
        clearAllBtn.addActionListener(e -> {
            targetCheckBoxes.values().forEach(cb -> cb.setSelected(false));
            updateSummaryAndSourceIndicators();
            refreshFileImpactPreview();
        });
        GlassButton queryOnlyBtn = new GlassButton("仅 Query 预设");
        queryOnlyBtn.addActionListener(e -> {
            targetCheckBoxes.values().forEach(cb -> cb.setSelected(false));
            targetCheckBoxes.get("entityQuery").setSelected(true);
            targetCheckBoxes.get("serviceQuery").setSelected(true);
            updateSummaryAndSourceIndicators();
            refreshFileImpactPreview();
        });
        quickBtnPanel.add(selectAllBtn);
        quickBtnPanel.add(clearAllBtn);
        quickBtnPanel.add(queryOnlyBtn);

        targetsHeader.add(targetSelectionCountLabel, BorderLayout.WEST);
        targetsHeader.add(quickBtnPanel, BorderLayout.EAST);

        JPanel checkGrid = new JPanel(new GridLayout(2, 5, 6, 4));
        checkGrid.setOpaque(false);
        registerTargetCheckBox(checkGrid, "entity", "Entity 实体");
        registerTargetCheckBox(checkGrid, "entityBO", "Entity BO");
        registerTargetCheckBox(checkGrid, "entityVO", "Entity VO");
        registerTargetCheckBox(checkGrid, "entityQuery", "Entity Query");
        registerTargetCheckBox(checkGrid, "enums", "Enum 枚举 (按列生成)");
        registerTargetCheckBox(checkGrid, "mapper", "Mapper 接口");
        registerTargetCheckBox(checkGrid, "service", "Service (接口+Impl)");
        registerTargetCheckBox(checkGrid, "serviceQuery", "ServiceQuery");
        registerTargetCheckBox(checkGrid, "serviceResults", "ServiceResults");
        registerTargetCheckBox(checkGrid, "controller", "Controller");

        targetsCard.add(targetsHeader, BorderLayout.NORTH);
        targetsCard.add(checkGrid, BorderLayout.CENTER);

        JPanel topCombined = new JPanel(new BorderLayout(0, 8));
        topCombined.setOpaque(false);
        topCombined.add(formCard, BorderLayout.NORTH);
        topCombined.add(targetsCard, BorderLayout.CENTER);

        // 3. 底部左右分栏：使用 createCleanSplitPane 彻底消除亮白色竖向分隔条
        SolidGlassPanel leftTableCard = createLeftTableSelectionCard();
        SolidGlassPanel rightPreviewCard = createRightFilePreviewCard();

        JSplitPane bottomSplit = SolidGlassTheme.createCleanSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                leftTableCard,
                rightPreviewCard,
                360,
                0.42
        );

        mainPanel.add(topCombined, BorderLayout.NORTH);
        mainPanel.add(bottomSplit, BorderLayout.CENTER);
        return mainPanel;
    }

    private void styleTextField(JBTextField field) {
        field.setBackground(SolidGlassTheme.inputBg());
        field.setForeground(SolidGlassTheme.textPrimary());
    }

    private SolidGlassPanel createLeftTableSelectionCard() {
        SolidGlassPanel card = new SolidGlassPanel(new BorderLayout(0, 6), Thickness.MEDIUM);

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        leftTablePanelTitleLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(12f)));
        leftTablePanelTitleLabel.setForeground(SolidGlassTheme.textPrimary());

        JPanel checkAllBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        checkAllBar.setOpaque(false);
        GlassButton checkAllTablesBtn = new GlassButton("全选表");
        checkAllTablesBtn.addActionListener(e -> {
            for (int i = 0; i < tableListModel.size(); i++) {
                tableListModel.getElementAt(i).setChecked(true);
            }
            tableJList.repaint();
            syncCheckedTablesToSummaryField();
        });
        GlassButton uncheckAllTablesBtn = new GlassButton("清空勾选");
        uncheckAllTablesBtn.addActionListener(e -> {
            for (TableItem item : masterTableMap.values()) {
                item.setChecked(false);
            }
            tableJList.repaint();
            syncCheckedTablesToSummaryField();
        });
        checkAllBar.add(checkAllTablesBtn);
        checkAllBar.add(uncheckAllTablesBtn);

        headerRow.add(leftTablePanelTitleLabel, BorderLayout.WEST);
        headerRow.add(checkAllBar, BorderLayout.EAST);

        JPanel filterToolbar = new JPanel(new BorderLayout(6, 0));
        filterToolbar.setOpaque(false);
        styleTextField(tableSearchField);
        tableSearchField.getEmptyText().setText("筛选表名或注释...");
        tableSearchField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                filterTableList(tableSearchField.getText());
            }
        });
        loadTablesBtn.addActionListener(e -> fetchDatabaseTablesAsync());

        filterToolbar.add(tableSearchField, BorderLayout.CENTER);
        filterToolbar.add(loadTablesBtn, BorderLayout.EAST);

        JPanel northBox = new JPanel(new BorderLayout(0, 5));
        northBox.setOpaque(false);
        northBox.add(headerRow, BorderLayout.NORTH);
        northBox.add(filterToolbar, BorderLayout.SOUTH);

        tableJList.setBackground(SolidGlassTheme.inputBg());
        tableJList.setFixedCellHeight(JBUI.scale(26));
        tableJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableJList.setCellRenderer(new TableItemCellRenderer());
        tableJList.getEmptyText().setText("尚无生成表，请加载数据库表或解析离线 DDL");
        ToolTipManager.sharedInstance().registerComponent(tableJList);

        tableJList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int idx = tableJList.locationToIndex(e.getPoint());
                if (idx < 0 || idx >= tableListModel.size()) {
                    return;
                }
                Rectangle cellBounds = tableJList.getCellBounds(idx, idx);
                if (cellBounds == null || !cellBounds.contains(e.getPoint())) {
                    return;
                }
                TableItem clickedItem = tableListModel.getElementAt(idx);
                int relX = e.getPoint().x - cellBounds.x;

                if (relX <= JBUI.scale(28) || e.getClickCount() == 2) {
                    clickedItem.setChecked(!clickedItem.isChecked());
                }

                activePreviewTableName = clickedItem.getTableName();
                tableJList.repaint();
                syncCheckedTablesToSummaryField();
                reloadQueryTablesForCurrentTable();
                refreshFileImpactPreview();
            }
        });

        JBScrollPane listScroll = new JBScrollPane(tableJList);
        SolidGlassTheme.styleScrollPane(listScroll);
        listScroll.setPreferredSize(new Dimension(350, 230));
        listScroll.setMinimumSize(new Dimension(260, 180));

        card.add(northBox, BorderLayout.NORTH);
        card.add(listScroll, BorderLayout.CENTER);
        return card;
    }

    /**
     * 表列表渲染器：使用 #263A57 / #82B5FF 标识当前预览行（失焦后依然清晰保持）
     */
    private class TableItemCellRenderer extends JPanel implements ListCellRenderer<TableItem> {
        private final JCheckBox checkBox = new JCheckBox();
        private final JLabel nameLabel = new JLabel();
        private final JLabel previewBadge = new JLabel("预览中");
        private boolean isPreviewActive = false;

        public TableItemCellRenderer() {
            super(new BorderLayout(6, 0));
            setBorder(JBUI.Borders.empty(0, 6));
            checkBox.setOpaque(false);
            nameLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.PLAIN, JBUI.scaleFontSize(12f)));
            previewBadge.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(10.5f)));
            previewBadge.setForeground(SolidGlassTheme.selectedFg());

            JPanel left = new JPanel(new BorderLayout(4, 0));
            left.setOpaque(false);
            left.add(checkBox, BorderLayout.WEST);
            left.add(nameLabel, BorderLayout.CENTER);

            add(left, BorderLayout.CENTER);
            add(previewBadge, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends TableItem> list,
                                                      TableItem value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            checkBox.setSelected(value.isChecked());
            nameLabel.setText(value.getDisplayLabel());
            isPreviewActive = StringUtils.equalsIgnoreCase(value.getTableName(), activePreviewTableName);
            previewBadge.setVisible(isPreviewActive);

            if (isPreviewActive) {
                setOpaque(true);
                setBackground(SolidGlassTheme.selectedBg());
                nameLabel.setForeground(SolidGlassTheme.selectedFg());
                nameLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(12f)));
            } else if (value.isChecked()) {
                setOpaque(true);
                setBackground(SolidGlassTheme.hoverBg());
                nameLabel.setForeground(SolidGlassTheme.textPrimary());
                nameLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.PLAIN, JBUI.scaleFontSize(12f)));
            } else {
                setOpaque(true);
                setBackground(SolidGlassTheme.inputBg());
                nameLabel.setForeground(SolidGlassTheme.textSecondary());
                nameLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.PLAIN, JBUI.scaleFontSize(12f)));
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (isPreviewActive) {
                g.setColor(SolidGlassTheme.selectedFg());
                g.fillRect(0, 2, JBUI.scale(3), getHeight() - 4);
            }
        }
    }

    /**
     * 构建右侧面板：「生成文件预览」
     * - 收敛蓝色语义：「合并（保留手写代码）」使用次要文字色 #A8B0BD；「新增」使用绿色 #66C18C；「冲突」使用红色 #F08B8B
     */
    private SolidGlassPanel createRightFilePreviewCard() {
        SolidGlassPanel card = new SolidGlassPanel(new BorderLayout(0, 6), Thickness.MEDIUM);

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        rightPreviewTitleLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(12f)));
        rightPreviewTitleLabel.setForeground(SolidGlassTheme.textPrimary());

        rightPreviewTitleLabel.setMinimumSize(new Dimension(0, 0));
        GlassButton refreshPreviewBtn = new GlassButton("刷新预览");
        refreshPreviewBtn.setToolTipText("重新读取字段并更新生成文件预览");
        refreshPreviewBtn.addActionListener(e -> previewSelectedTableEnumsAsync());

        ((BorderLayout) headerRow.getLayout()).setHgap(JBUI.scale(8));
        headerRow.add(rightPreviewTitleLabel, BorderLayout.CENTER);
        headerRow.add(refreshPreviewBtn, BorderLayout.EAST);

        JPanel previewHeading = new JPanel(new BorderLayout(0, 4));
        previewHeading.setOpaque(false);
        previewHeading.add(headerRow, BorderLayout.NORTH);
        previewHeading.add(rightPreviewDetailsLabel, BorderLayout.SOUTH);

        JPanel outputDirRow = new JPanel(new BorderLayout(6, 0));
        outputDirRow.setOpaque(false);
        styleTextField(outputDirReadOnlyField);
        outputDirReadOnlyField.setEditable(false);
        outputDirReadOnlyField.setForeground(SolidGlassTheme.textMuted());
        outputDirReadOnlyField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(11.5f)));

        GlassButton copyDirBtn = new GlassButton("复制路径");
        copyDirBtn.addActionListener(e -> {
            String dirText = outputDirReadOnlyField.getText();
            if (StringUtils.isNotBlank(dirText)) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(dirText), null);
                copyDirBtn.setText("已复制 ✓");
                javax.swing.Timer t = new javax.swing.Timer(1200, ev -> copyDirBtn.setText("复制路径"));
                t.setRepeats(false);
                t.start();
            }
        });

        JBLabel outDirLabel = new JBLabel("输出目录:");
        outDirLabel.setForeground(SolidGlassTheme.textSecondary());
        outputDirRow.add(outDirLabel, BorderLayout.WEST);
        outputDirRow.add(outputDirReadOnlyField, BorderLayout.CENTER);
        outputDirRow.add(copyDirBtn, BorderLayout.EAST);

        JPanel northBox = new JPanel(new BorderLayout(0, 5));
        northBox.setOpaque(false);
        northBox.add(previewHeading, BorderLayout.NORTH);
        northBox.add(outputDirRow, BorderLayout.SOUTH);

        filePreviewTableModel = new DefaultTableModel(new Object[]{"文件路径（相对输出目录）", "处理方式"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        filePreviewTable = new JBTable(filePreviewTableModel);
        filePreviewTable.setBackground(SolidGlassTheme.inputBg());
        filePreviewTable.setForeground(SolidGlassTheme.textPrimary());
        filePreviewTable.setGridColor(SolidGlassTheme.borderColor());
        filePreviewTable.setSelectionBackground(SolidGlassTheme.selectedBg());
        filePreviewTable.setSelectionForeground(SolidGlassTheme.selectedFg());
        filePreviewTable.setRowHeight(JBUI.scale(23));
        filePreviewTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(11.5f)));
        filePreviewTable.getColumnModel().getColumn(0).setPreferredWidth(305);
        filePreviewTable.getColumnModel().getColumn(1).setPreferredWidth(145);
        filePreviewTable.getEmptyText().setText("请在左侧选择表以预览即将生成的文件及处理方式");

        // 处理方式列配色：新增 -> 绿色(#66C18C)；合并 -> 次要文字色(#A8B0BD)；冲突 -> 错误红(#F08B8B)；覆盖/跳过 -> 警告黄(#E4B567)/辅助灰(#909AA9)
        filePreviewTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String text = Objects.toString(value, "");
                if (!isSelected) {
                    c.setBackground(SolidGlassTheme.inputBg());
                    if (text.startsWith("新增")) {
                        c.setForeground(SolidGlassTheme.successFg());
                    } else if (text.startsWith("合并")) {
                        c.setForeground(SolidGlassTheme.textSecondary());
                    } else if (text.startsWith("冲突")) {
                        c.setForeground(SolidGlassTheme.errorFg());
                    } else if (text.startsWith("覆盖") || text.startsWith("跳过")) {
                        c.setForeground(SolidGlassTheme.warningFg());
                    } else {
                        c.setForeground(SolidGlassTheme.textMuted());
                    }
                }
                return c;
            }
        });

        JBScrollPane tableScroll = new JBScrollPane(filePreviewTable);
        SolidGlassTheme.styleScrollPane(tableScroll);
        tableScroll.setPreferredSize(new Dimension(480, 195));
        tableScroll.setMinimumSize(new Dimension(320, 150));

        card.add(northBox, BorderLayout.NORTH);
        card.add(tableScroll, BorderLayout.CENTER);
        card.add(fieldParseSummaryLabel, BorderLayout.SOUTH);
        return card;
    }

    private void promptManualAddTables() {
        String input = Messages.showInputDialog(
                project,
                "请输入要添加的数据库表名（多表用英文逗号分隔）：",
                "手动添加表至选择列表",
                Messages.getQuestionIcon()
        );
        if (StringUtils.isBlank(input)) {
            return;
        }
        String firstAdded = null;
        for (String raw : input.split(",")) {
            String t = raw.trim();
            if (StringUtils.isNotBlank(t)) {
                TableItem item = masterTableMap.get(t);
                if (item == null) {
                    item = new TableItem(t, "", true);
                    masterTableMap.put(t, item);
                } else {
                    item.setChecked(true);
                }
                if (firstAdded == null) {
                    firstAdded = t;
                }
            }
        }
        if (firstAdded != null) {
            activePreviewTableName = firstAdded;
        }
        filterTableList(tableSearchField.getText());
        syncCheckedTablesToSummaryField();
        reloadQueryTablesForCurrentTable();
        refreshFileImpactPreview();
    }

    private List<String> getCheckedTableNames() {
        return masterTableMap.values().stream()
                .filter(TableItem::isChecked)
                .map(TableItem::getTableName)
                .collect(Collectors.toList());
    }

    private void syncCheckedTablesToSummaryField() {
        List<String> checked = getCheckedTableNames();
        selectedTablesReadOnlyField.setText(String.join(",", checked));
        leftTablePanelTitleLabel.setText("选择生成表（已勾选 " + checked.size() + " 张参与生成）");
        if (StringUtils.isBlank(activePreviewTableName) && !checked.isEmpty()) {
            activePreviewTableName = checked.get(0);
        }
        updateSummaryAndSourceIndicators();
        refreshFileImpactPreview();
    }

    private void addGridPair(JPanel panel,
                             GridBagConstraints gbc,
                             int row,
                             String leftLabel,
                             JComponent leftComp,
                             String rightLabel,
                             JComponent rightComp) {
        gbc.gridy = row;

        gbc.gridx = 0;
        gbc.weightx = 0;
        JBLabel l1 = new JBLabel(leftLabel);
        l1.setForeground(SolidGlassTheme.textPrimary());
        panel.add(l1, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.48;
        panel.add(leftComp, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        JBLabel l2 = new JBLabel(rightLabel);
        l2.setForeground(SolidGlassTheme.textPrimary());
        l2.setBorder(JBUI.Borders.emptyLeft(8));
        panel.add(l2, gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.52;
        panel.add(rightComp, gbc);
    }

    /**
     * 页签二：自定义 Query 与 ServiceQuery 规则
     * - 顶部明确显示「规则作用于：<当前表名>」
     * - 将默认 Nothing to show 替换为明确说明字段用途的引导空态
     */
    private JComponent createQueryRulesPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 8));
        outer.setOpaque(false);
        outer.setBorder(JBUI.Borders.empty(4, 0, 0, 0));

        // 顶部当前作用表信息栏
        SolidGlassPanel targetBar = new SolidGlassPanel(new BorderLayout(8, 0), Thickness.THIN);
        targetBar.setBorder(JBUI.Borders.empty(6, 12));
        queryPageTargetTableLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(12.5f)));
        queryPageTargetTableLabel.setForeground(SolidGlassTheme.selectedFg());

        JLabel scopeHint = SolidGlassTheme.createHelperLabel("说明：扩展字段与查询条件按表独立保存在本地工作区，切换预览表时自动加载对应规则");
        targetBar.add(queryPageTargetTableLabel, BorderLayout.WEST);
        targetBar.add(scopeHint, BorderLayout.EAST);

        JPanel tablesGrid = new JPanel(new GridLayout(2, 1, 0, 8));
        tablesGrid.setOpaque(false);

        // 1. BeanQuery 扩展字段卡片
        SolidGlassPanel beanCard = new SolidGlassPanel(new BorderLayout(4, 6), Thickness.MEDIUM);
        beanQueryTableModel = new DefaultTableModel(new Object[]{"变量名称 (name)", "Java类型 (format)", "字段描述 (description)"}, 0);
        beanQueryTable = new JBTable(beanQueryTableModel);
        beanQueryTable.setBackground(SolidGlassTheme.inputBg());
        beanQueryTable.setForeground(SolidGlassTheme.textPrimary());
        beanQueryTable.setGridColor(SolidGlassTheme.borderColor());
        beanQueryTable.setRowHeight(JBUI.scale(24));
        TableColumn typeColumn = beanQueryTable.getColumnModel().getColumn(1);
        typeColumn.setCellEditor(new DefaultCellEditor(new ComboBox<>(JAVA_TYPES)));

        // 定制空态说明（替代 Nothing to show）
        StatusText beanEmptyText = beanQueryTable.getEmptyText();
        beanEmptyText.setText("尚未添加扩展字段", new com.intellij.ui.SimpleTextAttributes(
                com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN, SolidGlassTheme.textSecondary()));
        beanEmptyText.appendSecondaryText("可添加关键词、时间范围等非表字段参数", new com.intellij.ui.SimpleTextAttributes(
                com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN, SolidGlassTheme.textMuted()), null);

        JPanel beanBtnBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        beanBtnBar.setOpaque(false);
        JBLabel beanTitle = new JBLabel("EntityQuery 扩展字段");
        beanTitle.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(12f)));
        beanTitle.setForeground(SolidGlassTheme.textPrimary());
        beanBtnBar.add(beanTitle);

        GlassButton addBeanRowBtn = new GlassButton("+ 新增扩展字段");
        addBeanRowBtn.addActionListener(e -> beanQueryTableModel.addRow(new Object[]{"", "String", ""}));
        GlassButton removeBeanRowBtn = new GlassButton("- 删除选中");
        removeBeanRowBtn.addActionListener(e -> removeSelectedRows(beanQueryTable, beanQueryTableModel));
        GlassButton clearBeanBtn = new GlassButton("清空");
        clearBeanBtn.addActionListener(e -> beanQueryTableModel.setRowCount(0));
        reloadQueryCacheBtn = new GlassButton("重载当前表缓存");
        reloadQueryCacheBtn.addActionListener(e -> {
            beanQueryDrafts.remove(loadedQueryTableName);
            serviceQueryDrafts.remove(loadedQueryTableName);
            loadedQueryTableName = "";
            reloadQueryTablesForCurrentTable();
        });

        beanBtnBar.add(addBeanRowBtn);
        beanBtnBar.add(removeBeanRowBtn);
        beanBtnBar.add(clearBeanBtn);
        beanBtnBar.add(reloadQueryCacheBtn);

        JBScrollPane beanScroll = new JBScrollPane(beanQueryTable);
        SolidGlassTheme.styleScrollPane(beanScroll);
        beanCard.add(beanBtnBar, BorderLayout.NORTH);
        beanCard.add(beanScroll, BorderLayout.CENTER);

        // 2. ServiceQuery 查询规则卡片
        SolidGlassPanel serviceCard = new SolidGlassPanel(new BorderLayout(4, 6), Thickness.MEDIUM);
        serviceQueryTableModel = new DefaultTableModel(
                new Object[]{"查询类型", "数据库列名", "实体属性名", "是否String", "区间开始属性", "区间结束属性", "自定义SQL"}, 0
        ) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 3 ? Boolean.class : String.class;
            }
        };
        serviceQueryTable = new JBTable(serviceQueryTableModel);
        serviceQueryTable.setBackground(SolidGlassTheme.inputBg());
        serviceQueryTable.setForeground(SolidGlassTheme.textPrimary());
        serviceQueryTable.setGridColor(SolidGlassTheme.borderColor());
        serviceQueryTable.setRowHeight(JBUI.scale(24));
        TableColumn queryTypeCol = serviceQueryTable.getColumnModel().getColumn(0);
        queryTypeCol.setCellEditor(new DefaultCellEditor(new ComboBox<>(QUERY_TYPES)));

        // 定制空态说明（替代 Nothing to show）
        StatusText serviceEmptyText = serviceQueryTable.getEmptyText();
        serviceEmptyText.setText("尚未添加查询条件", new com.intellij.ui.SimpleTextAttributes(
                com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN, SolidGlassTheme.textSecondary()));
        serviceEmptyText.appendSecondaryText("可配置模糊查询、in、between 等 QueryWrapper 条件", new com.intellij.ui.SimpleTextAttributes(
                com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN, SolidGlassTheme.textMuted()), null);

        JPanel serviceBtnBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        serviceBtnBar.setOpaque(false);
        JBLabel serviceTitle = new JBLabel("ServiceQuery 查询条件");
        serviceTitle.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(12f)));
        serviceTitle.setForeground(SolidGlassTheme.textPrimary());
        serviceBtnBar.add(serviceTitle);

        GlassButton addServiceRowBtn = new GlassButton("+ 新增查询条件");
        addServiceRowBtn.addActionListener(e -> serviceQueryTableModel.addRow(
                new Object[]{"0:等于", "", "", Boolean.TRUE, "", "", ""}
        ));
        GlassButton removeServiceRowBtn = new GlassButton("- 删除选中");
        removeServiceRowBtn.addActionListener(e -> removeSelectedRows(serviceQueryTable, serviceQueryTableModel));
        GlassButton clearServiceBtn = new GlassButton("清空");
        clearServiceBtn.addActionListener(e -> serviceQueryTableModel.setRowCount(0));

        serviceBtnBar.add(addServiceRowBtn);
        serviceBtnBar.add(removeServiceRowBtn);
        serviceBtnBar.add(clearServiceBtn);

        JBScrollPane serviceScroll = new JBScrollPane(serviceQueryTable);
        SolidGlassTheme.styleScrollPane(serviceScroll);
        serviceCard.add(serviceBtnBar, BorderLayout.NORTH);
        serviceCard.add(serviceScroll, BorderLayout.CENTER);

        tablesGrid.add(beanCard);
        tablesGrid.add(serviceCard);

        outer.add(targetBar, BorderLayout.NORTH);
        outer.add(tablesGrid, BorderLayout.CENTER);
        return outer;
    }

    /**
     * 页签三：数据源连接设置（来源类型与连接验证结果明确分开，绿色仅用于验证成功）
     */
    private JComponent createDatabaseConfigPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 8));
        wrapper.setOpaque(false);
        wrapper.setBorder(JBUI.Borders.empty(4, 0, 0, 0));

        styleTextField(jdbcUrlField);
        styleTextField(databaseField);
        styleTextField(usernameField);
        passwordField.setBackground(SolidGlassTheme.inputBg());
        passwordField.setForeground(SolidGlassTheme.textPrimary());

        // 当用户修改连接参数时，将连接验证状态重置为「未验证连接」
        DocumentAdapter resetVerifyListener = new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                databaseConfigVersion++;
                dbConnectionVerified = false;
                lastVerifiedDbTableCount = 0;
                resolvedTableColumnsCache.clear();
                dbPageStatusBadge.setStatus("连接状态：未验证，请重新加载表清单", GlyphState.IDLE);
                updateSummaryAndSourceIndicators();
                refreshFileImpactPreview();
            }
        };
        jdbcUrlField.getDocument().addDocumentListener(resetVerifyListener);
        databaseField.getDocument().addDocumentListener(resetVerifyListener);
        usernameField.getDocument().addDocumentListener(resetVerifyListener);
        passwordField.getDocument().addDocumentListener(resetVerifyListener);

        JPanel form = FormBuilder.createFormBuilder()
                .addLabeledComponent("JDBC URL (支持 MySQL/PG/Oracle/DM):", jdbcUrlField)
                .addLabeledComponent("数据库 / Schema 名 (Database):", databaseField)
                .addLabeledComponent("用户名 (Username):", usernameField)
                .addLabeledComponent("密码 (Password):", passwordField)
                .getPanel();
        form.setOpaque(false);

        JPanel actionRow = new JPanel(new BorderLayout(8, 0));
        actionRow.setOpaque(false);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        btnBar.setOpaque(false);

        GlassButton testConnBtn = new GlassButton("测试连接并加载表清单", true);
        testConnBtn.addActionListener(e -> fetchDatabaseTablesAsync());

        GlassButton scanSpringBtn = new GlassButton("从 Spring 配置识别数据源");
        scanSpringBtn.addActionListener(e -> {
            boolean imported = settingsService.importFromSpringConfig(projectRootPath, true);
            if (imported && settingsService.getState() != null) {
                CodeGenSettingsState.ConfigState st = settingsService.getState();
                jdbcUrlField.setText(st.jdbcUrl);
                databaseField.setText(st.database);
                usernameField.setText(st.username);
                passwordField.setText(st.retrieveCredential());
                currentSourceMode = DataSourceMode.DATABASE;
                dbConnectionVerified = false;
                dbPageStatusBadge.setStatus("已读取配置（未验证连接）", GlyphState.IDLE);
                updateSummaryAndSourceIndicators();
            } else {
                Messages.showWarningDialog(project, "未在 src/main/resources/application* 中检测到数据源配置。", "提示");
            }
        });

        GlassButton importPropBtn = new GlassButton("从 mysql.properties 导入");
        importPropBtn.addActionListener(e -> {
            boolean imported = settingsService.importFromProjectProperties(projectRootPath, true);
            if (imported && settingsService.getState() != null) {
                CodeGenSettingsState.ConfigState st = settingsService.getState();
                jdbcUrlField.setText(st.jdbcUrl);
                databaseField.setText(st.database);
                usernameField.setText(st.username);
                passwordField.setText(st.retrieveCredential());
                packageField.setText(st.packageName);
                currentSourceMode = DataSourceMode.DATABASE;
                dbConnectionVerified = false;
                dbPageStatusBadge.setStatus("已导入配置（未验证连接）", GlyphState.IDLE);
                updateSummaryAndSourceIndicators();
            } else {
                Messages.showWarningDialog(project, "未在当前项目下找到可导入的配置文件。", "提示");
            }
        });

        btnBar.add(testConnBtn);
        btnBar.add(scanSpringBtn);
        btnBar.add(importPropBtn);

        actionRow.add(btnBar, BorderLayout.WEST);
        actionRow.add(dbPageStatusBadge, BorderLayout.EAST);

        SolidGlassPanel dbCard = new SolidGlassPanel(new BorderLayout(0, 10), Thickness.MEDIUM);
        dbCard.add(form, BorderLayout.NORTH);
        dbCard.add(actionRow, BorderLayout.CENTER);

        wrapper.add(dbCard, BorderLayout.NORTH);
        return wrapper;
    }

    /**
     * 页签四：离线 DDL SQL 解析面板
     * - 拆开「清空 SQL 内容」与「切换为数据库连接」两个操作
     * - 将解析结果收敛为底部单行摘要条，把主要空间全部留给带克制语法高亮的 SQL 编辑区
     */
    private JComponent createOfflineDdlPanel() {
        SolidGlassPanel ddlCard = new SolidGlassPanel(new BorderLayout(0, 8), Thickness.MEDIUM);
        ddlSqlPane.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { ddlTextChanged(); }
            @Override
            public void removeUpdate(DocumentEvent e) { ddlTextChanged(); }
            @Override
            public void changedUpdate(DocumentEvent e) { }
        });

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftActions.setOpaque(false);

        GlassButton parseBtn = new GlassButton("解析 DDL 并应用到选表", true);
        parseBtn.addActionListener(e -> {
            parseDdlAndPopulateTables(true);
            if (!parsedDdlTables.isEmpty() && mainTabbedPane != null) {
                mainTabbedPane.setSelectedIndex(0);
            }
        });

        GlassButton loadSampleBtn = new GlassButton("加载示例 SQL");
        loadSampleBtn.addActionListener(e -> {
            ddlSqlPane.setText(SAMPLE_DDL);
            parseDdlAndPopulateTables(false);
        });

        // 独立操作 1：仅清空编辑器内容与 DDL 解析缓存
        GlassButton clearSqlOnlyBtn = new GlassButton("清空 SQL 内容");
        clearSqlOnlyBtn.addActionListener(e -> {
            ddlSqlPane.setText("");
            parsedDdlTables.clear();
            lastParsedDdlScript = null;
            if (currentSourceMode == DataSourceMode.OFFLINE_DDL) {
                masterTableMap.clear();
                resolvedTableColumnsCache.clear();
                activePreviewTableName = "";
                filterTableList(tableSearchField.getText());
                syncCheckedTablesToSummaryField();
                reloadQueryTablesForCurrentTable();
            }
            ddlSingleLineSummaryBadge.setStatus("已清空 SQL 内容（待输入 CREATE TABLE 语句）", GlyphState.IDLE);
            updateSummaryAndSourceIndicators();
            refreshFileImpactPreview();
        });

        leftActions.add(parseBtn);
        leftActions.add(loadSampleBtn);
        leftActions.add(clearSqlOnlyBtn);

        // 独立操作 2：切换来源为数据库连接
        GlassButton switchToDbModeBtn = new GlassButton("切换为数据库连接来源");
        switchToDbModeBtn.addActionListener(e -> {
            databaseConfigVersion++;
            currentSourceMode = DataSourceMode.DATABASE;
            updateSummaryAndSourceIndicators();
            if (mainTabbedPane != null) {
                mainTabbedPane.setSelectedIndex(2);
            }
        });

        topBar.add(leftActions, BorderLayout.WEST);
        topBar.add(switchToDbModeBtn, BorderLayout.EAST);

        // 中部主空间：带克制语法高亮的 SQL 编辑器（占据全部主要空间，无横向分隔条）
        JBScrollPane sqlScroll = new JBScrollPane(ddlSqlPane);
        SolidGlassTheme.styleScrollPane(sqlScroll);

        // 底部单行解析摘要栏（例如：解析成功 · 1 张表 · 7 个字段）
        JPanel bottomSummaryRow = new JPanel(new BorderLayout(8, 0));
        bottomSummaryRow.setOpaque(false);
        bottomSummaryRow.add(ddlSingleLineSummaryBadge, BorderLayout.WEST);
        bottomSummaryRow.add(SolidGlassTheme.createHelperLabel("支持 MySQL / PostgreSQL / Oracle 建表语法，自动提取非空约束、长度与枚举注释"), BorderLayout.EAST);

        ddlCard.add(topBar, BorderLayout.NORTH);
        ddlCard.add(sqlScroll, BorderLayout.CENTER);
        ddlCard.add(bottomSummaryRow, BorderLayout.SOUTH);

        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(false);
        outer.setBorder(JBUI.Borders.empty(4, 0, 0, 0));
        outer.add(ddlCard, BorderLayout.CENTER);
        return outer;
    }

    private void updateWriteStrategyHint() {
        int idx = writeStrategyCombo.getSelectedIndex();
        if (idx == 0) {
            writeStrategyHintLabel.setText("保留已有类中的手写方法与自定义逻辑，仅追加新增字段与缺失 import");
        } else if (idx == 1) {
            writeStrategyHintLabel.setText("生成后先弹出左右分栏 Diff 预览窗口，确认后再写入磁盘");
        } else {
            writeStrategyHintLabel.setText("直接用新生成的代码替换同名文件（已有手写修改将被覆盖）");
        }
    }

    @Nullable
    private List<String> getResolvedEnumClassNamesForTable(String tableName) {
        if (StringUtils.isBlank(tableName)) {
            return null;
        }
        List<Table> cols = getResolvedColumnsForTable(tableName);
        if (cols == null) {
            return null;
        }
        String fixedEnumCol = fixedEnumField.getText().trim();
        String className = CodeGenerateUtils.toUpper(tableName);
        List<String> enumFiles = new ArrayList<>();
        for (Table col : cols) {
            if (StringUtils.isNotBlank(fixedEnumCol) && !Objects.equals(col.getTypeName(), fixedEnumCol)) {
                continue;
            }
            List<EnumsEntityContent> items = EnumCommentParser.parseEnumItems(col.getColumnComment());
            if (!items.isEmpty()) {
                enumFiles.add(className + col.getColumnNameUpper() + "Enum.java");
            }
        }
        return enumFiles;
    }

    /**
     * 核心状态更新：
     * 1. 分离更新顶栏「来源类型 SourceBadge」与「连接/解析验证状态 StatusGlyph」
     * 2. 当 0 张表勾选或 0 个生成选项勾选时，底部严格显示「待生成：0 张表 · 0 个文件」，并同步禁用「开始生成」主按钮
     */
    private List<String> missingOfflineTables() {
        return getCheckedTableNames().stream()
                .filter(table -> parsedDdlTables.keySet().stream().noneMatch(table::equalsIgnoreCase))
                .collect(Collectors.toList());
    }

    private boolean ddlNeedsParsing() {
        return parsedDdlTables.isEmpty() || lastParsedDdlScript == null
                || !lastParsedDdlScript.equals(ddlSqlPane.getText());
    }

    private void ddlTextChanged() {
        if (ddlNeedsParsing()) {
            ddlSingleLineSummaryBadge.setStatus("SQL 已修改，请重新解析", GlyphState.WARNING);
        } else {
            ddlSingleLineSummaryBadge.setStatus("解析结果已同步 · " + parsedDdlTables.size() + " 张表", GlyphState.READY);
        }
        updateSummaryAndSourceIndicators();
        refreshFileImpactPreview();
    }

    private void updateSummaryAndSourceIndicators() {
        List<String> checkedTables = getCheckedTableNames();
        int tableCount = checkedTables.size();

        // 1. 分离显示「来源类型（中性）」与「验证结果（仅成功时用绿色）」
        loadTablesBtn.setText(currentSourceMode == DataSourceMode.OFFLINE_DDL
                ? "切换并加载数据库表" : "加载表清单");
        loadTablesBtn.setToolTipText("从数据库读取表清单；成功后替换当前来源和选表列表");
        if (currentSourceMode == DataSourceMode.OFFLINE_DDL) {
            sourceBadge.setSourceText("来源：离线 DDL");
            if (ddlNeedsParsing()) {
                verificationBadge.setStatus("DDL 待重新解析", GlyphState.WARNING);
            } else if (!parsedDdlTables.isEmpty()) {
                int totalCols = parsedDdlTables.values().stream().mapToInt(t -> t.getColumns().size()).sum();
                verificationBadge.setStatus("解析成功 (" + parsedDdlTables.size() + " 张表 · " + totalCols + " 个字段)", GlyphState.READY);
            } else {
                verificationBadge.setStatus("待解析 DDL", GlyphState.IDLE);
            }
        } else {
            String dbName = StringUtils.defaultIfBlank(databaseField.getText().trim(), "未填库名");
            sourceBadge.setSourceText("来源：数据库连接 (" + dbName + ")");
            if (dbConnectionVerified) {
                verificationBadge.setStatus("连接成功 (" + lastVerifiedDbTableCount + " 张表)", GlyphState.READY);
            } else {
                verificationBadge.setStatus("未验证连接", GlyphState.IDLE);
            }
        }

        // 2. 统计已选生成选项数与单表基础文件数
        int selectedOptions = 0;
        int baseFilesPerTable = 0;
        boolean enumChecked = false;
        for (Map.Entry<String, JBCheckBox> entry : targetCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selectedOptions++;
                if ("enums".equals(entry.getKey())) {
                    enumChecked = true;
                } else if ("service".equals(entry.getKey())) {
                    baseFilesPerTable += 2;
                } else {
                    baseFilesPerTable += 1;
                }
            }
        }

        // 中间选项标题：说明当前选项配置
        if (!enumChecked) {
            targetSelectionCountLabel.setText("生成选项（已选 " + selectedOptions + " / 10 项 · 单表固定产出 " + baseFilesPerTable + " 个文件）");
        } else {
            targetSelectionCountLabel.setText("生成选项（已选 " + selectedOptions + " / 10 项 · 单表 " + baseFilesPerTable + " 个基础文件 + 按枚举列注释生成枚举）");
        }

        // 3. 底部「待生成」总量统计与「开始生成」主按钮启用/禁用控制
        boolean sourceReady = currentSourceMode == DataSourceMode.OFFLINE_DDL
                ? !ddlNeedsParsing() && missingOfflineTables().isEmpty()
                : dbConnectionVerified && parsedDdlTables.isEmpty();
        boolean canExecute = tableCount > 0 && selectedOptions > 0 && sourceReady;
        setOKActionEnabled(canExecute);

        String strategyShortName = (String) writeStrategyCombo.getSelectedItem();
        if (tableCount == 0) {
            footerSummaryLabel.setText("待生成：0 张表 · 0 个文件（请先在左侧列表勾选参与生成的表）");
            footerSummaryLabel.setForeground(SolidGlassTheme.warningFg());
            getOKAction().putValue(Action.SHORT_DESCRIPTION, "请先在下方列表勾选至少 1 张参与生成的表");
        } else if (selectedOptions == 0) {
            footerSummaryLabel.setText("待生成：" + tableCount + " 张表 · 0 个文件（请至少勾选 1 个生成选项）");
            footerSummaryLabel.setForeground(SolidGlassTheme.warningFg());
            getOKAction().putValue(Action.SHORT_DESCRIPTION, "请至少勾选 1 个生成选项");
        } else if (!sourceReady) {
            String reason = currentSourceMode == DataSourceMode.OFFLINE_DDL
                    ? "请解析所有勾选表的 DDL 后生成" : "请验证连接并重新加载表清单";
            footerSummaryLabel.setText("待生成：" + tableCount + " 张表 · " + reason);
            footerSummaryLabel.setForeground(SolidGlassTheme.warningFg());
            getOKAction().putValue(Action.SHORT_DESCRIPTION, reason);
        } else {
            footerSummaryLabel.setForeground(SolidGlassTheme.textPrimary());
            getOKAction().putValue(Action.SHORT_DESCRIPTION, "对已勾选的 " + tableCount + " 张表执行代码生成");

            boolean allCheckedResolved = checkedTables.stream().allMatch(t -> getResolvedEnumClassNamesForTable(t) != null);
            String fileCountDesc;
            if (!enumChecked) {
                fileCountDesc = (tableCount * baseFilesPerTable) + " 个文件";
            } else if (allCheckedResolved) {
                int totalExact = 0;
                int totalEnums = 0;
                for (String t : checkedTables) {
                    int eCount = getResolvedEnumClassNamesForTable(t).size();
                    totalEnums += eCount;
                    totalExact += baseFilesPerTable + eCount;
                }
                fileCountDesc = "计划生成 " + totalExact + " 个文件（" + (tableCount * baseFilesPerTable) + " 个基础 + " + totalEnums + " 个枚举）";
            } else {
                fileCountDesc = "预计 " + (tableCount * baseFilesPerTable) + " 个基础文件（另含条件枚举）";
            }
            footerSummaryLabel.setText("待生成：" + tableCount + " 张表 · " + fileCountDesc + " · " + strategyShortName);
        }

        String relOutDir = computeRelativeOutputDir();
        outputDirReadOnlyField.setText(relOutDir);
        footerOutputPathLabel.setText("输出目录：" + relOutDir);
    }

    private String computeRelativeOutputDir() {
        String basePkg = StringUtils.defaultIfBlank(packageField.getText().trim(), "com.example");
        String mod = moduleField.getText().trim();
        String fullPkg = StringUtils.isNotBlank(mod) ? (basePkg + "." + mod).toLowerCase(Locale.ROOT) : basePkg.toLowerCase(Locale.ROOT);
        return "src/main/java/" + fullPkg.replace('.', '/');
    }

    /**
     * 刷新右侧生成文件预览：将当前预览表的具体文件数（如 12 个文件）保留在预览区标题与底部摘要，不与未勾选时的底部 0 文件冲突
     */
    private void refreshFileImpactPreview() {
        if (filePreviewTableModel == null) {
            return;
        }
        filePreviewTableModel.setRowCount(0);

        List<String> checked = getCheckedTableNames();
        String previewTable = activePreviewTableName;
        if (StringUtils.isBlank(previewTable) && !checked.isEmpty()) {
            previewTable = checked.get(0);
            activePreviewTableName = previewTable;
        }

        if (StringUtils.isBlank(previewTable)) {
            rightPreviewTitleLabel.setText("生成文件预览");
            rightPreviewTitleLabel.setToolTipText(null);
            rightPreviewDetailsLabel.setText("未选择预览表");
            fieldParseSummaryLabel.setText("请在左侧加载或添加生成表，点击任意行即可预览该表对应的生成文件清单。");
            return;
        }

        if (currentSourceMode == DataSourceMode.DATABASE && !dbConnectionVerified) {
            rightPreviewTitleLabel.setText("生成文件预览 · " + previewTable);
            rightPreviewTitleLabel.setToolTipText(previewTable);
            rightPreviewDetailsLabel.setText("连接未验证，请重新加载表清单");
            fieldParseSummaryLabel.setText("字段解析：旧数据库元数据已失效");
            return;
        }

        String relOutDir = computeRelativeOutputDir();
        outputDirReadOnlyField.setText(relOutDir);
        String absBaseDir = projectRootPath + relOutDir;
        String className = CodeGenerateUtils.toUpper(previewTable);
        String strategyCode = getSelectedWriteStrategyCode();

        Map<String, String[]> fixedLayerFiles = new LinkedHashMap<>();
        fixedLayerFiles.put("entity", new String[]{"entity/" + className + ".java"});
        fixedLayerFiles.put("entityBO", new String[]{"entity/bo/" + className + "BO.java"});
        fixedLayerFiles.put("entityVO", new String[]{"entity/vo/" + className + "VO.java"});
        fixedLayerFiles.put("entityQuery", new String[]{"entity/query/" + className + "Query.java"});
        fixedLayerFiles.put("mapper", new String[]{"mapper/" + className + "Mapper.java"});
        fixedLayerFiles.put("service", new String[]{
                "service/" + className + "Service.java",
                "service/impl/" + className + "ServiceImpl.java"
        });
        fixedLayerFiles.put("serviceQuery", new String[]{"service/query/" + className + "ServiceQuery.java"});
        fixedLayerFiles.put("serviceResults", new String[]{"service/results/" + className + "ServiceResults.java"});
        fixedLayerFiles.put("controller", new String[]{"controller/" + className + "Controller.java"});

        int previewFileCount = 0;
        for (Map.Entry<String, String[]> entry : fixedLayerFiles.entrySet()) {
            JBCheckBox cb = targetCheckBoxes.get(entry.getKey());
            if (cb != null && cb.isSelected()) {
                for (String relPath : entry.getValue()) {
                    previewFileCount++;
                    File diskFile = new File(absBaseDir, relPath);
                    String actionStatus = evaluateDiskFileActionStatus(diskFile, strategyCode);
                    filePreviewTableModel.addRow(new Object[]{relPath, actionStatus});
                }
            }
        }

        JBCheckBox enumCb = targetCheckBoxes.get("enums");
        List<String> resolvedEnums = getResolvedEnumClassNamesForTable(previewTable);
        if (enumCb != null && enumCb.isSelected()) {
            if (resolvedEnums == null) {
                filePreviewTableModel.addRow(new Object[]{
                        "entity/enums/" + className + "*Enum.java",
                        "待解析（点击右上角刷新确认枚举列）"
                });
            } else if (resolvedEnums.isEmpty()) {
                filePreviewTableModel.addRow(new Object[]{
                        "entity/enums/（无枚举注释列）",
                        "跳过（无枚举列）"
                });
            } else {
                for (String enumFileName : resolvedEnums) {
                    previewFileCount++;
                    String relPath = "entity/enums/" + enumFileName;
                    File diskFile = new File(absBaseDir, relPath);
                    String actionStatus = evaluateDiskFileActionStatus(diskFile, strategyCode);
                    filePreviewTableModel.addRow(new Object[]{relPath, actionStatus});
                }
            }
        }

        boolean isCheckedForGen = checked.stream().anyMatch(previewTable::equalsIgnoreCase);
        String checkStateTag = isCheckedForGen ? "参与生成" : "仅预览，未勾选";
        String fileCountTag = (resolvedEnums != null || (enumCb != null && !enumCb.isSelected()))
                ? (" · 计划生成 " + previewFileCount + " 个文件")
                : (" · 预计 " + previewFileCount + " 个基础文件，枚举待解析");
        rightPreviewTitleLabel.setText("生成文件预览 · " + previewTable);
        rightPreviewTitleLabel.setToolTipText(previewTable);
        rightPreviewDetailsLabel.setText(currentSourceMode == DataSourceMode.OFFLINE_DDL && ddlNeedsParsing()
                ? "SQL 已修改，以下为上次解析结果" : checkStateTag + fileCountTag);

        List<Table> cols = getResolvedColumnsForTable(previewTable);
        if (cols != null) {
            ValidationAnnotationResolver.enrichValidationAnnotations(cols, enableValidationCheckBox.isSelected());
            long valCount = cols.stream().filter(c -> c.getValidationAnnotations() != null && !c.getValidationAnnotations().isEmpty()).count();
            int enumCount = resolvedEnums != null ? resolvedEnums.size() : 0;
            fieldParseSummaryLabel.setText("预览表 [" + previewTable + "] 字段统计：共 " + cols.size() + " 列 · 识别 "
                    + enumCount + " 个枚举列 · " + valCount + " 列含校验注解");
        } else {
            fieldParseSummaryLabel.setText("预览表 [" + previewTable + "] 字段统计：尚未读取列元数据，点击右上角「刷新预览」可精确计算枚举文件");
        }
    }

    @Nullable
    private List<Table> getResolvedColumnsForTable(String tableName) {
        if (StringUtils.isBlank(tableName)) {
            return null;
        }
        if (currentSourceMode == DataSourceMode.OFFLINE_DDL) {
            for (DdlSqlParser.ParsedTable parsed : parsedDdlTables.values()) {
                if (parsed.getTableName().equalsIgnoreCase(tableName)) {
                    return parsed.getColumns();
                }
            }
            return null;
        }
        return resolvedTableColumnsCache.get(tableName.toLowerCase(Locale.ROOT));
    }

    private String evaluateDiskFileActionStatus(File diskFile, String strategyCode) {
        if (!diskFile.exists()) {
            return "新增";
        }
        if (IncrementalCodeMerger.WriteStrategy.DIFF_PREVIEW.name().equalsIgnoreCase(strategyCode)) {
            return "冲突（待 Diff 确认）";
        } else if (IncrementalCodeMerger.WriteStrategy.OVERWRITE.name().equalsIgnoreCase(strategyCode)) {
            return "覆盖（替换已有）";
        } else {
            return "合并（保留手写代码）";
        }
    }

    private void populateMasterTablesFromDbList(List<String> rawDbTableStrings) {
        Set<String> previouslyChecked = new HashSet<>(getCheckedTableNames());
        masterTableMap.clear();
        for (String itemStr : rawDbTableStrings) {
            String tName = itemStr.contains(" ") ? itemStr.substring(0, itemStr.indexOf(" ")).trim() : itemStr.trim();
            String comment = "";
            int openIdx = itemStr.indexOf('(');
            int closeIdx = itemStr.lastIndexOf(')');
            if (openIdx > 0 && closeIdx > openIdx) {
                comment = itemStr.substring(openIdx + 1, closeIdx).trim();
            }
            boolean check = previouslyChecked.contains(tName);
            masterTableMap.put(tName, new TableItem(tName, comment, check));
        }
        currentSourceMode = DataSourceMode.DATABASE;
        resolvedTableColumnsCache.clear();
        dbConnectionVerified = true;
        lastVerifiedDbTableCount = rawDbTableStrings.size();
        parsedDdlTables.clear();
        if (!masterTableMap.containsKey(activePreviewTableName)) {
            activePreviewTableName = masterTableMap.isEmpty() ? "" : masterTableMap.keySet().iterator().next();
        }
        filterTableList(tableSearchField.getText());
        syncCheckedTablesToSummaryField();
        reloadQueryTablesForCurrentTable();
    }

    private void parseDdlAndPopulateTables(boolean showDialog) {
        String script = ddlSqlPane.getText();
        Map<String, DdlSqlParser.ParsedTable> result = DdlSqlParser.parseDdlScript(script);
        parsedDdlTables.clear();
        parsedDdlTables.putAll(result);

        if (result.isEmpty()) {
            ddlSingleLineSummaryBadge.setStatus("解析失败：未识别到有效的 CREATE TABLE 语句", GlyphState.ERROR);
            if (showDialog) {
                Messages.showWarningDialog(project, "未识别到有效的 CREATE TABLE 语句！", "DDL 解析提示");
            }
            updateSummaryAndSourceIndicators();
            return;
        }

        databaseConfigVersion++;
        currentSourceMode = DataSourceMode.OFFLINE_DDL;
        lastParsedDdlScript = script;
        masterTableMap.clear();
        int totalCols = 0;
        int totalEnums = 0;
        for (DdlSqlParser.ParsedTable pt : result.values()) {
            masterTableMap.put(pt.getTableName(), new TableItem(pt.getTableName(), pt.getTableComment(), true));
            resolvedTableColumnsCache.put(pt.getTableName().toLowerCase(Locale.ROOT), pt.getColumns());
            totalCols += pt.getColumns().size();
            for (Table col : pt.getColumns()) {
                if (!EnumCommentParser.parseEnumItems(col.getColumnComment()).isEmpty()) {
                    totalEnums++;
                }
            }
        }

        ddlSingleLineSummaryBadge.setStatus(
                "解析成功 · " + result.size() + " 张表 · " + totalCols + " 个字段 · " + totalEnums + " 个枚举列",
                GlyphState.READY
        );

        activePreviewTableName = result.keySet().iterator().next();
        filterTableList(tableSearchField.getText());
        syncCheckedTablesToSummaryField();
        reloadQueryTablesForCurrentTable();
        refreshFileImpactPreview();
    }

    private void registerTargetCheckBox(JPanel parent, String key, String label) {
        JBCheckBox checkBox = new JBCheckBox(label, false);
        checkBox.setOpaque(false);
        checkBox.setForeground(SolidGlassTheme.textPrimary());
        checkBox.addActionListener(e -> {
            updateSummaryAndSourceIndicators();
            refreshFileImpactPreview();
        });
        targetCheckBoxes.put(key, checkBox);
        parent.add(checkBox);
    }

    private void removeSelectedRows(JBTable table, DefaultTableModel model) {
        int[] selectedRows = table.getSelectedRows();
        for (int i = selectedRows.length - 1; i >= 0; i--) {
            model.removeRow(selectedRows[i]);
        }
    }

    private void filterTableList(String keyword) {
        tableListModel.clear();
        String kw = StringUtils.trimToEmpty(keyword).toLowerCase(Locale.ROOT);
        for (TableItem item : masterTableMap.values()) {
            if (kw.isEmpty() || item.getDisplayLabel().toLowerCase(Locale.ROOT).contains(kw)) {
                tableListModel.addElement(item);
            }
        }
    }

    private void fetchDatabaseTablesAsync() {
        syncDbFieldsToState();
        int requestVersion = ++databaseConfigVersion;
        DataSourceMode sourceAtStart = currentSourceMode;
        String databaseName = databaseField.getText().trim();
        dbConnectionVerified = false;
        resolvedTableColumnsCache.clear();
        updateSummaryAndSourceIndicators();
        refreshFileImpactPreview();
        dbPageStatusBadge.setStatus("正在连接数据库...", GlyphState.WORKING);
        verificationBadge.setStatus("正在连接数据库...", GlyphState.WORKING);
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Loading Database Tables...", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try (Connection conn = CodeGenerateUtils.getDblink(project, projectRootPath)) {
                    List<String> tables = CodeGenerateUtils.getAllTablesWithComments(conn, databaseName);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (requestVersion != databaseConfigVersion || currentSourceMode != sourceAtStart) return;
                        dbPageStatusBadge.setStatus("连接成功（共 " + tables.size() + " 张表）", GlyphState.READY);
                        populateMasterTablesFromDbList(tables);
                    });
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (requestVersion != databaseConfigVersion || currentSourceMode != sourceAtStart) return;
                        dbConnectionVerified = false;
                        updateSummaryAndSourceIndicators();
                        refreshFileImpactPreview();
                        if (currentSourceMode == DataSourceMode.DATABASE) verificationBadge.setStatus("连接失败", GlyphState.ERROR);
                        dbPageStatusBadge.setStatus("连接失败", GlyphState.ERROR);
                        Messages.showErrorDialog(
                                project,
                                "加载数据库表失败，请检查「数据源连接」页签或切换至「离线 DDL 解析」：\n" + ex.getMessage(),
                                "加载失败"
                        );
                    });
                }
            }
        });
    }

    private void previewSelectedTableEnumsAsync() {
        List<String> checked = getCheckedTableNames();
        String targetTable = StringUtils.defaultIfBlank(activePreviewTableName, checked.isEmpty() ? "" : checked.get(0));
        if (StringUtils.isBlank(targetTable)) {
            refreshFileImpactPreview();
            return;
        }
        if (currentSourceMode == DataSourceMode.OFFLINE_DDL) {
            updateSummaryAndSourceIndicators();
            refreshFileImpactPreview();
            return;
        }

        if (!dbConnectionVerified) {
            refreshFileImpactPreview();
            return;
        }
        syncDbFieldsToState();
        int requestVersion = databaseConfigVersion;
        String databaseName = databaseField.getText().trim();
        verificationBadge.setStatus("正在读取表字段：" + targetTable, GlyphState.WORKING);
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Parsing Table Metadata...", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try (Connection conn = CodeGenerateUtils.getDblink(project, projectRootPath)) {
                    List<Table> columns = CodeGenerateUtils.getList(conn, targetTable, databaseName);
                    ValidationAnnotationResolver.enrichValidationAnnotations(columns, enableValidationCheckBox.isSelected());
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (requestVersion != databaseConfigVersion || currentSourceMode != DataSourceMode.DATABASE) return;
                        resolvedTableColumnsCache.put(targetTable.toLowerCase(Locale.ROOT), columns);
                        updateSummaryAndSourceIndicators();
                        refreshFileImpactPreview();
                    });
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (requestVersion != databaseConfigVersion || currentSourceMode != DataSourceMode.DATABASE) return;
                        verificationBadge.setStatus("读取字段失败", GlyphState.ERROR);
                        refreshFileImpactPreview();
                    });
                }
            }
        });
    }

    private void reloadQueryTablesForCurrentTable() {
        if (beanQueryTableModel == null || serviceQueryTableModel == null) {
            return;
        }
        List<String> checked = getCheckedTableNames();
        String tableKey = StringUtils.defaultIfBlank(activePreviewTableName, checked.isEmpty() ? "" : checked.get(0));

        if (tableKey.equals(loadedQueryTableName)) {
            return;
        }
        saveCurrentQueryDraft();
        loadedQueryTableName = tableKey;
        if (StringUtils.isBlank(tableKey)) {
            queryPageTargetTableLabel.setText("规则作用于：未选择表（请先在「生成配置与选表」页选择表）");
            if (reloadQueryCacheBtn != null) {
                reloadQueryCacheBtn.setText("重载当前表缓存");
            }
            beanQueryTableModel.setRowCount(0);
            serviceQueryTableModel.setRowCount(0);
            return;
        }

        queryPageTargetTableLabel.setText("规则作用于：" + tableKey);
        if (reloadQueryCacheBtn != null) {
            reloadQueryCacheBtn.setText("重载 [" + tableKey + "] 缓存");
        }

        beanQueryTableModel.setRowCount(0);
        serviceQueryTableModel.setRowCount(0);

        List<BeanQuery> beanQueries = beanQueryDrafts.containsKey(tableKey)
                ? beanQueryDrafts.get(tableKey) : settingsService.getBeanQueryList(tableKey, projectRootPath);
        for (BeanQuery bq : beanQueries) {
            beanQueryTableModel.addRow(new Object[]{
                    StringUtils.defaultString(bq.getName()),
                    StringUtils.defaultIfBlank(bq.getFormat(), "String"),
                    StringUtils.defaultString(bq.getDescription())
            });
        }

        List<ServiceQuery> serviceQueries = serviceQueryDrafts.containsKey(tableKey)
                ? serviceQueryDrafts.get(tableKey) : settingsService.getServiceQueryList(tableKey, projectRootPath);
        for (ServiceQuery sq : serviceQueries) {
            int typeIdx = 0;
            try {
                typeIdx = Integer.parseInt(StringUtils.defaultIfBlank(sq.getType(), "0"));
            } catch (NumberFormatException ignored) {
            }
            String typeLabel = (typeIdx >= 0 && typeIdx < QUERY_TYPES.length) ? QUERY_TYPES[typeIdx] : QUERY_TYPES[0];
            String rawCol = StringUtils.unwrap(StringUtils.defaultString(sq.getColumn()), '`');
            String rawSql = StringUtils.unwrap(StringUtils.defaultString(sq.getSql()), '"');
            serviceQueryTableModel.addRow(new Object[]{
                    typeLabel,
                    rawCol,
                    StringUtils.defaultString(sq.getParameter()),
                    Boolean.TRUE.equals(sq.getParameterType()),
                    StringUtils.defaultString(sq.getStart()),
                    StringUtils.defaultString(sq.getEnd()),
                    rawSql
            });
        }
    }

    private void syncDbFieldsToState() {
        CodeGenSettingsState.ConfigState state = settingsService.getState();
        if (state != null) {
            state.jdbcUrl = jdbcUrlField.getText().trim();
            state.database = databaseField.getText().trim();
            state.username = usernameField.getText().trim();
            state.storeCredential(new String(passwordField.getPassword()));
            state.packageName = packageField.getText().trim();
            state.author = authorField.getText().trim();
            state.lastModuleName = moduleField.getText().trim();
            state.lastTableNames = selectedTablesReadOnlyField.getText().trim();
            state.writeStrategy = getSelectedWriteStrategyCode();
            state.docStyle = getSelectedDocStyleCode();
            state.enableValidation = enableValidationCheckBox.isSelected();
            state.lastDdlScript = ddlSqlPane.getText();
        }
    }

    private String getSelectedWriteStrategyCode() {
        int idx = writeStrategyCombo.getSelectedIndex();
        return (idx >= 0 && idx < WRITE_STRATEGY_CODES.length) ? WRITE_STRATEGY_CODES[idx] : WRITE_STRATEGY_CODES[0];
    }

    private void selectStrategyComboByCode(String code) {
        for (int i = 0; i < WRITE_STRATEGY_CODES.length; i++) {
            if (WRITE_STRATEGY_CODES[i].equalsIgnoreCase(code)) {
                writeStrategyCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    private String getSelectedDocStyleCode() {
        int idx = docStyleCombo.getSelectedIndex();
        return (idx >= 0 && idx < DOC_STYLE_CODES.length) ? DOC_STYLE_CODES[idx] : DOC_STYLE_CODES[0];
    }

    private void selectDocStyleComboByCode(String code) {
        for (int i = 0; i < DOC_STYLE_CODES.length; i++) {
            if (DOC_STYLE_CODES[i].equalsIgnoreCase(code)) {
                docStyleCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
        if (currentSourceMode == DataSourceMode.OFFLINE_DDL && ddlNeedsParsing()) {
            return new ValidationInfo("SQL 已修改，请先重新解析 DDL", ddlSqlPane);
        }
        if (currentSourceMode == DataSourceMode.OFFLINE_DDL && !missingOfflineTables().isEmpty()) {
            return new ValidationInfo("离线 DDL 未包含选中表：" + String.join(", ", missingOfflineTables()), selectedTablesReadOnlyField);
        }
        if (currentSourceMode == DataSourceMode.DATABASE && (!dbConnectionVerified || !parsedDdlTables.isEmpty())) {
            return new ValidationInfo("请验证连接并重新加载表清单", tableSearchField);
        }
        if (StringUtils.isBlank(authorField.getText())) {
            return new ValidationInfo("请输入作者名称", authorField);
        }
        if (StringUtils.isBlank(moduleField.getText())) {
            return new ValidationInfo("请输入模块名称", moduleField);
        }
        if (getCheckedTableNames().isEmpty()) {
            return new ValidationInfo("请在左侧「选择生成表」列表中勾选至少 1 张参与生成的表", selectedTablesReadOnlyField);
        }
        boolean anySelected = targetCheckBoxes.values().stream().anyMatch(AbstractButton::isSelected);
        if (!anySelected) {
            return new ValidationInfo("请至少勾选 1 个生成选项");
        }
        return null;
    }

    @Override
    protected void doOKAction() {
        if (beanQueryTable.isEditing()) {
            beanQueryTable.getCellEditor().stopCellEditing();
        }
        if (serviceQueryTable.isEditing()) {
            serviceQueryTable.getCellEditor().stopCellEditing();
        }

        if (doValidate() != null) {
            super.doOKAction();
            return;
        }
        syncCheckedTablesToSummaryField();
        syncDbFieldsToState();

        String tableKey = selectedTablesReadOnlyField.getText().trim();
        params.put("author", authorField.getText().trim());
        params.put("moduleName", moduleField.getText().trim());
        params.put("include", tableKey);
        params.put("writeStrategy", getSelectedWriteStrategyCode());
        params.put("docStyle", getSelectedDocStyleCode());
        params.put("enableValidation", enableValidationCheckBox.isSelected());

        if (currentSourceMode == DataSourceMode.OFFLINE_DDL && !parsedDdlTables.isEmpty()) {
            params.put("parsedDdlTables", new LinkedHashMap<>(parsedDdlTables));
        } else {
            params.remove("parsedDdlTables");
        }

        if (StringUtils.isNotBlank(fixedEnumField.getText())) {
            params.put("fixedEnums", fixedEnumField.getText().trim());
        } else {
            params.remove("fixedEnums");
        }

        for (Map.Entry<String, JBCheckBox> entry : targetCheckBoxes.entrySet()) {
            params.put(entry.getKey(), entry.getValue().isSelected() ? "y" : "n");
        }

        saveCurrentQueryDraft();
        beanQueryDrafts.forEach((key, value) -> settingsService.saveBeanQueryList(key, value, projectRootPath));
        serviceQueryDrafts.forEach((key, value) -> settingsService.saveServiceQueryList(key, value, projectRootPath));
        Map<String, List<BeanQuery>> beanQueriesByTable = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, List<ServiceQuery>> serviceQueriesByTable = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String table : getCheckedTableNames()) {
            beanQueriesByTable.put(table, settingsService.getBeanQueryList(table, projectRootPath));
            serviceQueriesByTable.put(table, settingsService.getServiceQueryList(table, projectRootPath));
        }
        params.put("beanQueryByTable", beanQueriesByTable);
        params.put("serviceQueryListByTable", serviceQueriesByTable);
        params.put("beanQuery", beanQueryDrafts.getOrDefault(loadedQueryTableName, Collections.emptyList()));
        params.put("serviceQueryList", serviceQueryDrafts.getOrDefault(loadedQueryTableName, Collections.emptyList()));

        super.doOKAction();
        if (onConfirmCallback != null) {
            onConfirmCallback.run();
        }
    }

    private void saveCurrentQueryDraft() {
        if (StringUtils.isBlank(loadedQueryTableName)) {
            return;
        }
        if (beanQueryTable.isEditing()) {
            beanQueryTable.getCellEditor().stopCellEditing();
        }
        if (serviceQueryTable.isEditing()) {
            serviceQueryTable.getCellEditor().stopCellEditing();
        }
        List<BeanQuery> beanQueries = Lists.newArrayList();
        for (int i = 0; i < beanQueryTableModel.getRowCount(); i++) {
            String name = StringUtils.trimToEmpty(String.valueOf(beanQueryTableModel.getValueAt(i, 0)));
            String format = StringUtils.trimToEmpty(String.valueOf(beanQueryTableModel.getValueAt(i, 1)));
            String desc = StringUtils.trimToEmpty(String.valueOf(beanQueryTableModel.getValueAt(i, 2)));
            if (StringUtils.isNotBlank(name)) {
                BeanQuery bq = new BeanQuery();
                bq.setName(name);
                bq.setFormat(StringUtils.defaultIfBlank(format, "String"));
                bq.setType(String.valueOf(indexOfJavaType(bq.getFormat())));
                bq.setDescription(desc);
                beanQueries.add(bq);
            }
        }
        beanQueryDrafts.put(loadedQueryTableName, beanQueries);

        List<ServiceQuery> serviceQueries = Lists.newArrayList();
        for (int i = 0; i < serviceQueryTableModel.getRowCount(); i++) {
            String typeLabel = String.valueOf(serviceQueryTableModel.getValueAt(i, 0));
            String typeCode = typeLabel.contains(":") ? typeLabel.substring(0, typeLabel.indexOf(":")).trim() : "0";
            String col = StringUtils.trimToEmpty(String.valueOf(serviceQueryTableModel.getValueAt(i, 1)));
            String param = StringUtils.capitalize(StringUtils.trimToEmpty(String.valueOf(serviceQueryTableModel.getValueAt(i, 2))));
            boolean isStr = Boolean.TRUE.equals(serviceQueryTableModel.getValueAt(i, 3));
            String start = StringUtils.capitalize(StringUtils.trimToEmpty(String.valueOf(serviceQueryTableModel.getValueAt(i, 4))));
            String end = StringUtils.capitalize(StringUtils.trimToEmpty(String.valueOf(serviceQueryTableModel.getValueAt(i, 5))));
            String sql = StringUtils.trimToEmpty(String.valueOf(serviceQueryTableModel.getValueAt(i, 6)));

            if (StringUtils.isNotBlank(col)) {
                ServiceQuery sq = new ServiceQuery();
                sq.setType(typeCode);
                sq.setColumn("`" + StringUtils.unwrap(col, '`') + "`");
                sq.setParameter(param);
                sq.setParameterType(isStr);
                sq.setStart(start);
                sq.setEnd(end);
                sq.setSql("\"" + StringUtils.unwrap(sql, '"') + "\"");
                serviceQueries.add(sq);
            }
        }
        serviceQueryDrafts.put(loadedQueryTableName, serviceQueries);
    }

    private int indexOfJavaType(String format) {
        for (int i = 0; i < JAVA_TYPES.length; i++) {
            if (JAVA_TYPES[i].equalsIgnoreCase(format)) {
                return i;
            }
        }
        return 0;
    }

    public String getAuthor() {
        return authorField.getText().trim();
    }

    public String getModuleName() {
        return moduleField.getText().trim();
    }

    public String getTableNames() {
        return selectedTablesReadOnlyField.getText().trim();
    }
}
