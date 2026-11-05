package com.code.utils;

import com.code.utils.entity.Table;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多数据库方言元数据适配器与 Spring 配置文件数据源扫描器（P2 + P3：提取字段非空、最大长度与主键约束）
 */
public enum DatabaseDialect {
    MYSQL("com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver"),
    POSTGRESQL("org.postgresql.Driver", "org.postgresql.Driver"),
    ORACLE("oracle.jdbc.OracleDriver", "oracle.jdbc.driver.OracleDriver"),
    DM("dm.jdbc.driver.DmDriver", "dm.jdbc.driver.DmDriver");

    private final String primaryDriver;
    private final String fallbackDriver;

    DatabaseDialect(String primaryDriver, String fallbackDriver) {
        this.primaryDriver = primaryDriver;
        this.fallbackDriver = fallbackDriver;
    }

    public static DatabaseDialect fromJdbcUrl(String jdbcUrl) {
        if (StringUtils.isBlank(jdbcUrl)) {
            return MYSQL;
        }
        String lower = jdbcUrl.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc:postgresql:")) {
            return POSTGRESQL;
        } else if (lower.startsWith("jdbc:oracle:")) {
            return ORACLE;
        } else if (lower.startsWith("jdbc:dm:")) {
            return DM;
        }
        return MYSQL;
    }

    public void loadDriver() throws ClassNotFoundException {
        try {
            Class.forName(primaryDriver);
        } catch (ClassNotFoundException e) {
            Class.forName(fallbackDriver);
        }
    }

    public Map<String, String> queryTablesWithComments(Connection conn, String database) throws SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        String sql;
        switch (this) {
            case POSTGRESQL:
                sql = "SELECT relname AS TABLE_NAME, CAST(obj_description(relfilenode, 'pg_class') AS VARCHAR) AS TABLE_COMMENT " +
                        "FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace " +
                        "WHERE c.relkind = 'r' AND n.nspname = COALESCE(NULLIF(?, ''), 'public') ORDER BY relname";
                break;
            case ORACLE:
            case DM:
                sql = "SELECT TABLE_NAME, COMMENTS AS TABLE_COMMENT FROM USER_TAB_COMMENTS WHERE TABLE_TYPE = 'TABLE' ORDER BY TABLE_NAME";
                break;
            case MYSQL:
            default:
                sql = "SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.tables WHERE table_schema = ? ORDER BY TABLE_NAME";
                break;
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (this == MYSQL || this == POSTGRESQL) {
                stmt.setString(1, StringUtils.defaultString(database));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    String comment = Objects.toString(rs.getString("TABLE_COMMENT"), "").trim();
                    if (StringUtils.isNotBlank(name)) {
                        result.put(name, comment);
                    }
                }
            }
        }
        return result;
    }

    public String querySingleTableComment(Connection conn, String database, String tableName) throws SQLException {
        Map<String, String> all = queryTablesWithComments(conn, database);
        for (Map.Entry<String, String> entry : all.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(tableName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 查询指定表的全部字段信息（含非空约束 IS_NULLABLE、字符长度 CHARACTER_MAXIMUM_LENGTH 与主键标识 COLUMN_KEY）
     */
    public List<Table> queryColumns(Connection conn, String database, String tableName) throws SQLException {
        List<Table> list = new ArrayList<>();
        String sql;
        switch (this) {
            case POSTGRESQL:
                sql = "SELECT a.attname AS COLUMN_NAME, t.typname AS DATA_TYPE, col_description(a.attrelid, a.attnum) AS COLUMN_COMMENT, " +
                        "CASE WHEN a.attnotnull THEN 'NO' ELSE 'YES' END AS IS_NULLABLE, " +
                        "CASE WHEN a.atttypmod > 4 THEN a.atttypmod - 4 ELSE NULL END AS CHARACTER_MAXIMUM_LENGTH, " +
                        "'' AS COLUMN_KEY " +
                        "FROM pg_class c JOIN pg_attribute a ON a.attrelid = c.oid JOIN pg_type t ON a.atttypid = t.oid " +
                        "WHERE c.relname = ? AND a.attnum > 0 AND NOT a.attisdropped ORDER BY a.attnum";
                break;
            case ORACLE:
            case DM:
                sql = "SELECT c.COLUMN_NAME, c.DATA_TYPE, m.COMMENTS AS COLUMN_COMMENT, " +
                        "CASE WHEN c.NULLABLE = 'N' THEN 'NO' ELSE 'YES' END AS IS_NULLABLE, " +
                        "c.CHAR_LENGTH AS CHARACTER_MAXIMUM_LENGTH, " +
                        "'' AS COLUMN_KEY " +
                        "FROM USER_TAB_COLUMNS c LEFT JOIN USER_COL_COMMENTS m " +
                        "ON c.TABLE_NAME = m.TABLE_NAME AND c.COLUMN_NAME = m.COLUMN_NAME " +
                        "WHERE UPPER(c.TABLE_NAME) = UPPER(?) ORDER BY c.COLUMN_ID";
                break;
            case MYSQL:
            default:
                sql = "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT, IS_NULLABLE, CHARACTER_MAXIMUM_LENGTH, COLUMN_KEY " +
                        "FROM INFORMATION_SCHEMA.`COLUMNS` " +
                        "WHERE table_name = ? AND table_schema = ? " +
                        "ORDER BY ORDINAL_POSITION";
                break;
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            if (this == MYSQL) {
                stmt.setString(2, database);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Table table = new Table();
                    String rawColumnName = Objects.toString(rs.getString("COLUMN_NAME"), "");
                    String rawDataType = Objects.toString(rs.getString("DATA_TYPE"), "");
                    String rawComment = Objects.toString(rs.getString("COLUMN_COMMENT"), "");
                    String isNullableStr = Objects.toString(rs.getString("IS_NULLABLE"), "YES");
                    String columnKeyStr = Objects.toString(rs.getString("COLUMN_KEY"), "");
                    Object maxLenObj = rs.getObject("CHARACTER_MAXIMUM_LENGTH");

                    table.setTypeName(rawColumnName);
                    String camelLower = StringUtils.uncapitalize(CodeGenerateUtils.toUpper(rawColumnName.toLowerCase(Locale.ROOT)));
                    table.setColumnName(camelLower);
                    table.setColumnNameUpper(StringUtils.capitalize(camelLower));
                    table.setDataType(CodeGenerateUtils.sqlType2JavaType(rawDataType));
                    table.setColumnComment(rawComment);
                    table.setNullable(!"NO".equalsIgnoreCase(isNullableStr));
                    table.setPrimaryKey("PRI".equalsIgnoreCase(columnKeyStr) || "id".equalsIgnoreCase(rawColumnName));

                    if (maxLenObj instanceof Number) {
                        long len = ((Number) maxLenObj).longValue();
                        if (len > 0 && len <= Integer.MAX_VALUE) {
                            table.setMaxLength((int) len);
                        }
                    }
                    list.add(table);
                }
            }
        }
        return list;
    }

    public static Map<String, String> scanSpringDataSourceConfig(String projectRootPath) {
        Map<String, String> discovered = new HashMap<>();
        if (StringUtils.isBlank(projectRootPath)) {
            return discovered;
        }
        File resDir = new File(projectRootPath, "src/main/resources");
        if (!resDir.exists() || !resDir.isDirectory()) {
            return discovered;
        }
        File[] files = resDir.listFiles((dir, name) ->
                name.startsWith("application") || name.startsWith("bootstrap")
        );
        if (files == null) {
            return discovered;
        }

        Pattern urlPattern = Pattern.compile("(?:url|jdbc-url)\\s*[:=]\\s*['\"]?(jdbc:[^'\"\\s#]+)['\"]?");
        Pattern userPattern = Pattern.compile("username\\s*[:=]\\s*['\"]?([^'\"\\s#]+)['\"]?");
        Pattern pwdPattern = Pattern.compile("password\\s*[:=]\\s*['\"]?([^'\"\\s#]+)['\"]?");

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                Matcher urlMatcher = urlPattern.matcher(content);
                if (urlMatcher.find() && !discovered.containsKey("url")) {
                    String url = urlMatcher.group(1).trim();
                    discovered.put("url", url);
                    String db = extractDatabaseFromJdbcUrl(url);
                    if (StringUtils.isNotBlank(db)) {
                        discovered.put("database", db);
                    }
                }
                Matcher userMatcher = userPattern.matcher(content);
                if (userMatcher.find() && !discovered.containsKey("username")) {
                    discovered.put("username", userMatcher.group(1).trim());
                }
                Matcher pwdMatcher = pwdPattern.matcher(content);
                if (pwdMatcher.find() && !discovered.containsKey("password")) {
                    discovered.put("password", pwdMatcher.group(1).trim());
                }
            } catch (Exception ignored) {
            }
        }
        return discovered;
    }

    public static String extractDatabaseFromJdbcUrl(String jdbcUrl) {
        if (StringUtils.isBlank(jdbcUrl)) {
            return "";
        }
        Matcher m = Pattern.compile("//[^/]+/([^?;:\\s]+)").matcher(jdbcUrl);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }
}
