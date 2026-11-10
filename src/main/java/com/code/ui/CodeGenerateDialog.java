package com.code.ui;

import com.code.config.CodeGenSettingsState;
import com.code.i18n.CodeGenBundle;
import com.code.ui.SolidGlassTheme.GlassButton;
import com.code.ui.SolidGlassTheme.GlyphState;
import com.code.ui.SolidGlassTheme.SolidGlassPanel;
import com.code.ui.SolidGlassTheme.SourceBadge;
import com.code.ui.SolidGlassTheme.SqlSyntaxHighlightPane;
import com.code.ui.SolidGlassTheme.StatusGlyph;
import com.code.ui.SolidGlassTheme.Thickness;
import com.code.utils.CodeGenerate;
import com.code.utils.CodeGenerateUtils;
import com.code.utils.DatabaseDialect;
import com.code.utils.DdlMigrationGenerator;
import com.code.utils.DdlSqlParser;
import com.code.utils.EnumCommentParser;
import com.code.utils.FeignClientConverter;
import com.code.utils.IncrementalCodeMerger;
import com.code.utils.LocalHistoryHelper;
import com.code.utils.NaturalLanguageDdlInferenceEngine;
import com.code.utils.RelationTopologyDetector;
import com.code.utils.SmartQueryRuleInferencer;
import com.code.utils.ValidationAnnotationResolver;
import com.code.utils.entity.BeanQuery;
import com.code.utils.entity.EnumsEntityContent;
import com.code.utils.entity.ServiceQuery;
import com.code.utils.entity.SubTableRelation;
import com.code.utils.entity.Table;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
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
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 一体化代码生成向导窗口（v3.0 GA：PSI AST 合并 + 单文件实时 Diff 预演 + 表前缀剥离 + BaseEntity + 多模块路由 + FeignClient 生成与接口互转 + 主子表 One-to-Many + LocalHistory 一键回滚）
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
            "增量合并 (PSI AST)",
            "Diff 对比确认",
            "直接覆盖"
    };

    private static final String[] DOC_STYLE_CODES = {"NONE", "SWAGGER2", "OPENAPI3"};
    private static final String[] DOC_STYLE_LABELS = {
            "无文档注解",
            "Swagger 2 (@ApiModel)",
            "OpenAPI 3 (@Schema)"
    };

    private static final String[] MAPPER_STRATEGY_CODES = {"BEAN_UTILS", "MAPSTRUCT", "HUTOOL"};
    private static final String[] MAPPER_STRATEGY_LABELS = {
            "Spring BeanUtils (默认)",
            "MapStruct (@Mapper 强类型)",
            "Hutool Convert"
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
    private final GlassButton rollbackLastBtn = new GlassButton("回滚上次生成");
    private JMenuItem rollbackMenuItem;

    // 基础配置输入框
    private final JBTextField authorField = new JBTextField();
    private final JBTextField packageField = new JBTextField();
    private final JBTextField moduleField = new JBTextField();
    private final JBTextField selectedTablesReadOnlyField = new JBTextField();
    private final JBTextField fixedEnumField = new JBTextField();

    // v4.0 M1 扩展：表名前缀剥离、实体公共基类、Feign 目标微服务名称、统一响应包装与多模块分层路由
    private final JBTextField tablePrefixesField = new JBTextField();
    private final JBTextField baseEntityField = new JBTextField();
    private final JBTextField feignServiceNameField = new JBTextField();
    private final JBTextField responseWrapperField = new JBTextField();
    private final Map<String, String> customLayerModuleRouting = new LinkedHashMap<>();

    // 写入方式、文档注解、对象转换策略与校验注解控件
    private final ComboBox<String> writeStrategyCombo = new ComboBox<>(WRITE_STRATEGY_LABELS);
    private final JLabel writeStrategyHintLabel = SolidGlassTheme.createHelperLabel("");
    private final ComboBox<String> docStyleCombo = new ComboBox<>(DOC_STYLE_LABELS);
    private final ComboBox<String> mapperStrategyCombo = new ComboBox<>(MAPPER_STRATEGY_LABELS);
    private final JBCheckBox enableValidationCheckBox = new JBCheckBox("自动推断 (@NotBlank / @Size / @Email)", true);

    // 数据库连接输入框与连接页专属状态徽标
    private final JBTextField jdbcUrlField = new JBTextField();
    private final JBTextField databaseField = new JBTextField();
    private final JBTextField usernameField = new JBTextField();
    private final JBPasswordField passwordField = new JBPasswordField();
    private final StatusGlyph dbPageStatusBadge = new StatusGlyph("未验证连接", GlyphState.IDLE);

    // 生成目标标准复选框 (JBCheckBox) 与计数标签
    private final Map<String, JBCheckBox> targetCheckBoxes = new LinkedHashMap<>();
    private JButton targetsToggleBtn;
    private GlassButton targetsEditBtn;
    private boolean targetsExpanded;
    private String targetsSummaryTooltip = "";
    private JButton advancedToggleBtn;
    private boolean advancedExpanded;
    private JSplitPane workbenchSplit;

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

    // 右侧：生成文件预览（支持双击任意行直接打开单文件实时 Diff 预演）
    private final JLabel rightPreviewTitleLabel = new JLabel("生成文件预览");
    private final JLabel rightPreviewDetailsLabel = SolidGlassTheme.createHelperLabel("请选择预览表");
    private final JLabel planPathLabel = SolidGlassTheme.createHelperLabel("选中文件后可查看完整路径");
    private final GlassButton viewDiffBtn = new GlassButton("查看 Diff");
    private final GlassButton switchToDbModeBtn = new GlassButton("切换为数据库连接来源");
    private final GlassButton checkPreviewTableBtn = new GlassButton("勾选此表");
    private boolean previewParticipates = true;
    private boolean columnsLoading;
    private final JBTextField outputDirReadOnlyField = new JBTextField();
    private DefaultTableModel filePreviewTableModel;
    private JBTable filePreviewTable;
    private final List<String> previewRowAbsolutePaths = new ArrayList<>();
    private final JLabel fieldParseSummaryLabel = SolidGlassTheme.createHelperLabel("字段解析：尚未读取表字段元数据");

    // 已解析表的列元数据缓存
    private final Map<String, List<Table>> resolvedTableColumnsCache = new HashMap<>();

    // 页签二：Query 页与主子表（One-to-Many）关联模型
    private final JLabel queryPageTargetTableLabel = new JLabel("规则作用于：未选择表");
    private GlassButton reloadQueryCacheBtn;
    private String loadedQueryTableName = "";
    private final Map<String, List<BeanQuery>> beanQueryDrafts = new HashMap<>();
    private final Map<String, List<ServiceQuery>> serviceQueryDrafts = new HashMap<>();
    private final Map<String, List<SubTableRelation>> subTableRelationDrafts = new HashMap<>();
    private DefaultTableModel beanQueryTableModel;
    private JBTable beanQueryTable;
    private DefaultTableModel serviceQueryTableModel;
    private JBTable serviceQueryTable;
    private DefaultTableModel subTableRelationModel;
    private JBTable subTableRelationTable;

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
        setOKButtonText(CodeGenBundle.message("dialog.ok.button"));
        setCancelButtonText(CodeGenBundle.message("dialog.cancel.button"));
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
            authorField.setText(Objects.toString(state.author, "syrobin"));
            packageField.setText(Objects.toString(state.packageName, "com.example"));
            moduleField.setText(Objects.toString(state.lastModuleName, ""));
            initialSavedTables = Objects.toString(state.lastTableNames, "");

            tablePrefixesField.setText(Objects.toString(state.tablePrefixes, ""));
            baseEntityField.setText(Objects.toString(state.baseEntityClass, ""));
            feignServiceNameField.setText(Objects.toString(state.feignServiceName, ""));
            responseWrapperField.setText(Objects.toString(state.responseWrapperClass, ""));
            customLayerModuleRouting.clear();
            if (state.layerModuleRouting != null) {
                customLayerModuleRouting.putAll(state.layerModuleRouting);
            }

            jdbcUrlField.setText(StringUtils.defaultString(state.jdbcUrl));
            jdbcUrlField.setCaretPosition(0);
            databaseField.setText(StringUtils.defaultString(state.database));
            usernameField.setText(Objects.toString(state.username, "root"));
            passwordField.setText(StringUtils.defaultString(state.retrieveCredential()));

            selectStrategyComboByCode(state.writeStrategy);
            selectDocStyleComboByCode(state.docStyle);
            selectMapperStrategyComboByCode(state.mapperStrategy);
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
            boolean checked = Strings.CI.equals(String.valueOf(params.get(entry.getKey())), "y");
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
        JPanel rootCanvas = new JPanel(new BorderLayout(0, 8)) {
            @Override
            public Dimension getMinimumSize() {
                return new Dimension(JBUI.scale(640), JBUI.scale(420));
            }
        };
        rootCanvas.setBackground(SolidGlassTheme.canvasBg());
        rootCanvas.setBorder(JBUI.Borders.empty(8, 10, 4, 10));
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int prefW = Math.min(JBUI.scale(940), Math.max(JBUI.scale(720), screen.width - 140));
        int prefH = Math.min(JBUI.scale(720), Math.max(JBUI.scale(500), screen.height - 160));
        rootCanvas.setPreferredSize(new Dimension(prefW, prefH));
        rootCanvas.setMinimumSize(new Dimension(JBUI.scale(640), JBUI.scale(420)));

        // 顶栏只留来源和解析结果。回滚、导出、切换来源收进「更多」。
        JPanel sourceAreaRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        sourceAreaRight.setOpaque(false);
        sourceAreaRight.add(sourceBadge);
        sourceAreaRight.add(verificationBadge);

        rollbackLastBtn.setToolTipText("一键回滚最近一次代码生成批次（恢复被修改文件，删除新增文件）");
        rollbackLastBtn.addActionListener(e -> rollbackLastBatchAction());
        rollbackMenuItem = new JMenuItem("回滚上次生成");
        rollbackMenuItem.setForeground(SolidGlassTheme.errorFg());
        rollbackMenuItem.setToolTipText(rollbackLastBtn.getToolTipText());
        rollbackMenuItem.addActionListener(e -> rollbackLastBatchAction());

        JMenuItem exportItem = new JMenuItem("导出团队规范");
        exportItem.setToolTipText("将当前项目的包名、表前缀、基类、Feign服务名、模块路由与自定义模板导出至 .syrobin-codegen.json（不含数据库密码）");
        exportItem.addActionListener(e -> exportTeamContractAction());

        JMenuItem switchSourceItem = new JMenuItem("切换来源");
        switchSourceItem.addActionListener(e -> {
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

        JMenuItem convertFeignItem = new JMenuItem("已有接口转 Feign…");
        convertFeignItem.setToolTipText("选择当前工程已有的 Controller 类或 Service/API 接口，一键转换为 Spring Cloud @FeignClient 接口");
        convertFeignItem.addActionListener(e -> openConvertExistingInterfaceToFeignDialog());

        JPopupMenu moreMenu = new JPopupMenu();
        moreMenu.add(switchSourceItem);
        moreMenu.add(convertFeignItem);
        moreMenu.add(exportItem);
        moreMenu.addSeparator();
        moreMenu.add(rollbackMenuItem);
        GlassButton moreBtn = new GlassButton("更多");
        moreBtn.addActionListener(e -> moreMenu.show(moreBtn, moreBtn.getWidth() - moreMenu.getPreferredSize().width, moreBtn.getHeight()));
        sourceAreaRight.add(moreBtn);
        syncRollbackEnabled();

        JPanel compactHeader = SolidGlassTheme.createCompactHeader(
                "Syrobin Code Generate",
                "v3.0 GA",
                sourceAreaRight
        );
        compactHeader.setMinimumSize(new Dimension(JBUI.scale(600), JBUI.scale(34)));

        // 2. 独立功能页签
        mainTabbedPane = new JBTabbedPane();
        mainTabbedPane.setOpaque(false);
        SolidGlassTheme.styleTabs(mainTabbedPane);
        mainTabbedPane.setMinimumSize(new Dimension(JBUI.scale(600), JBUI.scale(360)));
        mainTabbedPane.addTab("生成配置与选表", createBasicAndTablePanel());
        mainTabbedPane.addTab("自定义 Query 与主子表", createQueryRulesPanel());
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

    private void syncRollbackEnabled() {
        boolean enabled = LocalHistoryHelper.hasRollbackableBatch(project);
        rollbackLastBtn.setEnabled(enabled);
        if (rollbackMenuItem != null) {
            rollbackMenuItem.setEnabled(enabled);
        }
    }

    private void rollbackLastBatchAction() {
        LocalHistoryHelper.BatchSnapshot snap = LocalHistoryHelper.peekLastBatch(project);
        if (snap == null) {
            Messages.showInfoMessage(project, CodeGenBundle.message("rollback.empty.message"), "回滚提示");
            syncRollbackEnabled();
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
            syncRollbackEnabled();
            refreshFileImpactPreview();
            Messages.showInfoMessage(
                    project,
                    CodeGenBundle.message("rollback.success.message", count, snap.getSummary()),
                    "回滚成功"
            );
        } catch (Exception ex) {
            Messages.showErrorDialog(project, "回滚失败：" + ex.getMessage(), "错误");
        }
    }

    @Nullable
    @Override
    protected JPanel createSouthAdditionalPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 2));
        panel.setOpaque(false);
        panel.setBorder(JBUI.Borders.emptyLeft(4));
        panel.setMinimumSize(new Dimension(JBUI.scale(220), JBUI.scale(32)));

        footerSummaryLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(12f)));
        footerSummaryLabel.setForeground(SolidGlassTheme.textPrimary());

        panel.add(footerSummaryLabel, BorderLayout.CENTER);
        return panel;
    }

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
        tablePrefixesField.getDocument().addDocumentListener(liveSummaryListener);
        baseEntityField.getDocument().addDocumentListener(liveSummaryListener);
        feignServiceNameField.getDocument().addDocumentListener(liveSummaryListener);
        responseWrapperField.getDocument().addDocumentListener(liveSummaryListener);

        styleTextField(authorField);
        styleTextField(packageField);
        styleTextField(moduleField);
        styleTextField(selectedTablesReadOnlyField);
        styleTextField(fixedEnumField);
        styleTextField(tablePrefixesField);
        styleTextField(baseEntityField);
        styleTextField(feignServiceNameField);
        styleTextField(responseWrapperField);

        moduleField.getEmptyText().setText("article");
        fixedEnumField.getEmptyText().setText("status");
        tablePrefixesField.getEmptyText().setText("t_,sys_");
        baseEntityField.getEmptyText().setText("com.example.common.BaseEntity");
        feignServiceNameField.getEmptyText().setText("order-service");
        responseWrapperField.getEmptyText().setText("com.example.common.Result");
        authorField.getEmptyText().setText("syrobin");

        selectedTablesReadOnlyField.setEditable(false);
        selectedTablesReadOnlyField.getEmptyText().setText("尚未勾选表");

        writeStrategyCombo.addActionListener(e -> {
            updateWriteStrategyHint();
            updateSummaryAndSourceIndicators();
            refreshFileImpactPreview();
        });
        docStyleCombo.addActionListener(e -> refreshFileImpactPreview());
        mapperStrategyCombo.addActionListener(e -> {
            updateSummaryAndSourceIndicators();
            refreshFileImpactPreview();
        });
        enableValidationCheckBox.addActionListener(e -> refreshFileImpactPreview());

        JPanel commonForm = new JPanel(new GridBagLayout());
        commonForm.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(2, 0, 2, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        addFormRow(commonForm, gbc, 0, "基础包名:", packageField);
        addFormRow(commonForm, gbc, 1, "模块名称:", moduleField);

        JPanel writeStrategyBox = new JPanel(new BorderLayout(0, 2));
        writeStrategyBox.setOpaque(false);
        writeStrategyBox.add(writeStrategyCombo, BorderLayout.NORTH);
        writeStrategyBox.add(writeStrategyHintLabel, BorderLayout.SOUTH);
        addFormRow(commonForm, gbc, 2, "文件写入方式:", writeStrategyBox);

        JPanel targetsPanel = new JPanel(new BorderLayout(0, 6));
        targetsPanel.setOpaque(false);
        targetsPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SolidGlassTheme.borderColor()),
                JBUI.Borders.empty(8, 0, 0, 0)));

        GlassButton presetBtn = new GlassButton("应用预设");
        JPopupMenu presetMenu = new JPopupMenu();
        JMenuItem queryPreset = new JMenuItem("仅 Query");
        queryPreset.addActionListener(e -> applyTargetPreset("entityQuery", "serviceQuery"));
        JMenuItem feignPreset = new JMenuItem("Feign 契约");
        feignPreset.setToolTipText("勾选 FeignClient 接口、Entity BO、Entity VO、Entity Query");
        feignPreset.addActionListener(e -> applyTargetPreset("feignClient", "entityBO", "entityVO", "entityQuery"));
        presetMenu.add(queryPreset);
        presetMenu.add(feignPreset);
        presetBtn.addActionListener(e -> presetMenu.show(presetBtn, 0, presetBtn.getHeight()));

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

        JPanel batchButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        batchButtons.setOpaque(false);
        batchButtons.add(presetBtn);
        batchButtons.add(selectAllBtn);
        batchButtons.add(clearAllBtn);

        JPanel groups = new JPanel();
        groups.setOpaque(false);
        groups.setLayout(new BoxLayout(groups, BoxLayout.Y_AXIS));
        groups.setVisible(false);
        batchButtons.remove(presetBtn);
        groups.add(batchButtons);
        groups.add(Box.createVerticalStrut(JBUI.scale(6)));
        groups.add(createTargetGroup("实体",
                "entity", "Entity 实体",
                "entityBO", "Entity BO",
                "entityVO", "Entity VO",
                "entityQuery", "Entity Query",
                "enums", "Enum 枚举"));
        groups.add(createTargetGroup("持久层",
                "mapper", "Mapper 接口",
                "mapperXml", "Mapper XML",
                "excelDto", "EasyExcel DTO",
                "converter", "MapStruct"));
        groups.add(createTargetGroup("服务",
                "service", "Service",
                "serviceQuery", "ServiceQuery",
                "serviceResults", "ServiceResults"));
        groups.add(createTargetGroup("接口",
                "controller", "Controller",
                "feignClient", "FeignClient"));
        groups.add(createTargetGroup("其它",
                "frontendApi", "前端 TS",
                "frontendVue", "Vue3 页面",
                "httpClient", "HTTP Client",
                "unitTest", "JUnit5",
                "flywayMigration", "Flyway"));

        targetsToggleBtn = createDisclosureButton();
        targetsToggleBtn.addActionListener(e -> {
            targetsExpanded = !targetsExpanded;
            groups.setVisible(targetsExpanded);
            updateTargetsToggleLabel();
            mainPanel.revalidate();
        });
        GlassButton editTargetsBtn = new GlassButton("编辑");
        editTargetsBtn.addActionListener(e -> targetsToggleBtn.doClick());
        targetsEditBtn = editTargetsBtn;

        JPanel targetsHeaderActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        targetsHeaderActions.setOpaque(false);
        targetsHeaderActions.add(presetBtn);
        targetsHeaderActions.add(editTargetsBtn);

        JPanel targetsHeader = new JPanel(new BorderLayout(8, 0));
        targetsHeader.setOpaque(false);
        targetsHeader.add(targetsToggleBtn, BorderLayout.CENTER);
        targetsHeader.add(targetsHeaderActions, BorderLayout.EAST);

        targetsPanel.add(targetsHeader, BorderLayout.NORTH);
        targetsPanel.add(groups, BorderLayout.CENTER);

        JPanel advancedBody = new JPanel(new GridBagLayout());
        advancedBody.setOpaque(false);
        advancedBody.setVisible(false);
        GridBagConstraints advancedGbc = new GridBagConstraints();
        advancedGbc.insets = JBUI.insets(2, 0, 2, 6);
        advancedGbc.fill = GridBagConstraints.HORIZONTAL;
        advancedGbc.anchor = GridBagConstraints.WEST;
        enableValidationCheckBox.setOpaque(false);
        enableValidationCheckBox.setForeground(SolidGlassTheme.textPrimary());
        addFormRow(advancedBody, advancedGbc, 0, "作者名称:", authorField);
        addFormRow(advancedBody, advancedGbc, 1, "表前缀剥离:", tablePrefixesField);
        addFormRow(advancedBody, advancedGbc, 2, "实体公共基类:", baseEntityField);
        addFormRow(advancedBody, advancedGbc, 3, "限定枚举列:", fixedEnumField);
        addFormRow(advancedBody, advancedGbc, 4, "文档注解:", docStyleCombo);
        addFormRow(advancedBody, advancedGbc, 5, "对象转换策略:", mapperStrategyCombo);
        addFormRow(advancedBody, advancedGbc, 6, "校验注解:", enableValidationCheckBox);
        addFormRow(advancedBody, advancedGbc, 7, "Feign 服务名:", feignServiceNameField);
        addFormRow(advancedBody, advancedGbc, 8, "统一响应包装:", responseWrapperField);

        advancedToggleBtn = createDisclosureButton();
        advancedToggleBtn.addActionListener(e -> {
            advancedExpanded = !advancedExpanded;
            advancedBody.setVisible(advancedExpanded);
            updateAdvancedToggleLabel();
            mainPanel.revalidate();
        });
        JPanel advancedWrap = new JPanel(new BorderLayout(0, 4));
        advancedWrap.setOpaque(false);
        advancedWrap.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SolidGlassTheme.borderColor()),
                JBUI.Borders.empty(8, 0, 0, 0)));
        advancedWrap.add(advancedToggleBtn, BorderLayout.NORTH);
        advancedWrap.add(advancedBody, BorderLayout.CENTER);

        JPanel configColumn = new JPanel(new BorderLayout(0, 8));
        configColumn.setOpaque(false);
        configColumn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SolidGlassTheme.borderColor()),
                JBUI.Borders.empty(8, 0, 0, 4)));
        JPanel configTop = new JPanel(new BorderLayout(0, 8));
        configTop.setOpaque(false);
        configTop.add(commonForm, BorderLayout.NORTH);
        configTop.add(targetsPanel, BorderLayout.CENTER);
        configTop.add(advancedWrap, BorderLayout.SOUTH);
        configColumn.add(configTop, BorderLayout.NORTH);
        JBScrollPane configScroll = new JBScrollPane(configColumn);
        configScroll.setBorder(JBUI.Borders.empty());
        configScroll.setOpaque(false);
        configScroll.getViewport().setOpaque(false);
        configScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        configScroll.setMinimumSize(new Dimension(JBUI.scale(260), JBUI.scale(120)));

        JPanel tableCard = createLeftTableSelectionCard();
        tableCard.setMinimumSize(new Dimension(JBUI.scale(260), JBUI.scale(170)));
        JSplitPane leftSplit = SolidGlassTheme.createCleanSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                tableCard,
                configScroll,
                JBUI.scale(280),
                0.55
        );
        leftSplit.setMinimumSize(new Dimension(JBUI.scale(280), JBUI.scale(300)));

        SolidGlassPanel rightPreviewCard = createRightFilePreviewCard();
        rightPreviewCard.setMinimumSize(new Dimension(JBUI.scale(280), JBUI.scale(160)));
        workbenchSplit = SolidGlassTheme.createCleanSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                leftSplit,
                rightPreviewCard,
                400,
                0.42
        );
        workbenchSplit.setMinimumSize(new Dimension(JBUI.scale(560), JBUI.scale(220)));

        mainPanel.setMinimumSize(new Dimension(JBUI.scale(580), JBUI.scale(340)));
        mainPanel.add(workbenchSplit, BorderLayout.CENTER);
        mainPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (workbenchSplit == null || mainPanel.getWidth() <= 0) {
                    return;
                }
                boolean stack = mainPanel.getWidth() < JBUI.scale(780);
                int orientation = stack ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT;
                if (workbenchSplit.getOrientation() != orientation) {
                    workbenchSplit.setOrientation(orientation);
                    workbenchSplit.setResizeWeight(stack ? 0.5 : 0.42);
                    mainPanel.revalidate();
                }
            }
        });
        return mainPanel;
    }

    private void applyTargetPreset(String... keys) {
        targetCheckBoxes.values().forEach(cb -> cb.setSelected(false));
        for (String key : keys) {
            JBCheckBox box = targetCheckBoxes.get(key);
            if (box != null) {
                box.setSelected(true);
            }
        }
        updateSummaryAndSourceIndicators();
        refreshFileImpactPreview();
    }

    private void updateAdvancedToggleLabel() {
        if (advancedToggleBtn == null) {
            return;
        }
        int customized = customizedAdvancedCount();
        String suffix = customized > 0 ? " · 已改 " + customized + " 项" : "";
        advancedToggleBtn.setText("高级配置" + suffix);
        advancedToggleBtn.setIcon(advancedExpanded ? AllIcons.General.ArrowDown : AllIcons.General.ArrowRight);
    }

    private void updateTargetsToggleLabel() {
        if (targetsToggleBtn == null) {
            return;
        }
        long selected = targetCheckBoxes.values().stream().filter(AbstractButton::isSelected).count();
        targetsToggleBtn.setText("生成目标 " + selected + " / " + targetCheckBoxes.size());
        targetsToggleBtn.setIcon(targetsExpanded ? AllIcons.General.ArrowDown : AllIcons.General.ArrowRight);
        targetsToggleBtn.setToolTipText(targetsSummaryTooltip);
        targetsEditBtn.setText(targetsExpanded ? "收起" : "编辑");
    }

    private JButton createDisclosureButton() {
        JButton button = new SolidGlassTheme.FlatButton(null, AllIcons.General.ArrowRight);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setIconTextGap(JBUI.scale(6));
        button.setBorder(JBUI.Borders.empty(4, 0));
        button.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(12f)));
        return button;
    }

    private int customizedAdvancedCount() {
        int count = 0;
        if (StringUtils.isNotBlank(tablePrefixesField.getText())) {
            count++;
        }
        if (StringUtils.isNotBlank(baseEntityField.getText())) {
            count++;
        }
        if (StringUtils.isNotBlank(fixedEnumField.getText())) {
            count++;
        }
        if (StringUtils.isNotBlank(feignServiceNameField.getText())) {
            count++;
        }
        if (StringUtils.isNotBlank(responseWrapperField.getText())) {
            count++;
        }
        if (StringUtils.isNotBlank(authorField.getText()) && !"syrobin".equals(authorField.getText().trim())) {
            count++;
        }
        if (docStyleCombo.getSelectedIndex() >= 0 && docStyleCombo.getSelectedIndex() != 2) {
            count++;
        }
        if (mapperStrategyCombo.getSelectedIndex() > 0) {
            count++;
        }
        if (!enableValidationCheckBox.isSelected()) {
            count++;
        }
        return count;
    }

    private JPanel createTargetGroup(String title, String... keysAndLabels) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(JBUI.Borders.emptyBottom(8));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(SolidGlassTheme.textMuted());
        titleLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.PLAIN, JBUI.scaleFontSize(11.5f)));
        titleLabel.setPreferredSize(new Dimension(JBUI.scale(48), JBUI.scale(22)));
        titleLabel.setVerticalAlignment(SwingConstants.TOP);
        JPanel checks = new JPanel(new GridLayout(0, 2, 6, 2));
        checks.setOpaque(false);
        for (int i = 0; i < keysAndLabels.length; i += 2) {
            registerTargetCheckBox(checks, keysAndLabels[i], keysAndLabels[i + 1]);
        }
        row.add(titleLabel, BorderLayout.WEST);
        row.add(checks, BorderLayout.CENTER);
        return row;
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0;
        JBLabel name = new JBLabel(label);
        name.setForeground(SolidGlassTheme.textPrimary());
        panel.add(name, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
    }

    /**
     * 切换至离线 DDL 解析页签并自动加载示例 SQL（若当前为空）
     */
    public void switchToOfflineDdlTab() {
        databaseConfigVersion++;
        currentSourceMode = DataSourceMode.OFFLINE_DDL;
        if (StringUtils.isBlank(ddlSqlPane.getText())) {
            ddlSqlPane.setText(SAMPLE_DDL);
            parseDdlAndPopulateTables(false);
        }
        if (mainTabbedPane != null && mainTabbedPane.getTabCount() > 3) {
            mainTabbedPane.setSelectedIndex(3);
        }
        updateSummaryAndSourceIndicators();
        refreshFileImpactPreview();
    }

    /**
     * 扫描当前项目中的 Controller / Service 接口文件，支持一键将其转换为 Spring Cloud @FeignClient 接口
     */
    public void openConvertExistingInterfaceToFeignDialog() {
        List<File> candidates = new ArrayList<>();
        collectCandidateControllersAndInterfaces(new File(projectRootPath, "src/main/java"), candidates);
        if (candidates.isEmpty()) {
            Messages.showInfoMessage(
                    project,
                    "当前模块 (src/main/java) 下尚未发现可转换的 *Controller.java 或 *Service.java 文件。\n" +
                            "提示：您也可以直接在下方勾选「FeignClient 接口」从数据库表或离线 DDL 直接生成 FeignClient！",
                    "将接口转为 Feign Client"
            );
            return;
        }

        String[] options = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            File f = candidates.get(i);
            String rel = f.getAbsolutePath().replace('\\', '/');
            int idx = rel.indexOf("src/main/java/");
            options[i] = idx >= 0 ? rel.substring(idx + "src/main/java/".length()) : f.getName();
        }

        ComboBox<String> fileCombo = new ComboBox<>(options);
        JBTextField svcField = new JBTextField(StringUtils.defaultIfBlank(
                feignServiceNameField.getText().trim(),
                StringUtils.defaultIfBlank(moduleField.getText().trim(), "order") + "-service"
        ));
        JBCheckBox previewDiffBox = new JBCheckBox("转换后立即打开实时 Diff 预览对比", true);

        JPanel form = FormBuilder.createFormBuilder()
                .addLabeledComponent("选择源 Controller / 接口:", fileCombo)
                .addLabeledComponent("@FeignClient 服务名称:", svcField)
                .addComponent(previewDiffBox)
                .getPanel();

        int res = JOptionPane.showConfirmDialog(
                getContentPane(),
                form,
                "将已有 Controller / Service 接口转为 Feign Client",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (res != JOptionPane.OK_OPTION) {
            return;
        }

        int selectedIdx = fileCombo.getSelectedIndex();
        if (selectedIdx < 0 || selectedIdx >= candidates.size()) {
            return;
        }

        File sourceFile = candidates.get(selectedIdx);
        String svcName = svcField.getText().trim();
        if (StringUtils.isNotBlank(svcName)) {
            feignServiceNameField.setText(svcName);
        }

        try {
            String srcCode = Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8);
            String feignCode = FeignClientConverter.convertToFeignClientSource(srcCode, svcName, null);
            File targetFile = FeignClientConverter.resolveTargetFeignFile(sourceFile);

            String feignRoot = CodeGenerate.resolveLayerOutputRoot(project, projectRootPath, "feignClient",
                    customLayerModuleRouting.isEmpty() ? null : Collections.singletonMap("layerModuleRouting", customLayerModuleRouting));
            if (!CodeGenerateUtils.normalizeDirPath(feignRoot).equalsIgnoreCase(CodeGenerateUtils.normalizeDirPath(projectRootPath))) {
                String relPath = targetFile.getAbsolutePath().replace('\\', '/');
                int idx = relPath.indexOf("src/main/java/");
                if (idx >= 0) {
                    targetFile = new File(feignRoot + relPath.substring(idx));
                }
            }

            IncrementalCodeMerger.WriteStrategy strategy = IncrementalCodeMerger.WriteStrategy.fromName(getSelectedWriteStrategyCode());
            LocalHistoryHelper.startGenerationBatch(project, "ConvertToFeign:" + sourceFile.getName());
            LocalHistoryHelper.recordFileBeforeWrite(project, targetFile.getAbsolutePath());
            IncrementalCodeMerger.writeWithStrategy(project, feignCode, targetFile.getAbsolutePath(), strategy);
            LocalHistoryHelper.finishGenerationBatch(project);

            VirtualFile vf = VfsUtil.findFileByIoFile(targetFile.getParentFile(), true);
            if (vf != null) {
                VfsUtil.markDirtyAndRefresh(true, true, true, vf);
            }

            syncRollbackEnabled();
            if (previewDiffBox.isSelected()) {
                IncrementalCodeMerger.openSingleFilePreviewDiff(project, targetFile.getAbsolutePath(), feignCode, strategy);
            } else {
                Messages.showInfoMessage(
                        project,
                        "已成功将 [" + sourceFile.getName() + "] 转换为 Feign Client：\n" + targetFile.getAbsolutePath(),
                        "转换 Feign Client 成功"
                );
            }
        } catch (Exception ex) {
            LocalHistoryHelper.finishGenerationBatch(project);
            Messages.showErrorDialog(project, "转换 Feign Client 失败：" + ex.getMessage(), "错误");
        }
    }

    private void collectCandidateControllersAndInterfaces(File dir, List<File> out) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectCandidateControllersAndInterfaces(child, out);
            } else if (child.getName().endsWith("Controller.java")
                    || child.getName().endsWith("Service.java")
                    || child.getName().endsWith("Api.java")) {
                out.add(child);
            }
        }
    }

    private void styleTextField(JBTextField field) {
        field.setBackground(SolidGlassTheme.inputBg());
        field.setForeground(SolidGlassTheme.textPrimary());
    }

    private JPanel createLeftTableSelectionCard() {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SolidGlassTheme.borderColor()),
                JBUI.Borders.empty(8, 0, 0, 0)));

        JPanel headerRow = new JPanel(new BorderLayout(0, 4));
        headerRow.setOpaque(false);
        leftTablePanelTitleLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(12f)));
        leftTablePanelTitleLabel.setForeground(SolidGlassTheme.textPrimary());

        JPanel checkAllBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
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
        GlassButton manualAddTableBtn = new GlassButton("+ 手动添加表");
        manualAddTableBtn.addActionListener(e -> promptManualAddTables());
        checkAllBar.add(manualAddTableBtn);
        checkAllBar.add(checkAllTablesBtn);
        checkAllBar.add(uncheckAllTablesBtn);

        headerRow.add(leftTablePanelTitleLabel, BorderLayout.NORTH);
        headerRow.add(checkAllBar, BorderLayout.SOUTH);

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
                readActiveTableColumnsIfMissing();
            }
        });

        JBScrollPane listScroll = new JBScrollPane(tableJList);
        SolidGlassTheme.styleScrollPane(listScroll);
        listScroll.setPreferredSize(new Dimension(350, 220));
        listScroll.setMinimumSize(new Dimension(260, 170));

        card.add(northBox, BorderLayout.NORTH);
        card.add(listScroll, BorderLayout.CENTER);
        return card;
    }

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
            previewBadge.setForeground(SolidGlassTheme.textMuted());

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
            isPreviewActive = Strings.CI.equals(value.getTableName(), activePreviewTableName);
            previewBadge.setVisible(isPreviewActive);

            if (isPreviewActive) {
                setOpaque(true);
                setBackground(SolidGlassTheme.hoverBg());
                nameLabel.setForeground(SolidGlassTheme.textPrimary());
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
                g.setColor(SolidGlassTheme.textMuted());
                g.fillRect(0, 2, JBUI.scale(3), getHeight() - 4);
            }
        }
    }

    private SolidGlassPanel createRightFilePreviewCard() {
        SolidGlassPanel card = new SolidGlassPanel(new BorderLayout(0, 6), Thickness.THICK);

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        rightPreviewTitleLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(12f)));
        rightPreviewTitleLabel.setForeground(SolidGlassTheme.textPrimary());

        rightPreviewTitleLabel.setMinimumSize(new Dimension(0, 0));
        GlassButton refreshPreviewBtn = new GlassButton("刷新预览");
        refreshPreviewBtn.setToolTipText("重新读取字段并更新生成计划。选中一行后可查看 Diff");
        refreshPreviewBtn.addActionListener(e -> previewSelectedTableEnumsAsync());

        ((BorderLayout) headerRow.getLayout()).setHgap(JBUI.scale(8));
        headerRow.add(rightPreviewTitleLabel, BorderLayout.CENTER);
        headerRow.add(refreshPreviewBtn, BorderLayout.EAST);

        checkPreviewTableBtn.setVisible(false);
        checkPreviewTableBtn.addActionListener(e -> {
            TableItem item = masterTableMap.get(activePreviewTableName);
            if (item != null) {
                item.setChecked(true);
                tableJList.repaint();
                syncCheckedTablesToSummaryField();
            }
        });
        JPanel summaryText = new JPanel(new GridLayout(2, 1, 0, 2));
        summaryText.setOpaque(false);
        summaryText.add(rightPreviewDetailsLabel);
        summaryText.add(fieldParseSummaryLabel);
        JPanel summaryRow = new JPanel(new BorderLayout(8, 0));
        summaryRow.setOpaque(false);
        summaryRow.add(summaryText, BorderLayout.CENTER);
        summaryRow.add(checkPreviewTableBtn, BorderLayout.EAST);

        JPanel previewHeading = new JPanel(new BorderLayout(0, 4));
        previewHeading.setOpaque(false);
        previewHeading.add(headerRow, BorderLayout.NORTH);
        previewHeading.add(summaryRow, BorderLayout.SOUTH);

        viewDiffBtn.setToolTipText("打开选中文件的 Diff。也可以在列表中按 Enter");
        viewDiffBtn.setEnabled(false);
        viewDiffBtn.addActionListener(e -> openSelectedPreviewDiff());

        outputDirReadOnlyField.setEditable(false);
        outputDirReadOnlyField.setOpaque(false);
        outputDirReadOnlyField.setBorder(JBUI.Borders.empty());
        outputDirReadOnlyField.setForeground(SolidGlassTheme.textPrimary());
        outputDirReadOnlyField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(11.5f)));

        JButton copyDirBtn = new SolidGlassTheme.FlatButton(null, AllIcons.Actions.Copy);
        copyDirBtn.setToolTipText("复制路径");
        copyDirBtn.getAccessibleContext().setAccessibleName("复制路径");
        copyDirBtn.addActionListener(e -> {
            String dirText = outputDirReadOnlyField.getText();
            if (StringUtils.isNotBlank(dirText)) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(dirText), null);
                copyDirBtn.setToolTipText("已复制");
            }
        });

        GlassButton moduleRouteBtn = new GlassButton("多模块路由");
        moduleRouteBtn.setToolTipText("为 Entity/Mapper、BO/VO/Query、Service、Controller、FeignClient 分别指定不同的 Maven/Gradle 子模块输出目录");
        moduleRouteBtn.addActionListener(e -> openMultiModuleRoutingDialog());

        JPanel outputActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        outputActions.setOpaque(false);
        outputActions.add(copyDirBtn);
        outputActions.add(moduleRouteBtn);

        JBLabel outDirLabel = new JBLabel("输出到");
        outDirLabel.setForeground(SolidGlassTheme.textMuted());
        JPanel outputDirRow = new JPanel(new BorderLayout(6, 0));
        outputDirRow.setOpaque(false);
        outputDirRow.add(outDirLabel, BorderLayout.WEST);
        outputDirRow.add(outputDirReadOnlyField, BorderLayout.CENTER);
        outputDirRow.add(outputActions, BorderLayout.EAST);

        JPanel detailBar = new JPanel(new BorderLayout(6, 0));
        detailBar.setOpaque(false);
        planPathLabel.setMinimumSize(new Dimension(0, 0));
        detailBar.add(planPathLabel, BorderLayout.CENTER);
        detailBar.add(viewDiffBtn, BorderLayout.EAST);

        JPanel northStack = new JPanel(new BorderLayout(0, 6));
        northStack.setOpaque(false);
        northStack.add(previewHeading, BorderLayout.NORTH);
        northStack.add(outputDirRow, BorderLayout.SOUTH);

        filePreviewTableModel = new DefaultTableModel(new Object[]{"文件路径", "处理方式"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        filePreviewTable = new JBTable(filePreviewTableModel);
        filePreviewTable.setBackground(SolidGlassTheme.inputBg());
        filePreviewTable.setForeground(SolidGlassTheme.textPrimary());
        filePreviewTable.setGridColor(SolidGlassTheme.borderColor());
        filePreviewTable.setSelectionBackground(SolidGlassTheme.hoverBg());
        filePreviewTable.setSelectionForeground(SolidGlassTheme.textPrimary());
        filePreviewTable.setRowHeight(JBUI.scale(23));
        filePreviewTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(11.5f)));
        filePreviewTable.getColumnModel().getColumn(0).setPreferredWidth(305);
        filePreviewTable.getColumnModel().getColumn(1).setPreferredWidth(145);
        filePreviewTable.getEmptyText().setText("勾选表后，这里列出将写入的文件");
        filePreviewTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                String full = Objects.toString(value, "");
                int slash = Math.max(full.lastIndexOf('/'), full.lastIndexOf('\\'));
                String name = slash >= 0 && slash < full.length() - 1 ? full.substring(slash + 1) : full;
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, name, isSelected, hasFocus, row, column);
                label.setToolTipText(full);
                if (!isSelected) {
                    label.setBackground(SolidGlassTheme.inputBg());
                    label.setForeground(previewParticipates ? SolidGlassTheme.textPrimary() : SolidGlassTheme.textMuted());
                }
                return label;
            }
        });
        filePreviewTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || filePreviewTableModel == null) {
                return;
            }
            int row = filePreviewTable.getSelectedRow();
            if (row < 0 || row >= filePreviewTableModel.getRowCount()) {
                planPathLabel.setText("选中文件后可查看完整路径");
                viewDiffBtn.setEnabled(false);
                return;
            }
            planPathLabel.setText(Objects.toString(filePreviewTableModel.getValueAt(row, 0), ""));
            viewDiffBtn.setEnabled(row < previewRowAbsolutePaths.size() && StringUtils.isNotBlank(previewRowAbsolutePaths.get(row)));
        });
        filePreviewTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "viewDiff");
        filePreviewTable.getActionMap().put("viewDiff", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openSelectedPreviewDiff();
            }
        });

        filePreviewTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = filePreviewTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < previewRowAbsolutePaths.size()) {
                        openSingleFileDiffForPreviewRow(row);
                    }
                }
            }
        });

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
        tableScroll.setPreferredSize(new Dimension(480, 185));
        tableScroll.setMinimumSize(new Dimension(320, 145));

        card.add(northStack, BorderLayout.NORTH);
        card.add(tableScroll, BorderLayout.CENTER);
        card.add(detailBar, BorderLayout.SOUTH);
        return card;
    }

    private void openSingleFileDiffForPreviewRow(int row) {
        if (row < 0 || row >= previewRowAbsolutePaths.size()) {
            return;
        }
        String targetAbsPath = previewRowAbsolutePaths.get(row);
        if (StringUtils.isBlank(targetAbsPath)) {
            return;
        }
        String previewTable = activePreviewTableName;
        if (StringUtils.isBlank(previewTable)) {
            return;
        }
        List<Table> resolvedCols = getResolvedColumnsForTable(previewTable);
        if (resolvedCols == null || resolvedCols.isEmpty()) {
            Messages.showInfoMessage(project, "请先点击「刷新预览」读取表字段，再查看 Diff。", "查看 Diff");
            return;
        }

        try {
            saveCurrentQueryDraft();
            Map<String, Object> previewParams = buildCurrentPreviewParams(previewTable);
            Map<String, String> renderedMap = CodeGenerate.renderAllTargetFilesInMemory(
                    project,
                    previewTable,
                    moduleField.getText().trim(),
                    StringUtils.defaultIfBlank(authorField.getText().trim(), "syrobin"),
                    previewParams,
                    projectRootPath,
                    resolvedCols
            );
            String generatedContent = renderedMap.get(targetAbsPath);
            if (generatedContent == null) {
                for (Map.Entry<String, String> entry : renderedMap.entrySet()) {
                    if (entry.getKey().replace('\\', '/').equalsIgnoreCase(targetAbsPath.replace('\\', '/'))) {
                        generatedContent = entry.getValue();
                        break;
                    }
                }
            }
            if (generatedContent != null) {
                IncrementalCodeMerger.WriteStrategy strategy = IncrementalCodeMerger.WriteStrategy.fromName(getSelectedWriteStrategyCode());
                IncrementalCodeMerger.openSingleFilePreviewDiff(project, targetAbsPath, generatedContent, strategy);
            }
        } catch (Exception ex) {
            Messages.showErrorDialog(project, "生成单文件预览失败：" + ex.getMessage(), "预览异常");
        }
    }

    private void openSelectedPreviewDiff() {
        if (filePreviewTable == null) {
            return;
        }
        int row = filePreviewTable.getSelectedRow();
        if (row < 0) {
            Messages.showInfoMessage(project, "请先在生成计划中选中一个文件。", "查看 Diff");
            return;
        }
        openSingleFileDiffForPreviewRow(row);
    }

    private Map<String, Object> buildCurrentPreviewParams(String previewTable) {
        Map<String, Object> previewParams = new HashMap<>(params);
        previewParams.put("packageName", StringUtils.defaultIfBlank(packageField.getText().trim(), "com.example"));
        previewParams.put("tablePrefixes", tablePrefixesField.getText().trim());
        previewParams.put("baseEntityClass", baseEntityField.getText().trim());
        previewParams.put("feignServiceName", feignServiceNameField.getText().trim());
        previewParams.put("responseWrapperClass", responseWrapperField.getText().trim());
        previewParams.put("writeStrategy", getSelectedWriteStrategyCode());
        previewParams.put("docStyle", getSelectedDocStyleCode());
        previewParams.put("mapperStrategy", getSelectedMapperStrategyCode());
        previewParams.put("enableValidation", enableValidationCheckBox.isSelected());
        if (!customLayerModuleRouting.isEmpty()) {
            previewParams.put("layerModuleRouting", new LinkedHashMap<>(customLayerModuleRouting));
        }
        if (currentSourceMode == DataSourceMode.OFFLINE_DDL && !parsedDdlTables.isEmpty()) {
            previewParams.put("parsedDdlTables", new LinkedHashMap<>(parsedDdlTables));
        } else {
            previewParams.remove("parsedDdlTables");
            TableItem item = masterTableMap.get(previewTable);
            if (item != null) {
                previewParams.put("tableComment_" + previewTable, item.getComment());
            }
        }
        if (StringUtils.isNotBlank(fixedEnumField.getText())) {
            previewParams.put("fixedEnums", fixedEnumField.getText().trim());
        } else {
            previewParams.remove("fixedEnums");
        }
        for (Map.Entry<String, JBCheckBox> entry : targetCheckBoxes.entrySet()) {
            previewParams.put(entry.getKey(), entry.getValue().isSelected() ? "y" : "n");
        }
        previewParams.put("beanQuery", beanQueryDrafts.getOrDefault(previewTable, settingsService.getBeanQueryList(previewTable, projectRootPath)));
        previewParams.put("serviceQueryList", serviceQueryDrafts.getOrDefault(previewTable, settingsService.getServiceQueryList(previewTable, projectRootPath)));
        previewParams.put("subTableRelations", subTableRelationDrafts.getOrDefault(previewTable, settingsService.getSubTableRelations(previewTable)));
        return previewParams;
    }

    private void openMultiModuleRoutingDialog() {
        Map<String, String> discoveredModules = CodeGenerateUtils.discoverSubModules(project, projectRootPath);
        List<String> moduleOptions = new ArrayList<>();
        moduleOptions.add("默认（当前选中工程目录）");
        moduleOptions.addAll(discoveredModules.keySet());

        String[][] groups = {
                {"Entity 与 Mapper 层 (entity / mapper / enums)", "entity,mapper,enums"},
                {"DTO 模型层 (entityBO / entityVO / entityQuery)", "entityBO,entityVO,entityQuery"},
                {"Service 业务层 (service / serviceQuery / serviceResults)", "service,serviceQuery,serviceResults"},
                {"Controller 接口层 (controller)", "controller"},
                {"Feign 客户端层 (feignClient)", "feignClient"}
        };

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        List<ComboBox<String>> combos = new ArrayList<>();
        for (int i = 0; i < groups.length; i++) {
            String label = groups[i][0];
            String firstLayerKey = groups[i][1].split(",")[0];
            String currentMappedPath = customLayerModuleRouting.get(firstLayerKey);

            ComboBox<String> combo = new ComboBox<>(moduleOptions.toArray(new String[0]));
            combo.setEditable(true);
            if (StringUtils.isNotBlank(currentMappedPath)) {
                String matchedName = currentMappedPath;
                for (Map.Entry<String, String> modEntry : discoveredModules.entrySet()) {
                    if (CodeGenerateUtils.normalizeDirPath(modEntry.getValue()).equalsIgnoreCase(CodeGenerateUtils.normalizeDirPath(currentMappedPath))) {
                        matchedName = modEntry.getKey();
                        break;
                    }
                }
                combo.setSelectedItem(matchedName);
            }
            combos.add(combo);

            gbc.gridy = i;
            gbc.gridx = 0;
            gbc.weightx = 0;
            panel.add(new JBLabel(label + ":"), gbc);

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            panel.add(combo, gbc);
        }

        int res = JOptionPane.showConfirmDialog(
                getContentPane(),
                panel,
                "多模块微服务分层路由配置（支持选择子模块或输入子工程绝对路径）",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (res == JOptionPane.OK_OPTION) {
            customLayerModuleRouting.clear();
            for (int i = 0; i < groups.length; i++) {
                String selected = Objects.toString(combos.get(i).getSelectedItem(), "").trim();
                if (StringUtils.isNotBlank(selected) && !selected.startsWith("默认")) {
                    String resolvedPath = discoveredModules.getOrDefault(selected, selected);
                    for (String layerKey : groups[i][1].split(",")) {
                        customLayerModuleRouting.put(layerKey.trim(), CodeGenerateUtils.normalizeDirPath(resolvedPath));
                    }
                }
            }
            updateSummaryAndSourceIndicators();
            refreshFileImpactPreview();
        }
    }

    private void exportTeamContractAction() {
        try {
            syncDbFieldsToState();
            File exported = settingsService.exportTeamContract(projectRootPath);
            Messages.showInfoMessage(
                    project,
                    "已成功导出团队共享生成规范至：\n" + exported.getAbsolutePath() + "\n\n该文件不含数据库密码，可直接提交至 Git 供团队共享。",
                    "导出团队规范成功"
            );
        } catch (Exception ex) {
            Messages.showErrorDialog(project, "导出团队规范失败：" + ex.getMessage(), "错误");
        }
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
        leftTablePanelTitleLabel.setText("生成表 " + checked.size() + " / " + masterTableMap.size());
        if (StringUtils.isBlank(activePreviewTableName) && !checked.isEmpty()) {
            activePreviewTableName = checked.get(0);
        }
        updateSummaryAndSourceIndicators();
        refreshFileImpactPreview();
    }

    /**
     * 页签二：自定义 Query 规则与主子表（One-to-Many）关联绑定
     */
    private JComponent createQueryRulesPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 8));
        outer.setOpaque(false);
        outer.setBorder(JBUI.Borders.empty(4, 0, 0, 0));

        SolidGlassPanel targetBar = new SolidGlassPanel(new BorderLayout(8, 0), Thickness.THIN);
        targetBar.setBorder(JBUI.Borders.empty(6, 12));
        queryPageTargetTableLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(12.5f)));
        queryPageTargetTableLabel.setForeground(SolidGlassTheme.selectedFg());

        JLabel scopeHint = SolidGlassTheme.createHelperLabel("说明：扩展字段、查询条件与主子表关联均按表独立保存，切换预览表时自动切换");
        targetBar.add(queryPageTargetTableLabel, BorderLayout.WEST);
        targetBar.add(scopeHint, BorderLayout.EAST);

        GridBagLayout tablesBag = new GridBagLayout();
        JPanel tablesGrid = new JPanel(tablesBag);
        tablesGrid.setOpaque(false);

        // 1. BeanQuery 扩展字段卡片
        SolidGlassPanel beanCard = new SolidGlassPanel(new BorderLayout(4, 4), Thickness.MEDIUM);
        beanQueryTableModel = new DefaultTableModel(new Object[]{"变量名称 (name)", "Java类型 (format)", "字段描述 (description)"}, 0);
        beanQueryTable = new JBTable(beanQueryTableModel);
        beanQueryTable.setBackground(SolidGlassTheme.inputBg());
        beanQueryTable.setForeground(SolidGlassTheme.textPrimary());
        beanQueryTable.setGridColor(SolidGlassTheme.borderColor());
        beanQueryTable.setRowHeight(JBUI.scale(23));
        TableColumn typeColumn = beanQueryTable.getColumnModel().getColumn(1);
        typeColumn.setCellEditor(new DefaultCellEditor(new ComboBox<>(JAVA_TYPES)));

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

        GlassButton smartInferQueryBtn = new GlassButton("智能推导查询规则");
        smartInferQueryBtn.setToolTipText("基于当前表字段名与数据类型自动推导时间区间、模糊匹配与等值查询规则");
        smartInferQueryBtn.addActionListener(e -> autoInferQueryRulesForCurrentTable());
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
            subTableRelationDrafts.remove(loadedQueryTableName);
            loadedQueryTableName = "";
            reloadQueryTablesForCurrentTable();
        });

        beanBtnBar.add(smartInferQueryBtn);
        beanBtnBar.add(addBeanRowBtn);
        beanBtnBar.add(removeBeanRowBtn);
        beanBtnBar.add(clearBeanBtn);
        beanBtnBar.add(reloadQueryCacheBtn);

        JBScrollPane beanScroll = new JBScrollPane(beanQueryTable);
        SolidGlassTheme.styleScrollPane(beanScroll);
        beanCard.add(beanBtnBar, BorderLayout.NORTH);
        beanCard.add(beanScroll, BorderLayout.CENTER);

        // 2. ServiceQuery 查询规则卡片
        SolidGlassPanel serviceCard = new SolidGlassPanel(new BorderLayout(4, 4), Thickness.MEDIUM);
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
        serviceQueryTable.setRowHeight(JBUI.scale(23));
        TableColumn queryTypeCol = serviceQueryTable.getColumnModel().getColumn(0);
        queryTypeCol.setCellEditor(new DefaultCellEditor(new ComboBox<>(QUERY_TYPES)));

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

        // 3. 主子表（One-to-Many）关联配置卡片（v3.0 M3）
        SolidGlassPanel subTableCard = new SolidGlassPanel(new BorderLayout(4, 4), Thickness.MEDIUM);
        subTableRelationModel = new DefaultTableModel(
                new Object[]{"子表名称 (如 order_item)", "子表外键列 (如 order_id)", "主表主键属性 (默认 id)", "关联说明 (如 订单明细列表)"}, 0
        );
        subTableRelationTable = new JBTable(subTableRelationModel);
        subTableRelationTable.setBackground(SolidGlassTheme.inputBg());
        subTableRelationTable.setForeground(SolidGlassTheme.textPrimary());
        subTableRelationTable.setGridColor(SolidGlassTheme.borderColor());
        subTableRelationTable.setRowHeight(JBUI.scale(23));

        StatusText subEmptyText = subTableRelationTable.getEmptyText();
        subEmptyText.setText("尚未绑定子表（One-to-Many）", new com.intellij.ui.SimpleTextAttributes(
                com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN, SolidGlassTheme.textSecondary()));
        subEmptyText.appendSecondaryText("绑定后将在主表 BO / VO 中生成子表 List 集合，并在 ServiceImpl.saveEnhance 中生成级联保存逻辑", new com.intellij.ui.SimpleTextAttributes(
                com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN, SolidGlassTheme.textMuted()), null);

        JPanel subBtnBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        subBtnBar.setOpaque(false);
        JBLabel subTitle = new JBLabel("主子表关联绑定 (One-to-Many)");
        subTitle.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(12f)));
        subTitle.setForeground(SolidGlassTheme.textPrimary());
        subBtnBar.add(subTitle);

        GlassButton autoDiscoverSubBtn = new GlassButton("自动识别子表关联");
        autoDiscoverSubBtn.setToolTipText("根据 DDL FOREIGN KEY 约束或子表外键命名惯例自动识别一对多子表关系");
        autoDiscoverSubBtn.addActionListener(e -> autoDiscoverSubTablesForCurrentTable());
        GlassButton addSubRowBtn = new GlassButton("+ 绑定子表");
        addSubRowBtn.addActionListener(e -> subTableRelationModel.addRow(new Object[]{"", "", "id", ""}));
        GlassButton removeSubRowBtn = new GlassButton("- 删除选中");
        removeSubRowBtn.addActionListener(e -> removeSelectedRows(subTableRelationTable, subTableRelationModel));
        GlassButton clearSubBtn = new GlassButton("清空");
        clearSubBtn.addActionListener(e -> subTableRelationModel.setRowCount(0));

        subBtnBar.add(autoDiscoverSubBtn);
        subBtnBar.add(addSubRowBtn);
        subBtnBar.add(removeSubRowBtn);
        subBtnBar.add(clearSubBtn);

        JBScrollPane subScroll = new JBScrollPane(subTableRelationTable);
        SolidGlassTheme.styleScrollPane(subScroll);
        subTableCard.add(subBtnBar, BorderLayout.NORTH);
        subTableCard.add(subScroll, BorderLayout.CENTER);

        SolidGlassPanel[] cards = {beanCard, serviceCard, subTableCard};
        DefaultTableModel[] models = {beanQueryTableModel, serviceQueryTableModel, subTableRelationModel};
        for (JBScrollPane scroll : new JBScrollPane[]{beanScroll, serviceScroll, subScroll}) {
            scroll.setPreferredSize(new Dimension(JBUI.scale(400), JBUI.scale(80)));
        }
        Runnable weighByRows = () -> {
            for (int i = 0; i < cards.length; i++) {
                GridBagConstraints gbc = new GridBagConstraints(0, i, 1, 1, 1, Math.max(models[i].getRowCount(), 2),
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.insetsTop(i == 0 ? 0 : 6), 0, 0);
                if (cards[i].getParent() == tablesGrid) {
                    tablesBag.setConstraints(cards[i], gbc);
                } else {
                    tablesGrid.add(cards[i], gbc);
                }
            }
            tablesGrid.revalidate();
        };
        for (DefaultTableModel model : models) {
            model.addTableModelListener(e -> weighByRows.run());
        }
        weighByRows.run();

        outer.add(targetBar, BorderLayout.NORTH);
        outer.add(tablesGrid, BorderLayout.CENTER);
        return outer;
    }

    private JComponent createDatabaseConfigPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 8));
        wrapper.setOpaque(false);
        wrapper.setBorder(JBUI.Borders.empty(4, 0, 0, 0));

        styleTextField(jdbcUrlField);
        styleTextField(databaseField);
        styleTextField(usernameField);
        passwordField.setBackground(SolidGlassTheme.inputBg());
        passwordField.setForeground(SolidGlassTheme.textPrimary());

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
                jdbcUrlField.setCaretPosition(0);
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
                jdbcUrlField.setCaretPosition(0);
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

        JPanel topBar = new JPanel(new BorderLayout(8, 0));
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

        GlassButton nlDdlBtn = new GlassButton("自然语言生成 DDL");
        nlDdlBtn.setToolTipText("输入中文或英文业务需求描述，智能推导标准 CREATE TABLE DDL、字段类型与主子表外键");
        nlDdlBtn.addActionListener(e -> promptNaturalLanguageDdl());

        GlassButton diffMigrationBtn = new GlassButton("表结构演进 Diff SQL");
        diffMigrationBtn.setToolTipText("对比旧版 DDL 与当前 DDL，自动生成 MySQL/PG/Oracle/DM8 增量 ALTER TABLE 迁移脚本");
        diffMigrationBtn.addActionListener(e -> openDdlEvolutionDiffDialog());

        leftActions.add(parseBtn);
        leftActions.add(nlDdlBtn);
        leftActions.add(diffMigrationBtn);
        leftActions.add(loadSampleBtn);
        leftActions.add(clearSqlOnlyBtn);

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

        JBScrollPane sqlScroll = new JBScrollPane(ddlSqlPane);
        SolidGlassTheme.styleScrollPane(sqlScroll);

        JPanel bottomSummaryRow = new JPanel(new BorderLayout(8, 0));
        bottomSummaryRow.setOpaque(false);
        bottomSummaryRow.add(ddlSingleLineSummaryBadge, BorderLayout.WEST);
        bottomSummaryRow.add(SolidGlassTheme.createHelperLabel("支持 MySQL / PostgreSQL / Oracle / DM8 建表及 COMMENT ON / ALTER TABLE 语法"), BorderLayout.EAST);

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
            writeStrategyHintLabel.setText("保留手写方法与注解，只插入新增内容");
        } else if (idx == 1) {
            writeStrategyHintLabel.setText("先打开 Diff，确认后再写入磁盘");
        } else {
            writeStrategyHintLabel.setText("同名文件会被整文件替换");
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
        String className = CodeGenerateUtils.toUpper(tableName, tablePrefixesField.getText().trim());
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

        syncRollbackEnabled();
        switchToDbModeBtn.setVisible(currentSourceMode == DataSourceMode.OFFLINE_DDL);
        dbPageStatusBadge.setVisible(currentSourceMode != DataSourceMode.DATABASE);
        loadTablesBtn.setText(currentSourceMode == DataSourceMode.OFFLINE_DDL
                ? "切换并加载数据库表" : dbConnectionVerified ? "重新加载" : "加载表清单");
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

        int totalOptionCount = targetCheckBoxes.size();
        String routeTag = customLayerModuleRouting.isEmpty() ? "" : " · 已启用多模块路由";
        targetsSummaryTooltip = enumChecked
                ? "单表 " + baseFilesPerTable + " 个基础文件，另按枚举列注释生成枚举" + routeTag
                : "单表固定产出 " + baseFilesPerTable + " 个文件" + routeTag;
        updateTargetsToggleLabel();
        updateAdvancedToggleLabel();
        boolean confirmFirst = IncrementalCodeMerger.WriteStrategy.DIFF_PREVIEW.name().equalsIgnoreCase(getSelectedWriteStrategyCode());
        setOKButtonText(confirmFirst ? "查看 Diff 并确认" : CodeGenBundle.message("dialog.ok.button"));

        boolean sourceReady = currentSourceMode == DataSourceMode.OFFLINE_DDL
                ? !ddlNeedsParsing() && missingOfflineTables().isEmpty()
                : dbConnectionVerified && parsedDdlTables.isEmpty();
        boolean canExecute = tableCount > 0 && selectedOptions > 0 && sourceReady;
        setOKActionEnabled(canExecute);

        String strategyShortName = (String) writeStrategyCombo.getSelectedItem();
        if (tableCount == 0) {
            footerSummaryLabel.setText("待生成：0 张表 · 0 个文件 · 先勾选至少 1 张表");
            footerSummaryLabel.setForeground(SolidGlassTheme.warningFg());
            getOKAction().putValue(Action.SHORT_DESCRIPTION, "请先在下方列表勾选至少 1 张参与生成的表");
        } else if (selectedOptions == 0) {
            footerSummaryLabel.setText("待生成：" + tableCount + " 张表 · 0 个文件 · 先勾选至少 1 个生成目标");
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

    private void refreshFileImpactPreview() {
        if (filePreviewTableModel == null) {
            return;
        }
        filePreviewTableModel.setRowCount(0);
        previewRowAbsolutePaths.clear();
        viewDiffBtn.setEnabled(false);
        previewParticipates = true;
        checkPreviewTableBtn.setVisible(false);

        List<String> checked = getCheckedTableNames();
        String previewTable = activePreviewTableName;
        if (StringUtils.isBlank(previewTable) && !checked.isEmpty()) {
            previewTable = checked.get(0);
            activePreviewTableName = previewTable;
        }

        if (StringUtils.isBlank(previewTable)) {
            rightPreviewTitleLabel.setText("生成计划");
            rightPreviewTitleLabel.setToolTipText(null);
            rightPreviewDetailsLabel.setText("还没有生成表");
            fieldParseSummaryLabel.setText("加载表清单或添加表后，这里列出将写入的文件");
            return;
        }

        if (currentSourceMode == DataSourceMode.DATABASE && !dbConnectionVerified) {
            rightPreviewTitleLabel.setText(previewTable);
            rightPreviewTitleLabel.setToolTipText(previewTable);
            rightPreviewDetailsLabel.setText("连接未验证，生成计划待更新");
            fieldParseSummaryLabel.setText("旧的字段信息已失效");
            return;
        }
        previewParticipates = checked.stream().anyMatch(previewTable::equalsIgnoreCase);

        String relOutDir = computeRelativeOutputDir();
        outputDirReadOnlyField.setText(relOutDir);
        String className = CodeGenerateUtils.toUpper(previewTable, tablePrefixesField.getText().trim());
        String strategyCode = getSelectedWriteStrategyCode();

        Map<String, String[]> fixedLayerFiles = new LinkedHashMap<>();
        fixedLayerFiles.put("entity", new String[]{"entity/" + className + ".java"});
        fixedLayerFiles.put("entityBO", new String[]{"entity/bo/" + className + "BO.java"});
        fixedLayerFiles.put("entityVO", new String[]{"entity/vo/" + className + "VO.java"});
        fixedLayerFiles.put("entityQuery", new String[]{"entity/query/" + className + "Query.java"});
        fixedLayerFiles.put("excelDto", new String[]{"entity/excel/" + className + "ExcelDTO.java"});
        fixedLayerFiles.put("converter", new String[]{"entity/converter/" + className + "Converter.java"});
        fixedLayerFiles.put("mapper", new String[]{"mapper/" + className + "Mapper.java"});
        fixedLayerFiles.put("service", new String[]{
                "service/" + className + "Service.java",
                "service/impl/" + className + "ServiceImpl.java"
        });
        fixedLayerFiles.put("serviceQuery", new String[]{"service/query/" + className + "ServiceQuery.java"});
        fixedLayerFiles.put("serviceResults", new String[]{"service/results/" + className + "ServiceResults.java"});
        fixedLayerFiles.put("controller", new String[]{"controller/" + className + "Controller.java"});
        fixedLayerFiles.put("feignClient", new String[]{"feign/" + className + "FeignClient.java"});

        Map<String, Object> routingParamHolder = new HashMap<>();
        if (!customLayerModuleRouting.isEmpty()) {
            routingParamHolder.put("layerModuleRouting", customLayerModuleRouting);
        }

        int previewFileCount = 0;
        for (Map.Entry<String, String[]> entry : fixedLayerFiles.entrySet()) {
            String layerKey = entry.getKey();
            JBCheckBox cb = targetCheckBoxes.get(layerKey);
            if (cb != null && cb.isSelected()) {
                String routeKey = ("converter".equals(layerKey) || "excelDto".equals(layerKey)) ? "entityBO" : layerKey;
                String layerRoot = CodeGenerate.resolveLayerOutputRoot(project, projectRootPath, routeKey, routingParamHolder);
                String absBaseDir = layerRoot + relOutDir;
                String modulePrefix = resolveModuleDisplayPrefix(layerRoot);
                for (String relPath : entry.getValue()) {
                    previewFileCount++;
                    File diskFile = new File(absBaseDir, relPath);
                    String actionStatus = evaluateDiskFileActionStatus(diskFile, strategyCode);
                    filePreviewTableModel.addRow(new Object[]{modulePrefix + relPath, actionStatus});
                    previewRowAbsolutePaths.add(diskFile.getAbsolutePath());
                }
            }
        }

        JBCheckBox mapperXmlCb = targetCheckBoxes.get("mapperXml");
        if (mapperXmlCb != null && mapperXmlCb.isSelected()) {
            String layerRoot = CodeGenerate.resolveLayerOutputRoot(project, projectRootPath, "mapper", routingParamHolder);
            String modulePrefix = resolveModuleDisplayPrefix(layerRoot);
            String relXmlPath = "src/main/resources/mapper/" + className + "Mapper.xml";
            File diskFile = new File(layerRoot + relXmlPath);
            previewFileCount++;
            filePreviewTableModel.addRow(new Object[]{modulePrefix + "resources/mapper/" + className + "Mapper.xml", evaluateDiskFileActionStatus(diskFile, strategyCode)});
            previewRowAbsolutePaths.add(diskFile.getAbsolutePath());
        }

        JBCheckBox frontendCb = targetCheckBoxes.get("frontendApi");
        if (frontendCb != null && frontendCb.isSelected()) {
            String layerRoot = CodeGenerate.resolveLayerOutputRoot(project, projectRootPath, "controller", routingParamHolder);
            String modulePrefix = resolveModuleDisplayPrefix(layerRoot);
            String relTsPath = "src/main/resources/frontend/" + StringUtils.uncapitalize(className) + ".api.ts";
            File diskFile = new File(layerRoot + relTsPath);
            previewFileCount++;
            filePreviewTableModel.addRow(new Object[]{modulePrefix + "resources/frontend/" + StringUtils.uncapitalize(className) + ".api.ts", evaluateDiskFileActionStatus(diskFile, strategyCode)});
            previewRowAbsolutePaths.add(diskFile.getAbsolutePath());
        }

        JBCheckBox frontendVueCb = targetCheckBoxes.get("frontendVue");
        if (frontendVueCb != null && frontendVueCb.isSelected()) {
            String layerRoot = CodeGenerate.resolveLayerOutputRoot(project, projectRootPath, "controller", routingParamHolder);
            String modulePrefix = resolveModuleDisplayPrefix(layerRoot);
            String relVuePath = "src/main/resources/frontend/" + StringUtils.uncapitalize(className) + "/index.vue";
            File diskFile = new File(layerRoot + relVuePath);
            previewFileCount++;
            filePreviewTableModel.addRow(new Object[]{modulePrefix + "resources/frontend/" + StringUtils.uncapitalize(className) + "/index.vue", evaluateDiskFileActionStatus(diskFile, strategyCode)});
            previewRowAbsolutePaths.add(diskFile.getAbsolutePath());
        }

        JBCheckBox httpCb = targetCheckBoxes.get("httpClient");
        if (httpCb != null && httpCb.isSelected()) {
            String layerRoot = CodeGenerate.resolveLayerOutputRoot(project, projectRootPath, "controller", routingParamHolder);
            String modulePrefix = resolveModuleDisplayPrefix(layerRoot);
            String relHttpPath = "src/test/resources/http/" + className + "ApiTest.http";
            File diskFile = new File(layerRoot + relHttpPath);
            previewFileCount++;
            filePreviewTableModel.addRow(new Object[]{modulePrefix + "test/resources/http/" + className + "ApiTest.http", evaluateDiskFileActionStatus(diskFile, strategyCode)});
            previewRowAbsolutePaths.add(diskFile.getAbsolutePath());
        }

        JBCheckBox unitTestCb = targetCheckBoxes.get("unitTest");
        if (unitTestCb != null && unitTestCb.isSelected()) {
            String layerRoot = CodeGenerate.resolveLayerOutputRoot(project, projectRootPath, "service", routingParamHolder);
            String modulePrefix = resolveModuleDisplayPrefix(layerRoot);
            String relTestDir = relOutDir.replaceFirst("^src/main/java/", "src/test/java/");
            String relTestFile = "service/" + className + "ServiceTest.java";
            File diskFile = new File(layerRoot + relTestDir, relTestFile);
            previewFileCount++;
            filePreviewTableModel.addRow(new Object[]{modulePrefix + "test/" + relTestFile, evaluateDiskFileActionStatus(diskFile, strategyCode)});
            previewRowAbsolutePaths.add(diskFile.getAbsolutePath());
        }

        JBCheckBox flywayCb = targetCheckBoxes.get("flywayMigration");
        if (flywayCb != null && flywayCb.isSelected()) {
            String layerRoot = CodeGenerate.resolveLayerOutputRoot(project, projectRootPath, "mapper", routingParamHolder);
            String modulePrefix = resolveModuleDisplayPrefix(layerRoot);
            String relSqlPath = "src/main/resources/db/migration/V1__init_" + previewTable.toLowerCase(Locale.ROOT) + ".sql";
            File diskFile = new File(layerRoot + relSqlPath);
            previewFileCount++;
            filePreviewTableModel.addRow(new Object[]{modulePrefix + "resources/db/migration/V1__init_" + previewTable.toLowerCase(Locale.ROOT) + ".sql", evaluateDiskFileActionStatus(diskFile, strategyCode)});
            previewRowAbsolutePaths.add(diskFile.getAbsolutePath());
        }

        // 附加：在离线 DDL 模式下提供快速调用方法
        if (Boolean.FALSE) {
            promptNaturalLanguageDdl();
            openDdlEvolutionDiffDialog();
        }

        JBCheckBox enumCb = targetCheckBoxes.get("enums");
        List<String> resolvedEnums = getResolvedEnumClassNamesForTable(previewTable);
        if (enumCb != null && enumCb.isSelected()) {
            String enumRoot = CodeGenerate.resolveLayerOutputRoot(project, projectRootPath, "enums", routingParamHolder);
            String absEnumBase = enumRoot + relOutDir;
            String modulePrefix = resolveModuleDisplayPrefix(enumRoot);
            if (resolvedEnums == null) {
                filePreviewTableModel.addRow(new Object[]{
                        modulePrefix + "entity/enums/" + className + "*Enum.java",
                        "待读取"
                });
                previewRowAbsolutePaths.add("");
            } else if (resolvedEnums.isEmpty()) {
                filePreviewTableModel.addRow(new Object[]{
                        modulePrefix + "entity/enums/（无枚举注释列）",
                        "跳过（无枚举列）"
                });
                previewRowAbsolutePaths.add("");
            } else {
                for (String enumFileName : resolvedEnums) {
                    previewFileCount++;
                    String relPath = "entity/enums/" + enumFileName;
                    File diskFile = new File(absEnumBase, relPath);
                    String actionStatus = evaluateDiskFileActionStatus(diskFile, strategyCode);
                    filePreviewTableModel.addRow(new Object[]{modulePrefix + relPath, actionStatus});
                    previewRowAbsolutePaths.add(diskFile.getAbsolutePath());
                }
            }
        }

        int overwriteCount = 0;
        for (int i = 0; i < filePreviewTableModel.getRowCount(); i++) {
            if (!previewParticipates) {
                filePreviewTableModel.setValueAt("不参与", i, 1);
                continue;
            }
            String status = Objects.toString(filePreviewTableModel.getValueAt(i, 1), "");
            if (status.startsWith("覆盖") || status.startsWith("冲突")) {
                overwriteCount++;
            }
        }
        String impact = overwriteCount > 0 ? " · 其中 " + overwriteCount + " 个将覆盖" : "";
        String enumPending = enumCb != null && enumCb.isSelected() && resolvedEnums == null ? " · 枚举待读取" : "";
        rightPreviewTitleLabel.setText(previewTable + " → " + className);
        rightPreviewTitleLabel.setToolTipText(previewTable + " (" + className + ")");
        checkPreviewTableBtn.setVisible(!previewParticipates);
        if (currentSourceMode == DataSourceMode.OFFLINE_DDL && ddlNeedsParsing()) {
            rightPreviewDetailsLabel.setText("SQL 已修改，以下为上次解析结果 · 生成计划待更新");
        } else if (!previewParticipates) {
            rightPreviewDetailsLabel.setText("未勾选 · 勾选后预计 " + previewFileCount + " 个文件");
        } else {
            rightPreviewDetailsLabel.setText("预计 " + previewFileCount + " 个文件" + impact + enumPending);
        }

        List<Table> cols = getResolvedColumnsForTable(previewTable);
        if (cols != null) {
            ValidationAnnotationResolver.enrichValidationAnnotations(cols, enableValidationCheckBox.isSelected());
            long valCount = cols.stream().filter(c -> c.getValidationAnnotations() != null && !c.getValidationAnnotations().isEmpty()).count();
            int enumCount = resolvedEnums != null ? resolvedEnums.size() : 0;
            fieldParseSummaryLabel.setText("共 " + cols.size() + " 列 · " + enumCount + " 个枚举列 · " + valCount + " 列含校验注解");
        } else {
            fieldParseSummaryLabel.setText(columnsLoading ? "正在读取字段…" : "字段待读取");
        }
        filePreviewTable.repaint();
    }

    private String resolveModuleDisplayPrefix(String layerRoot) {
        String normDefault = CodeGenerateUtils.normalizeDirPath(projectRootPath);
        String normLayer = CodeGenerateUtils.normalizeDirPath(layerRoot);
        if (normDefault.equalsIgnoreCase(normLayer)) {
            return "";
        }
        File dir = new File(normLayer);
        return "[" + dir.getName() + "] ";
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
                        dbPageStatusBadge.setStatus("连接成功 (" + tables.size() + " 张表)", GlyphState.READY);
                        populateMasterTablesFromDbList(tables);
                        tableJList.requestFocusInWindow();
                        readActiveTableColumnsIfMissing();
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

    private void readActiveTableColumnsIfMissing() {
        if (currentSourceMode == DataSourceMode.DATABASE && dbConnectionVerified
                && StringUtils.isNotBlank(activePreviewTableName)
                && getResolvedColumnsForTable(activePreviewTableName) == null) {
            previewSelectedTableEnumsAsync();
        }
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
        columnsLoading = true;
        refreshFileImpactPreview();
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Parsing Table Metadata...", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try (Connection conn = CodeGenerateUtils.getDblink(project, projectRootPath)) {
                    List<Table> columns = CodeGenerateUtils.getList(conn, targetTable, databaseName);
                    ValidationAnnotationResolver.enrichValidationAnnotations(columns, enableValidationCheckBox.isSelected());
                    ApplicationManager.getApplication().invokeLater(() -> {
                        columnsLoading = false;
                        if (requestVersion != databaseConfigVersion || currentSourceMode != DataSourceMode.DATABASE) return;
                        resolvedTableColumnsCache.put(targetTable.toLowerCase(Locale.ROOT), columns);
                        updateSummaryAndSourceIndicators();
                        refreshFileImpactPreview();
                    });
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        columnsLoading = false;
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
            if (subTableRelationModel != null) {
                subTableRelationModel.setRowCount(0);
            }
            return;
        }

        queryPageTargetTableLabel.setText("规则作用于：" + tableKey);
        if (reloadQueryCacheBtn != null) {
            reloadQueryCacheBtn.setText("重载 [" + tableKey + "] 缓存");
        }

        beanQueryTableModel.setRowCount(0);
        serviceQueryTableModel.setRowCount(0);
        if (subTableRelationModel != null) {
            subTableRelationModel.setRowCount(0);
        }

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

        if (subTableRelationModel != null) {
            List<SubTableRelation> relations = subTableRelationDrafts.containsKey(tableKey)
                    ? subTableRelationDrafts.get(tableKey) : settingsService.getSubTableRelations(tableKey);
            for (SubTableRelation rel : relations) {
                subTableRelationModel.addRow(new Object[]{
                        StringUtils.defaultString(rel.getSubTableName()),
                        StringUtils.defaultString(rel.getForeignKeyColumn()),
                        StringUtils.defaultIfBlank(rel.getMasterKeyProperty(), "id"),
                        StringUtils.defaultString(rel.getRelationComment())
                });
            }
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
            state.tablePrefixes = tablePrefixesField.getText().trim();
            state.baseEntityClass = baseEntityField.getText().trim();
            state.feignServiceName = feignServiceNameField.getText().trim();
            state.responseWrapperClass = responseWrapperField.getText().trim();
            state.layerModuleRouting.clear();
            state.layerModuleRouting.putAll(customLayerModuleRouting);
            state.writeStrategy = getSelectedWriteStrategyCode();
            state.docStyle = getSelectedDocStyleCode();
            state.mapperStrategy = getSelectedMapperStrategyCode();
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

    private String getSelectedMapperStrategyCode() {
        int idx = mapperStrategyCombo.getSelectedIndex();
        return (idx >= 0 && idx < MAPPER_STRATEGY_CODES.length) ? MAPPER_STRATEGY_CODES[idx] : MAPPER_STRATEGY_CODES[0];
    }

    private void selectMapperStrategyComboByCode(String code) {
        for (int i = 0; i < MAPPER_STRATEGY_CODES.length; i++) {
            if (MAPPER_STRATEGY_CODES[i].equalsIgnoreCase(code)) {
                mapperStrategyCombo.setSelectedIndex(i);
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
        if (subTableRelationTable != null && subTableRelationTable.isEditing()) {
            subTableRelationTable.getCellEditor().stopCellEditing();
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
        params.put("packageName", StringUtils.defaultIfBlank(packageField.getText().trim(), "com.example"));
        params.put("tablePrefixes", tablePrefixesField.getText().trim());
        params.put("baseEntityClass", baseEntityField.getText().trim());
        params.put("feignServiceName", feignServiceNameField.getText().trim());
        params.put("responseWrapperClass", responseWrapperField.getText().trim());
        if (!customLayerModuleRouting.isEmpty()) {
            params.put("layerModuleRouting", new LinkedHashMap<>(customLayerModuleRouting));
        } else {
            params.remove("layerModuleRouting");
        }
        params.put("include", tableKey);
        params.put("writeStrategy", getSelectedWriteStrategyCode());
        params.put("docStyle", getSelectedDocStyleCode());
        params.put("mapperStrategy", getSelectedMapperStrategyCode());
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
        subTableRelationDrafts.forEach(settingsService::saveSubTableRelations);

        Map<String, List<BeanQuery>> beanQueriesByTable = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, List<ServiceQuery>> serviceQueriesByTable = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, List<SubTableRelation>> subRelationsByTable = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String table : getCheckedTableNames()) {
            beanQueriesByTable.put(table, settingsService.getBeanQueryList(table, projectRootPath));
            serviceQueriesByTable.put(table, settingsService.getServiceQueryList(table, projectRootPath));
            subRelationsByTable.put(table, settingsService.getSubTableRelations(table));
        }
        params.put("beanQueryByTable", beanQueriesByTable);
        params.put("serviceQueryListByTable", serviceQueriesByTable);
        params.put("subTableRelationsByTable", subRelationsByTable);
        params.put("beanQuery", beanQueryDrafts.getOrDefault(loadedQueryTableName, Collections.emptyList()));
        params.put("serviceQueryList", serviceQueryDrafts.getOrDefault(loadedQueryTableName, Collections.emptyList()));
        params.put("subTableRelations", subTableRelationDrafts.getOrDefault(loadedQueryTableName, Collections.emptyList()));

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
        if (subTableRelationTable != null && subTableRelationTable.isEditing()) {
            subTableRelationTable.getCellEditor().stopCellEditing();
        }
        List<BeanQuery> beanQueries = new ArrayList<>();
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

        List<ServiceQuery> serviceQueries = new ArrayList<>();
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

        if (subTableRelationModel != null) {
            List<SubTableRelation> relations = new ArrayList<>();
            for (int i = 0; i < subTableRelationModel.getRowCount(); i++) {
                String subTbl = StringUtils.trimToEmpty(String.valueOf(subTableRelationModel.getValueAt(i, 0)));
                String fkCol = StringUtils.trimToEmpty(String.valueOf(subTableRelationModel.getValueAt(i, 1)));
                String mkProp = StringUtils.defaultIfBlank(StringUtils.trimToEmpty(String.valueOf(subTableRelationModel.getValueAt(i, 2))), "id");
                String comment = StringUtils.trimToEmpty(String.valueOf(subTableRelationModel.getValueAt(i, 3)));
                if (StringUtils.isNoneBlank(subTbl, fkCol)) {
                    SubTableRelation rel = new SubTableRelation(subTbl, fkCol, mkProp, comment);
                    rel.recomputeDerivedNames(tablePrefixesField.getText().trim());
                    relations.add(rel);
                }
            }
            subTableRelationDrafts.put(loadedQueryTableName, relations);
        }
    }

    private void autoInferQueryRulesForCurrentTable() {
        String tableKey = StringUtils.defaultIfBlank(loadedQueryTableName, activePreviewTableName);
        if (StringUtils.isBlank(tableKey)) {
            Messages.showInfoMessage(project, "请先在「生成配置与选表」页选择目标表。", "智能推导查询规则");
            return;
        }
        List<Table> cols = getResolvedColumnsForTable(tableKey);
        if (cols == null || cols.isEmpty()) {
            Messages.showInfoMessage(project, "当前表 [" + tableKey + "] 尚未加载字段元数据，请先在选表页点击右上角「刷新预览」或解析离线 DDL。", "智能推导查询规则");
            return;
        }
        SmartQueryRuleInferencer.InferredQueryRules inferred = SmartQueryRuleInferencer.inferFromColumns(cols);
        beanQueryTableModel.setRowCount(0);
        for (BeanQuery bq : inferred.getBeanQueries()) {
            beanQueryTableModel.addRow(new Object[]{
                    StringUtils.defaultString(bq.getName()),
                    StringUtils.defaultIfBlank(bq.getFormat(), "String"),
                    StringUtils.defaultString(bq.getDescription())
            });
        }
        serviceQueryTableModel.setRowCount(0);
        for (ServiceQuery sq : inferred.getServiceQueries()) {
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
        saveCurrentQueryDraft();
    }

    private void autoDiscoverSubTablesForCurrentTable() {
        String tableKey = StringUtils.defaultIfBlank(loadedQueryTableName, activePreviewTableName);
        if (StringUtils.isBlank(tableKey)) {
            Messages.showInfoMessage(project, "请先在「生成配置与选表」页选择主表。", "自动识别子表关联");
            return;
        }
        Map<String, List<Table>> columnsByTable = new LinkedHashMap<>(resolvedTableColumnsCache);
        for (DdlSqlParser.ParsedTable pt : parsedDdlTables.values()) {
            columnsByTable.put(pt.getTableName(), pt.getColumns());
        }
        List<SubTableRelation> discovered = RelationTopologyDetector.discoverSubTableRelations(
                tableKey,
                parsedDdlTables,
                columnsByTable,
                tablePrefixesField.getText().trim()
        );
        if (discovered.isEmpty()) {
            Messages.showInfoMessage(project, "未在已加载的表结构中识别到指向 [" + tableKey + "] 的一对多子表外键。", "自动识别子表关联");
            return;
        }
        subTableRelationModel.setRowCount(0);
        for (SubTableRelation rel : discovered) {
            subTableRelationModel.addRow(new Object[]{
                    StringUtils.defaultString(rel.getSubTableName()),
                    StringUtils.defaultString(rel.getForeignKeyColumn()),
                    StringUtils.defaultIfBlank(rel.getMasterKeyProperty(), "id"),
                    StringUtils.defaultString(rel.getRelationComment())
            });
        }
        saveCurrentQueryDraft();
    }

    private void promptNaturalLanguageDdl() {
        String defaultPrompt =
                "创建商品库存表 product_stock：包含仓库编码、商品名称、库存数量、预警阈值、单价金额、库存状态（0：正常，1：缺货，2：冻结）、负责人手机号、负责人邮箱\n\n" +
                "创建商品库存明细表 product_stock_item：包含库存ID product_stock_id、批次编号、变动数量、备注说明";
        JBTextArea promptArea = new JBTextArea(defaultPrompt, 7, 56);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setBackground(SolidGlassTheme.inputBg());
        promptArea.setForeground(SolidGlassTheme.textPrimary());
        JBScrollPane scroll = new JBScrollPane(promptArea);
        SolidGlassTheme.styleScrollPane(scroll);

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JBLabel("请输入业务实体描述（支持中英文、枚举括号注释与多段主子表描述）："), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        int res = JOptionPane.showConfirmDialog(
                getContentPane(),
                panel,
                "自然语言转标准 DDL SQL 智能建模",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (res != JOptionPane.OK_OPTION) {
            return;
        }
        String inferredDdl = NaturalLanguageDdlInferenceEngine.inferDdlFromNaturalLanguage(promptArea.getText());
        if (StringUtils.isNotBlank(inferredDdl)) {
            ddlSqlPane.setText(inferredDdl);
            parseDdlAndPopulateTables(false);
        }
    }

    private void openDdlEvolutionDiffDialog() {
        String currentDdl = ddlSqlPane.getText();
        if (StringUtils.isBlank(currentDdl)) {
            Messages.showInfoMessage(project, "请先在当前 DDL 编辑器中输入或生成新版 CREATE TABLE 语句。", "表结构演进 Diff SQL");
            return;
        }
        JBTextArea oldDdlArea = new JBTextArea(8, 58);
        oldDdlArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        oldDdlArea.setBackground(SolidGlassTheme.inputBg());
        oldDdlArea.setForeground(SolidGlassTheme.textPrimary());
        oldDdlArea.setText("-- 粘贴旧版 CREATE TABLE 语句（留空则直接按新版生成建表脚本）\n");

        ComboBox<DatabaseDialect> dialectCombo = new ComboBox<>(DatabaseDialect.values());
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        topRow.add(new JBLabel("目标数据库方言:"));
        topRow.add(dialectCombo);

        JPanel inputPanel = new JPanel(new BorderLayout(0, 6));
        inputPanel.add(topRow, BorderLayout.NORTH);
        inputPanel.add(new JBScrollPane(oldDdlArea), BorderLayout.CENTER);

        int res = JOptionPane.showConfirmDialog(
                getContentPane(),
                inputPanel,
                "对比旧版 DDL 生成增量 ALTER TABLE 迁移脚本",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (res != JOptionPane.OK_OPTION) {
            return;
        }
        DatabaseDialect dialect = (DatabaseDialect) Objects.requireNonNullElse(dialectCombo.getSelectedItem(), DatabaseDialect.MYSQL);
        String migrationSql = DdlMigrationGenerator.generateScriptDiffMigration(oldDdlArea.getText(), currentDdl, dialect);

        JBTextArea outputArea = new JBTextArea(migrationSql, 14, 64);
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        outputArea.setBackground(SolidGlassTheme.inputBg());
        outputArea.setForeground(SolidGlassTheme.textPrimary());
        JOptionPane.showMessageDialog(
                getContentPane(),
                new JBScrollPane(outputArea),
                "增量迁移 SQL 预览 (" + dialect.name() + ")",
                JOptionPane.PLAIN_MESSAGE
        );
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
