package com.code.utils;

import com.code.utils.entity.Table;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多数据库方言元数据适配器、动态 JDBC 驱动加载器与 Spring 配置文件扫描器（v3.0 M2）
 *
 * 核心升级：
 * 1. 支持从本地 Maven 仓库 (~/.m2/repository) 与 Gradle 缓存 (~/.gradle/caches) 动态发现并加载
 *    PostgreSQL、Oracle、达梦 (DM8) 的 JDBC 驱动 JAR，无需将所有商业/第三方驱动硬打包进插件。
 * 2. 通过 DriverShim 桥接 URLClassLoader 加载的 Driver，无缝兼容 DriverManager。
 *
 * @author syrobin
 */
public enum DatabaseDialect {
    MYSQL("com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver", "mysql-connector"),
    POSTGRESQL("org.postgresql.Driver", "org.postgresql.Driver", "postgresql-"),
    ORACLE("oracle.jdbc.OracleDriver", "oracle.jdbc.driver.OracleDriver", "ojdbc"),
    DM("dm.jdbc.driver.DmDriver", "dm.jdbc.driver.DmDriver", "DmJdbcDriver");

    private static final Map<DatabaseDialect, Driver> DYNAMIC_DRIVER_CACHE = new ConcurrentHashMap<>();

    private final String primaryDriver;
    private final String fallbackDriver;
    private final String jarPrefixHint;

    DatabaseDialect(String primaryDriver, String fallbackDriver, String jarPrefixHint) {
        this.primaryDriver = primaryDriver;
        this.fallbackDriver = fallbackDriver;
        this.jarPrefixHint = jarPrefixHint;
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

    /**
     * 加载数据库驱动：优先从插件自身 ClassLoader 加载；若未内置则自动扫描本地 Maven / Gradle 仓库动态加载
     */
    public void loadDriver() throws ClassNotFoundException {
        try {
            Class.forName(primaryDriver);
            return;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class.forName(fallbackDriver);
            return;
        } catch (ClassNotFoundException ignored) {
        }

        if (DYNAMIC_DRIVER_CACHE.containsKey(this)) {
            return;
        }

        File jarFile = findDriverJarInLocalRepositories();
        if (jarFile != null && jarFile.exists()) {
            try {
                loadDriverFromJar(jarFile);
                return;
            } catch (Exception ignored) {
            }
        }
        throw new ClassNotFoundException(
                "未找到 " + name() + " 数据库驱动 [" + primaryDriver + "]。请确保本地 Maven (~/.m2) 或 Gradle (~/.gradle) 仓库中已下载对应驱动 JAR。"
        );
    }

    /**
     * 从指定驱动 JAR 文件动态加载并注册 JDBC Driver
     */
    public synchronized Driver loadDriverFromJar(File jarFile) throws Exception {
        URL jarUrl = jarFile.toURI().toURL();
        URLClassLoader loader = new URLClassLoader(new URL[]{jarUrl}, DatabaseDialect.class.getClassLoader());
        Class<?> driverClass;
        try {
            driverClass = Class.forName(primaryDriver, true, loader);
        } catch (ClassNotFoundException ex) {
            driverClass = Class.forName(fallbackDriver, true, loader);
        }
        Driver rawDriver = (Driver) driverClass.getDeclaredConstructor().newInstance();
        Driver shim = new DriverShim(rawDriver);
        DriverManager.registerDriver(shim);
        DYNAMIC_DRIVER_CACHE.put(this, shim);
        return shim;
    }

    /**
     * 在本地 Maven (~/.m2/repository) 或 Gradle 缓存目录中搜索当前方言对应的 JDBC 驱动 JAR
     */
    @Nullable
    public File findDriverJarInLocalRepositories(File... customSearchRoots) {
        List<File> roots = new ArrayList<>();
        if (customSearchRoots != null) {
            for (File r : customSearchRoots) {
                if (r != null && r.exists()) {
                    roots.add(r);
                }
            }
        }
        String userHome = System.getProperty("user.home");
        if (StringUtils.isNotBlank(userHome)) {
            switch (this) {
                case POSTGRESQL -> roots.add(new File(userHome, ".m2/repository/org/postgresql/postgresql"));
                case ORACLE -> roots.add(new File(userHome, ".m2/repository/com/oracle/database/jdbc"));
                case DM -> roots.add(new File(userHome, ".m2/repository/com/dameng"));
                case MYSQL -> {
                    roots.add(new File(userHome, ".m2/repository/com/mysql/mysql-connector-j"));
                    roots.add(new File(userHome, ".m2/repository/mysql/mysql-connector-java"));
                }
            }
            roots.add(new File(userHome, ".gradle/caches/modules-2/files-2.1"));
        }

        for (File root : roots) {
            File found = searchJarRecursively(root, jarPrefixHint, 6);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Nullable
    private static File searchJarRecursively(File dir, String prefixHint, int maxDepth) {
        if (dir == null || !dir.exists() || maxDepth < 0) {
            return null;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        // 优先匹配较新版本的目录或 JAR（倒序排列）
        Arrays.sort(files, Comparator.comparing(File::getName).reversed());
        String lowerHint = prefixHint.toLowerCase(Locale.ROOT);
        for (File f : files) {
            String name = f.getName();
            if (f.isFile()
                    && name.endsWith(".jar")
                    && !name.endsWith("-sources.jar")
                    && !name.endsWith("-javadoc.jar")
                    && name.toLowerCase(Locale.ROOT).startsWith(lowerHint)) {
                return f;
            }
        }
        for (File f : files) {
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                File sub = searchJarRecursively(f, prefixHint, maxDepth - 1);
                if (sub != null) {
                    return sub;
                }
            }
        }
        return null;
    }

    /**
     * DriverManager 代理包装器：解决 DriverManager 拒绝调用非系统 ClassLoader 加载的 Driver 问题
     */
    public static class DriverShim implements Driver {
        private final Driver delegate;

        public DriverShim(Driver delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            return delegate.connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return delegate.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return delegate.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegate.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return delegate.jdbcCompliant();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
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
