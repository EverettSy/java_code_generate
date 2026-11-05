package com.code.utils;

import com.code.config.CodeGenSettingsState;
import com.code.utils.entity.Table;
import com.intellij.openapi.project.Project;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User:    syrobin
 * Date:    2016/2/1
 * Time:    17:55
 * details: 代码生成工具类（P2：支持 MySQL/PostgreSQL/Oracle/DM8 多数据库方言与 Spring 配置识别）
 */
public class CodeGenerateUtils {

    /**
     * @param rs ResultSet转list
     * @return List<Map<String, Object>>
     * @throws SQLException
     */
    public static List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
        if (rs == null) {
            return Collections.emptyList();
        }
        ResultSetMetaData md = rs.getMetaData();
        int columnCount = md.getColumnCount();
        List<Map<String, Object>> list = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> rowData = new HashMap<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                rowData.put(md.getColumnName(i), rs.getObject(i));
            }
            list.add(rowData);
        }
        return list;
    }

    /**
     * 获取数据库中所有表名列表（支持多方言）
     */
    public static List<String> getAllTableNames(Connection conn, String database) throws SQLException {
        DatabaseDialect dialect = detectDialect(conn);
        return new ArrayList<>(dialect.queryTablesWithComments(conn, database).keySet());
    }

    /**
     * 获取数据库中所有表名及表注释（支持多方言，格式：table_name (表注释)）
     */
    public static List<String> getAllTablesWithComments(Connection conn, String database) throws SQLException {
        DatabaseDialect dialect = detectDialect(conn);
        Map<String, String> map = dialect.queryTablesWithComments(conn, database);
        List<String> tables = new ArrayList<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (StringUtils.isNotBlank(entry.getValue())) {
                tables.add(entry.getKey() + " (" + entry.getValue().trim() + ")");
            } else {
                tables.add(entry.getKey());
            }
        }
        return tables;
    }

    /**
     * 获取表字段元数据信息
     */
    public static List<Table> getList(String path, String table_name, String database) throws Exception {
        try (Connection conn = getDblink(null, path)) {
            return getList(conn, table_name, database);
        }
    }

    /**
     * 复用已有数据库连接获取表字段元数据信息（自动适配多数据库方言）
     */
    public static List<Table> getList(Connection conn, String table_name, String database) throws SQLException {
        DatabaseDialect dialect = detectDialect(conn);
        return dialect.queryColumns(conn, database, table_name);
    }

    public static DatabaseDialect detectDialect(Connection conn) {
        try {
            String url = conn.getMetaData().getURL();
            return DatabaseDialect.fromJdbcUrl(url);
        } catch ( Exception e) {
            return DatabaseDialect.MYSQL;
        }
    }

    /**
     * 获取数据库连接（兼容旧签名）
     */
    public static Connection getDblink(String path) throws Exception {
        return getDblink(null, path);
    }

    /**
     * 获取数据库连接（优先读取 IDE 本地持久化配置，其次读取 mysql.properties 或 Spring application.yml）
     */
    public static Connection getDblink(@Nullable Project project, String path) throws Exception {
        if (project != null) {
            CodeGenSettingsState settings = CodeGenSettingsState.getInstance(project);
            settings.importFromProjectProperties(path, false);
            CodeGenSettingsState.ConfigState st = settings.getState();
            if (st != null && StringUtils.isNotBlank(st.jdbcUrl) && !st.jdbcUrl.contains("your_db")) {
                DatabaseDialect.fromJdbcUrl(st.jdbcUrl).loadDriver();
                return DriverManager.getConnection(st.jdbcUrl, st.username, st.retrieveCredential());
            }
        }
        File propFile = new File(path + "src/main/resources/templates/config/mysql.properties");
        if (propFile.exists()) {
            PropertiesUtil propertiesUtil = PropertiesUtil.getMySqlProperties(path);
            String url = propertiesUtil.getProperty("url");
            DatabaseDialect.fromJdbcUrl(url).loadDriver();
            return DriverManager.getConnection(
                    url,
                    propertiesUtil.getProperty("username"),
                    propertiesUtil.getProperty("password")
            );
        }
        if (project != null && CodeGenSettingsState.getInstance(project).getState() != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            DatabaseDialect.fromJdbcUrl(st.jdbcUrl).loadDriver();
            return DriverManager.getConnection(st.jdbcUrl, st.username, st.retrieveCredential());
        }
        throw new IllegalStateException("未配置数据库连接，请在生成弹窗「3. 数据源设置」中填写连接信息。");
    }

    /**
     * “_”+小写 转成驼峰首字母大写
     */
    public static String toUpper(String str) {
        if (StringUtils.isBlank(str)) {
            return "";
        }
        String[] split = StringUtils.split(str, "_");
        StringBuilder name = new StringBuilder();
        for (String part : split) {
            name.append(StringUtils.capitalize(part));
        }
        return name.toString();
    }

    /**
     * 功能：获得列的数据类型映射（支持 MySQL / PostgreSQL / Oracle / DM8 常见类型）
     */
    public static String sqlType2JavaType(String sqlType) {
        if (StringUtils.isBlank(sqlType)) {
            return "String";
        }
        String t = sqlType.trim().toLowerCase(Locale.ROOT);
        if (t.equals("bit") || t.equals("bool") || t.equals("boolean")) {
            return "Boolean";
        } else if (t.equals("tinyint") || t.equals("int2") || t.equals("int4")
                || t.equals("int") || t.equals("integer") || t.equals("mediumint") || t.equals("serial")) {
            return "Integer";
        } else if (t.equals("smallint")) {
            return "Short";
        } else if (t.equals("bigint") || t.equals("int8") || t.equals("bigserial") || t.equals("number")) {
            return "Long";
        } else if (t.equals("float") || t.equals("float4")) {
            return "Float";
        } else if (t.equals("double") || t.equals("float8") || t.equals("binary_double")) {
            return "Double";
        } else if (t.equals("decimal") || t.equals("numeric")
                || t.equals("real") || t.equals("money")
                || t.equals("smallmoney")) {
            return "BigDecimal";
        } else if (t.contains("varchar") || t.contains("char")
                || t.contains("text") || t.contains("clob")
                || t.equals("json") || t.equals("jsonb") || t.equals("variant")) {
            return "String";
        } else if (t.contains("datetime") || t.equals("date")
                || t.contains("timestamp")) {
            return "LocalDateTime";
        } else if (t.equals("image")) {
            return "Blob";
        } else if (t.contains("blob") || t.contains("binary") || t.equals("bytea") || t.equals("raw")) {
            return "byte[]";
        }

        return "String";
    }

    /**
     * 将内容以 UTF-8 编码写入文件
     */
    public static void writeFile(String content, String filePath) throws Exception {
        createFile(filePath);
        try (BufferedWriter bufferedWriter = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            bufferedWriter.write(content);
        }
    }

    /**
     * 创建单个文件及父目录
     */
    public static boolean createFile(String descFileName) {
        File file = new File(descFileName);
        if (file.exists()) {
            return false;
        }
        if (descFileName.endsWith(File.separator)) {
            return false;
        }
        File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            if (!parentFile.mkdirs()) {
                return false;
            }
        }
        try {
            return file.createNewFile();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取当前时间（24 小时制）
     */
    public static String getDate() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return format.format(new java.util.Date());
    }
}
