package com.code.ui;

import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class CodeGenerateDialogTest extends BasePlatformTestCase {
    private static final String DDL = "CREATE TABLE user_order (id bigint PRIMARY KEY, "
            + "status int COMMENT '状态（0：待支付，1：已支付）');";
    private CodeGenerateDialog dialog;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Map<String, Object> params = new HashMap<>();
        params.put("preloadedDdlScript", DDL);
        params.put("entity", "y");
        params.put("enums", "y");
        dialog = new CodeGenerateDialog(getProject(), myFixture.getTempDirFixture().getTempDirPath() + "/", params, "Code Generate");
        ((JTextField) field("moduleField")).setText("demo");
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (dialog != null) dialog.close(1);
        } finally {
            super.tearDown();
        }
    }

    public void testZeroSelectionDoesNotRecheckTablesDuringValidation() throws Exception {
        Map<?, ?> items = field("masterTableMap");
        for (Object item : items.values()) ((CodeGenerateDialog.TableItem) item).setChecked(false);
        call("syncCheckedTablesToSummaryField");
        assertFalse(dialog.isOKActionEnabled());
        assertTrue(((JLabel) field("footerSummaryLabel")).getText().contains("0 张表 · 0 个文件"));
        ValidationInfo validation = call("doValidate");
        assertNotNull(validation);
        assertFalse(((CodeGenerateDialog.TableItem) items.values().iterator().next()).isChecked());
    }

    public void testSqlEditInvalidatesGenerationAndReparseRestoresIt() throws Exception {
        assertTrue(dialog.isOKActionEnabled());
        JTextPane editor = field("ddlSqlPane");
        editor.setText(DDL.replace("status int", "state int"));
        assertFalse(dialog.isOKActionEnabled());
        assertTrue(((JLabel) field("rightPreviewDetailsLabel")).getText().contains("上次解析结果"));
        assertNotNull(call("doValidate"));
        call("parseDdlAndPopulateTables", new Class<?>[]{boolean.class}, false);
        assertTrue(dialog.isOKActionEnabled());
        assertNull(call("doValidate"));
        assertTrue(((JLabel) field("footerSummaryLabel")).getText().contains("计划生成 2 个文件"));
        editor.setText("invalid SQL");
        call("parseDdlAndPopulateTables", new Class<?>[]{boolean.class}, false);
        editor.setText(DDL);
        assertFalse(dialog.isOKActionEnabled());
        assertNotNull(call("doValidate"));
        call("parseDdlAndPopulateTables", new Class<?>[]{boolean.class}, false);
        assertTrue(dialog.isOKActionEnabled());
    }

    public void testQueryDraftsSurviveTableAndTabChanges() throws Exception {
        DefaultTableModel beans = field("beanQueryTableModel");
        DefaultTableModel queries = field("serviceQueryTableModel");
        beans.addRow(new Object[]{"keyword", "String", "关键词"});
        queries.addRow(new Object[]{"1:模糊查询", "status", "status", true, "", "", ""});
        call("reloadQueryTablesForCurrentTable");
        assertEquals(1, beans.getRowCount());
        setField("activePreviewTableName", "other_table");
        call("reloadQueryTablesForCurrentTable");
        assertEquals(0, beans.getRowCount());
        beans.addRow(new Object[]{"other", "Long", "另一张表"});
        setField("activePreviewTableName", "user_order");
        call("reloadQueryTablesForCurrentTable");
        assertEquals("keyword", beans.getValueAt(0, 0));
        assertEquals("status", queries.getValueAt(0, 1));
        ((JButton) field("reloadQueryCacheBtn")).doClick();
        assertEquals(0, beans.getRowCount());
        assertEquals(0, queries.getRowCount());
    }

    public void testListFitsViewportAndPreviewDoesNotImplicitlyCheckTable() throws Exception {
        JList<?> list = field("tableJList");
        list.setSize(280, 200);
        assertTrue(list.getScrollableTracksViewportWidth());
        Map<?, ?> items = field("masterTableMap");
        ((CodeGenerateDialog.TableItem) items.values().iterator().next()).setChecked(false);
        call("syncCheckedTablesToSummaryField");
        list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_PRESSED, 0, 0, 100, 10, 1, false));
        assertFalse(dialog.isOKActionEnabled());
        assertTrue(list.getToolTipText(new MouseEvent(list, MouseEvent.MOUSE_MOVED, 0, 0, 100, 10, 0, false)).contains("user_order"));
    }

    public void testLongPreviewTitleLeavesRoomForRefreshButton() throws Exception {
        setField("activePreviewTableName", "very_long_table_name_".repeat(15));
        call("refreshFileImpactPreview");
        JLabel title = field("rightPreviewTitleLabel");
        JPanel header = (JPanel) title.getParent();
        header.setSize(400, 40);
        header.doLayout();
        JButton refresh = (JButton) ((BorderLayout) header.getLayout()).getLayoutComponent(BorderLayout.EAST);
        assertTrue(title.getBounds().getMaxX() <= refresh.getX());
        assertEquals("刷新预览", refresh.getText());
        assertNotNull(title.getToolTipText());
    }

    public void testZeroTargetsDisablesGeneration() throws Exception {
        Map<?, ?> targets = field("targetCheckBoxes");
        for (Object target : targets.values()) ((AbstractButton) target).setSelected(false);
        call("updateSummaryAndSourceIndicators");
        assertFalse(dialog.isOKActionEnabled());
    }

    public void testSourceSwitchRequiresMatchingTables() throws Exception {
        Class<?> mode = CodeGenerateDialog.class.getDeclaredField("currentSourceMode").getType();
        setField("currentSourceMode", mode.getEnumConstants()[0]);
        call("updateSummaryAndSourceIndicators");
        assertFalse(dialog.isOKActionEnabled());
        assertNotNull(call("doValidate"));
        call("populateMasterTablesFromDbList", new Class<?>[]{java.util.List.class}, java.util.List.of("users (用户表)"));
        Map<?, ?> parsed = field("parsedDdlTables");
        assertTrue(parsed.isEmpty());
        assertEquals("users", field("activePreviewTableName"));
        assertEquals("规则作用于：users", ((JLabel) field("queryPageTargetTableLabel")).getText());
        assertNull(call("getResolvedColumnsForTable", new Class<?>[]{String.class}, "user_order"));
    }

    public void testPasswordChangeInvalidatesConnectionVerification() throws Exception {
        setField("dbConnectionVerified", true);
        ((JPasswordField) field("passwordField")).setText("test-only");
        assertEquals(Boolean.FALSE, field("dbConnectionVerified"));
    }

    public void testRenderWideAndCompactLayouts() throws Exception {
        JTabbedPane tabs = field("mainTabbedPane");
        JComponent root = (JComponent) tabs.getParent();
        root.addNotify();
        File output = new File("build/reports/ui-preview");
        assertTrue(output.isDirectory() || output.mkdirs());
        for (int width : new int[]{910, 1200}) {
            root.setSize(width, width == 910 ? 690 : 820);
            for (int tab = 0; tab < tabs.getTabCount(); tab++) {
                tabs.setSelectedIndex(tab);
                layoutTree(root);
                root.validate();
                BufferedImage image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                try {
                    root.printAll(graphics);
                } finally {
                    graphics.dispose();
                }
                ImageIO.write(image, "png", new File(output, width + "-tab-" + tab + ".png"));
            }
        }
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component component : container.getComponents()) {
            if (component instanceof Container child) layoutTree(child);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T field(String name) throws Exception {
        Field field = CodeGenerateDialog.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(dialog);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = CodeGenerateDialog.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(dialog, value);
    }

    private <T> T call(String name) throws Exception {
        return call(name, new Class<?>[0]);
    }

    @SuppressWarnings("unchecked")
    private <T> T call(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = CodeGenerateDialog.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return (T) method.invoke(dialog, args);
    }
}
