package com.code.ui;

import com.code.config.CodeGenSettingsState;
import com.code.utils.CodeGenerate;
import com.code.utils.DdlSqlParser;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CodeGenerateDialogRegressionTest extends BasePlatformTestCase {
    private static final String DDL = "CREATE TABLE user_order (id bigint, order_no varchar(32), "
            + "status int COMMENT '状态（0：待支付，1：已支付）'); "
            + "CREATE TABLE articles (id bigint, title varchar(64));";
    private CodeGenerateDialog dialog;
    private Map<String, Object> params;
    private Path outputRoot;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        CodeGenSettingsState.getInstance(getProject()).loadState(new CodeGenSettingsState.ConfigState());
        outputRoot = Files.createTempDirectory("codegen-regression-");
        params = new HashMap<>();
        params.put("preloadedDdlScript", DDL);
        params.put("entityQuery", "y");
        params.put("serviceQuery", "y");
        params.put("enums", "y");
        dialog = new CodeGenerateDialog(getProject(), outputRoot + "/", params, "Regression");
        ((JTextField) field("moduleField")).setText("demo");
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (dialog != null) dialog.close(1);
            if (outputRoot != null) FileUtil.delete(outputRoot.toFile());
            CodeGenSettingsState.getInstance(getProject()).loadState(new CodeGenSettingsState.ConfigState());
        } finally {
            super.tearDown();
        }
    }

    public void testBatchGenerationKeepsEachTablesOwnQueryRules() throws Exception {
        DefaultTableModel beans = field("beanQueryTableModel");
        DefaultTableModel queries = field("serviceQueryTableModel");
        beans.addRow(new Object[]{"orderKeyword", "String", "订单搜索"});
        queries.addRow(new Object[]{"1:模糊查询", "order_no", "orderKeyword", true, "", "", ""});
        setField("activePreviewTableName", "articles");
        call("reloadQueryTablesForCurrentTable");
        beans.addRow(new Object[]{"articleKeyword", "String", "文章搜索"});
        queries.addRow(new Object[]{"1:模糊查询", "title", "articleKeyword", true, "", "", ""});
        call("doOKAction");

        generate("user_order");
        generate("articles");
        String orderQuery = generated("entity/query/UserOrderQuery.java");
        String articleQuery = generated("entity/query/ArticlesQuery.java");
        assertTrue(orderQuery.contains("orderKeyword"));
        assertFalse(orderQuery.contains("articleKeyword"));
        assertTrue(articleQuery.contains("articleKeyword"));
        assertFalse(articleQuery.contains("orderKeyword"));
        String orderService = generated("service/query/UserOrderServiceQuery.java");
        String articleService = generated("service/query/ArticlesServiceQuery.java");
        assertTrue(orderService.contains("getOrderKeyword()"));
        assertFalse(orderService.contains("getArticleKeyword()"));
        assertTrue(articleService.contains("getArticleKeyword()"));
        assertFalse(articleService.contains("getOrderKeyword()"));
    }

    public void testTableWithoutRulesDoesNotInheritCurrentTablesRules() throws Exception {
        ((DefaultTableModel) field("beanQueryTableModel")).addRow(new Object[]{"orderKeyword", "String", "订单搜索"});
        call("doOKAction");
        generate("articles");
        assertFalse(generated("entity/query/ArticlesQuery.java").contains("orderKeyword"));
    }

    public void testOfflineUiRejectsTableMissingFromDdlWithoutFetchingMetadata() throws Exception {
        Map<String, CodeGenerateDialog.TableItem> tables = field("masterTableMap");
        tables.put("unknown_table", new CodeGenerateDialog.TableItem("unknown_table", "", true));
        setField("activePreviewTableName", "unknown_table");
        call("syncCheckedTablesToSummaryField");
        assertFalse(dialog.isOKActionEnabled());
        assertNotNull(call("doValidate"));
        int version = field("databaseConfigVersion");
        call("previewSelectedTableEnumsAsync");
        assertEquals(version, (int) field("databaseConfigVersion"));
        assertFalse((boolean) field("dbConnectionVerified"));
    }

    public void testOfflineGeneratorRejectsMissingTableBeforeJdbcFallback() throws Exception {
        params.put("parsedDdlTables", DdlSqlParser.parseDdlScript(DDL));
        try {
            generate("unknown_table");
            fail("Offline generation must reject tables missing from DDL");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("离线 DDL 未包含表：unknown_table"));
        }
        params.put("parsedDdlTables", Map.of());
        try {
            generate("user_order");
            fail("An empty offline map must not fall back to JDBC");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("离线 DDL"));
        }
    }

    public void testEveryConnectionFieldInvalidatesPreviewUntilReload() throws Exception {
        List<?> oldColumns = DdlSqlParser.parseDdlScript(DDL).get("user_order").getColumns();
        for (String fieldName : List.of("jdbcUrlField", "databaseField", "usernameField", "passwordField")) {
            call("populateMasterTablesFromDbList", new Class<?>[]{List.class}, List.of("user_order"));
            Map<String, Object> cache = field("resolvedTableColumnsCache");
            cache.put("user_order", oldColumns);
            call("refreshFileImpactPreview");
            assertTrue(dialog.isOKActionEnabled());
            int previousVersion = field("databaseConfigVersion");
            ((JTextField) field(fieldName)).setText("changed-for-test-" + fieldName);
            assertTrue(fieldName, cache.isEmpty());
            assertFalse(fieldName, dialog.isOKActionEnabled());
            assertNotNull(call("doValidate"));
            assertEquals(0, ((DefaultTableModel) field("filePreviewTableModel")).getRowCount());
            assertTrue((int) field("databaseConfigVersion") > previousVersion);
            call("populateMasterTablesFromDbList", new Class<?>[]{List.class}, List.of("user_order"));
            assertTrue(dialog.isOKActionEnabled());
        }
    }

    public void testEditingBackupConnectionKeepsOfflineDdlUsable() throws Exception {
        ((JTextField) field("jdbcUrlField")).setText("jdbc:mysql://127.0.0.1/backup");
        assertTrue(dialog.isOKActionEnabled());
        assertNull(call("doValidate"));
        assertNotNull(call("getResolvedColumnsForTable", new Class<?>[]{String.class}, "USER_ORDER"));
    }

    private void generate(String table) throws Exception {
        CodeGenerate.foundCode(getProject(), table, "demo", "regression", params, outputRoot + "/");
    }

    private String generated(String relativePath) throws Exception {
        return Files.readString(outputRoot.resolve("src/main/java/com/example/demo/" + relativePath));
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
