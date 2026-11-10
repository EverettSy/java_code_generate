package com.code.utils;

import com.code.utils.entity.Table;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 离线 DDL SQL 多方言解析引擎（v3.0 M2：支持 MySQL / PostgreSQL / Oracle / 达梦 DM8）
 *
 * 核心能力：
 * 1. 支持 MySQL 行内 COMMENT 与表尾 COMMENT='...'
 * 2. 支持 PostgreSQL / Oracle / DM8 表外独立注释语句：
 *    - COMMENT ON TABLE [schema.]table_name IS '...'
 *    - COMMENT ON COLUMN [schema.]table_name.column_name IS '...'
 * 3. 支持 ALTER TABLE [schema.]table_name ADD [COLUMN] ... 增量字段语句合并
 * 4. 支持 VARCHAR2(64 BYTE/CHAR)、NUMBER(p,s) 精度区分（含小数位自动映射 BigDecimal）、CHARACTER VARYING 等多方言类型
 *
 * @author syrobin
 */
public class DdlSqlParser {

    public static class ParsedTable {
        private String tableName;
        private String tableComment = "";
        private List<Table> columns = new ArrayList<>();
        /**
         * 显式外键约束映射：fkColumnName -> referencedTableName.referencedPkColumnName
         */
        private Map<String, String> foreignKeys = new LinkedHashMap<>();

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public String getTableComment() {
            return tableComment;
        }

        public void setTableComment(String tableComment) {
            this.tableComment = tableComment;
        }

        public List<Table> getColumns() {
            return columns;
        }

        public void setColumns(List<Table> columns) {
            this.columns = columns;
        }

        public Map<String, String> getForeignKeys() {
            return foreignKeys;
        }

        public void setForeignKeys(Map<String, String> foreignKeys) {
            this.foreignKeys = foreignKeys != null ? foreignKeys : new LinkedHashMap<>();
        }
    }

    private static final Pattern CREATE_TABLE_HEADER_PATTERN = Pattern.compile(
            "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:[`\"\\[]?\\w+[`\"\\]]?\\s*\\.\\s*)?[`\"\\[]?([a-zA-Z0-9_]+)[`\"\\]]?\\s*\\("
    );

    private static final Pattern TABLE_COMMENT_PATTERN = Pattern.compile(
            "(?i)COMMENT\\s*=\\s*'((?:''|\\\\'|[^'])*)'|COMMENT\\s+'((?:''|\\\\'|[^'])*)'"
    );

    private static final Pattern PRIMARY_KEY_CONSTRAINT_PATTERN = Pattern.compile(
            "(?i)PRIMARY\\s+KEY\\s*\\(([^)]+)\\)"
    );

    /**
     * 表级 FOREIGN KEY (`order_id`) REFERENCES `user_order` (`id`) 约束匹配
     */
    private static final Pattern TABLE_FOREIGN_KEY_PATTERN = Pattern.compile(
            "(?i)FOREIGN\\s+KEY\\s*\\(\\s*[`\"\\[]?([a-zA-Z0-9_]+)[`\"\\]]?\\s*\\)\\s*REFERENCES\\s+(?:[`\"\\[]?\\w+[`\"\\]]?\\s*\\.\\s*)?[`\"\\[]?([a-zA-Z0-9_]+)[`\"\\]]?\\s*(?:\\(\\s*[`\"\\[]?([a-zA-Z0-9_]+)[`\"\\]]?\\s*\\))?"
    );

    /**
     * 列内 REFERENCES `user_order` (`id`) 约束匹配
     */
    private static final Pattern INLINE_REFERENCES_PATTERN = Pattern.compile(
            "(?i)REFERENCES\\s+(?:[`\"\\[]?\\w+[`\"\\]]?\\s*\\.\\s*)?[`\"\\[]?([a-zA-Z0-9_]+)[`\"\\]]?\\s*(?:\\(\\s*[`\"\\[]?([a-zA-Z0-9_]+)[`\"\\]]?\\s*\\))?"
    );

    /**
     * 匹配字段定义行：支持 `character varying(64)`、`varchar2(64 byte)`、`number(12, 2)`、`timestamp with time zone` 等多方言语法
     */
    private static final Pattern COLUMN_LINE_PATTERN = Pattern.compile(
            "(?i)^\\s*[`\"\\[]?([a-zA-Z0-9_]+)[`\"\\]]?\\s+" +
            "(character\\s+varying|double\\s+precision|timestamp(?:\\s+with(?:out)?\\s+time\\s+zone)?|[a-zA-Z0-9_]+)" +
            "(?:\\s*\\(\\s*(\\d+)(?:\\s*,\\s*(\\d+))?(?:\\s*(?:BYTE|CHAR))?\\s*\\))?(.*)$"
    );

    private static final Pattern COLUMN_COMMENT_PATTERN = Pattern.compile(
            "(?i)COMMENT\\s+'((?:''|\\\\'|[^'])*)'"
    );

    /**
     * PostgreSQL / Oracle / DM8 表外注释：COMMENT ON TABLE [schema.]table IS '...'
     */
    private static final Pattern COMMENT_ON_TABLE_PATTERN = Pattern.compile(
            "(?i)COMMENT\\s+ON\\s+TABLE\\s+(?:[`\"\\[]?\\w+[`\"\\]]?\\s*\\.\\s*)?[`\"\\[]?([a-zA-Z0-9_]+)[`\"\\]]?\\s+IS\\s+'((?:''|\\\\'|[^'])*)'"
    );

    /**
     * PostgreSQL / Oracle / DM8 字段表外注释：COMMENT ON COLUMN [schema.]table.column IS '...'
     */
    private static final Pattern COMMENT_ON_COLUMN_PATTERN = Pattern.compile(
            "(?i)COMMENT\\s+ON\\s+COLUMN\\s+(?:[`\"\\[]?\\w+[`\"\\]]?\\s*\\.\\s*)?[`\"\\[]?([a-zA-Z0-9_]+)[`\"\\]]?\\s*\\.\\s*[`\"\\[]?([a-zA-Z0-9_]+)[`\"\\]]?\\s+IS\\s+'((?:''|\\\\'|[^'])*)'"
    );

    /**
     * ALTER TABLE [schema.]table ADD [COLUMN] ... 增量加列语句
     */
    private static final Pattern ALTER_TABLE_ADD_COLUMN_PATTERN = Pattern.compile(
            "(?i)ALTER\\s+TABLE\\s+(?:[`\"\\[]?\\w+[`\"\\]]?\\s*\\.\\s*)?[`\"\\[]?([a-zA-Z0-9_]+)[`\"\\]]?\\s+ADD\\s+(?:COLUMN\\s+)?([^;]+)"
    );

    /**
     * 解析包含一个或多个 CREATE TABLE / ALTER TABLE / COMMENT ON 的 SQL 文本，返回表名到 ParsedTable 的映射
     */
    public static Map<String, ParsedTable> parseDdlScript(String sqlScript) {
        Map<String, ParsedTable> result = new LinkedHashMap<>();
        if (StringUtils.isBlank(sqlScript)) {
            return result;
        }

        // 1. 解析所有 CREATE TABLE 主体
        Matcher headerMatcher = CREATE_TABLE_HEADER_PATTERN.matcher(sqlScript);
        while (headerMatcher.find()) {
            String rawTableName = headerMatcher.group(1).trim();
            int openParenIndex = headerMatcher.end() - 1;
            int closeParenIndex = findMatchingCloseParen(sqlScript, openParenIndex);
            if (closeParenIndex < 0) {
                continue;
            }

            String bodyContent = sqlScript.substring(openParenIndex + 1, closeParenIndex);
            int statementEnd = findStatementEnd(sqlScript, closeParenIndex + 1);
            String tailOptions = sqlScript.substring(closeParenIndex + 1, statementEnd);

            ParsedTable parsedTable = new ParsedTable();
            parsedTable.setTableName(rawTableName);
            parsedTable.setTableComment(extractTableComment(tailOptions));
            Map<String, String> fkMap = new LinkedHashMap<>();
            parsedTable.setColumns(parseColumnsFromBody(bodyContent, fkMap));
            parsedTable.setForeignKeys(fkMap);

            result.put(rawTableName, parsedTable);
        }

        if (result.isEmpty()) {
            return result;
        }

        // 2. 解析 ALTER TABLE ... ADD [COLUMN] 增量列语句
        Matcher alterMatcher = ALTER_TABLE_ADD_COLUMN_PATTERN.matcher(sqlScript);
        while (alterMatcher.find()) {
            String targetTable = alterMatcher.group(1).trim();
            String addBody = alterMatcher.group(2).trim();
            ParsedTable parsedTable = findTableIgnoreCase(result, targetTable);
            if (parsedTable == null) {
                continue;
            }
            if (addBody.startsWith("(") && addBody.endsWith(")")) {
                addBody = addBody.substring(1, addBody.length() - 1);
            }
            List<Table> addedCols = parseColumnsFromBody(addBody, parsedTable.getForeignKeys());
            for (Table added : addedCols) {
                mergeOrAddColumn(parsedTable.getColumns(), added);
            }
        }

        // 3. 回填 PostgreSQL / Oracle / DM8 的 COMMENT ON TABLE 语句
        Matcher tableCommentMatcher = COMMENT_ON_TABLE_PATTERN.matcher(sqlScript);
        while (tableCommentMatcher.find()) {
            String targetTable = tableCommentMatcher.group(1).trim();
            String comment = unescapeSqlString(tableCommentMatcher.group(2));
            ParsedTable parsedTable = findTableIgnoreCase(result, targetTable);
            if (parsedTable != null && StringUtils.isNotBlank(comment)) {
                parsedTable.setTableComment(comment);
            }
        }

        // 4. 回填 PostgreSQL / Oracle / DM8 的 COMMENT ON COLUMN 语句
        Matcher colCommentMatcher = COMMENT_ON_COLUMN_PATTERN.matcher(sqlScript);
        while (colCommentMatcher.find()) {
            String targetTable = colCommentMatcher.group(1).trim();
            String targetCol = colCommentMatcher.group(2).trim();
            String comment = unescapeSqlString(colCommentMatcher.group(3));
            ParsedTable parsedTable = findTableIgnoreCase(result, targetTable);
            if (parsedTable != null) {
                for (Table col : parsedTable.getColumns()) {
                    if (col.getTypeName().equalsIgnoreCase(targetCol)) {
                        col.setColumnComment(comment);
                        break;
                    }
                }
            }
        }

        return result;
    }

    private static void mergeOrAddColumn(List<Table> existingCols, Table newCol) {
        for (int i = 0; i < existingCols.size(); i++) {
            if (existingCols.get(i).getTypeName().equalsIgnoreCase(newCol.getTypeName())) {
                existingCols.set(i, newCol);
                return;
            }
        }
        existingCols.add(newCol);
    }

    private static ParsedTable findTableIgnoreCase(Map<String, ParsedTable> map, String tableName) {
        ParsedTable direct = map.get(tableName);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, ParsedTable> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(tableName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static List<Table> parseColumnsFromBody(String bodyContent, Map<String, String> foreignKeysOut) {
        List<Table> columns = new ArrayList<>();
        Set<String> primaryKeys = new HashSet<>();

        List<String> lines = splitTopLevelCommaLines(bodyContent);

        for (String rawLine : lines) {
            String trimmed = stripLeadingLineComments(rawLine).trim();
            Matcher pkMatcher = PRIMARY_KEY_CONSTRAINT_PATTERN.matcher(trimmed);
            if (pkMatcher.find() && isConstraintLine(trimmed)) {
                String pkCols = pkMatcher.group(1);
                for (String pk : pkCols.split(",")) {
                    String cleanPk = pk.replaceAll("[`\"\\[\\]\\s]", "");
                    if (StringUtils.isNotBlank(cleanPk)) {
                        primaryKeys.add(cleanPk.toLowerCase(Locale.ROOT));
                    }
                }
            }
            Matcher fkMatcher = TABLE_FOREIGN_KEY_PATTERN.matcher(trimmed);
            if (fkMatcher.find() && isConstraintLine(trimmed) && foreignKeysOut != null) {
                String fkCol = fkMatcher.group(1).trim();
                String refTable = fkMatcher.group(2).trim();
                String refPk = StringUtils.defaultIfBlank(fkMatcher.group(3), "id").trim();
                foreignKeysOut.put(fkCol, refTable + "." + refPk);
            }
        }

        for (String rawLine : lines) {
            String trimmed = stripLeadingLineComments(rawLine).trim();
            if (StringUtils.isBlank(trimmed) || isConstraintLine(trimmed)) {
                continue;
            }

            Matcher colMatcher = COLUMN_LINE_PATTERN.matcher(trimmed);
            if (!colMatcher.matches()) {
                continue;
            }

            String rawColName = colMatcher.group(1).trim();
            String rawSqlType = colMatcher.group(2).trim();
            String precisionOrLenStr = colMatcher.group(3);
            String scaleStr = colMatcher.group(4);
            String modifiers = StringUtils.defaultString(colMatcher.group(5));

            Table tableCol = new Table();
            tableCol.setTypeName(rawColName);
            String camelLower = StringUtils.uncapitalize(CodeGenerateUtils.toUpper(rawColName.toLowerCase(Locale.ROOT)));
            tableCol.setColumnName(camelLower);
            tableCol.setColumnNameUpper(StringUtils.capitalize(camelLower));
            tableCol.setDataType(resolveSqlTypeWithScale(rawSqlType, precisionOrLenStr, scaleStr));

            if (StringUtils.isNotBlank(precisionOrLenStr)) {
                try {
                    tableCol.setMaxLength(Integer.parseInt(precisionOrLenStr));
                } catch (NumberFormatException ignored) {
                }
            }

            String upperMod = modifiers.toUpperCase(Locale.ROOT);
            boolean isNotNull = upperMod.contains("NOT NULL") || upperMod.contains("PRIMARY KEY");
            boolean isPk = upperMod.contains("PRIMARY KEY") || primaryKeys.contains(rawColName.toLowerCase(Locale.ROOT));
            tableCol.setNullable(!isNotNull);
            tableCol.setPrimaryKey(isPk);

            Matcher inlineRefMatcher = INLINE_REFERENCES_PATTERN.matcher(modifiers);
            if (inlineRefMatcher.find() && foreignKeysOut != null) {
                String refTable = inlineRefMatcher.group(1).trim();
                String refPk = StringUtils.defaultIfBlank(inlineRefMatcher.group(2), "id").trim();
                foreignKeysOut.put(rawColName, refTable + "." + refPk);
            }

            Matcher commentMatcher = COLUMN_COMMENT_PATTERN.matcher(modifiers);
            if (commentMatcher.find()) {
                tableCol.setColumnComment(unescapeSqlString(commentMatcher.group(1)));
            } else {
                tableCol.setColumnComment("");
            }

            columns.add(tableCol);
        }

        return columns;
    }

    /**
     * 结合精度与小数位数映射 Java 类型（例如 Oracle NUMBER(12,2) -> BigDecimal，NUMBER(9) -> Integer，NUMBER(19) -> Long）
     */
    private static String resolveSqlTypeWithScale(String rawSqlType, String precisionStr, String scaleStr) {
        String normType = rawSqlType.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if ("number".equals(normType)) {
            if (StringUtils.isNotBlank(scaleStr)) {
                try {
                    int scale = Integer.parseInt(scaleStr);
                    if (scale > 0) {
                        return "BigDecimal";
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (StringUtils.isNotBlank(precisionStr)) {
                try {
                    int precision = Integer.parseInt(precisionStr);
                    if (precision == 1) {
                        return "Boolean";
                    } else if (precision <= 9) {
                        return "Integer";
                    } else {
                        return "Long";
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return "Long";
        }
        return CodeGenerateUtils.sqlType2JavaType(normType);
    }

    private static String stripLeadingLineComments(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String[] lines = text.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String t = line.trim();
            if (!t.startsWith("--") && !t.startsWith("//")) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static String unescapeSqlString(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("''", "'").replace("\\'", "'").trim();
    }

    private static boolean isConstraintLine(String trimmedLine) {
        String upper = trimmedLine.toUpperCase(Locale.ROOT);
        return upper.startsWith("PRIMARY KEY")
                || upper.startsWith("KEY ")
                || upper.startsWith("INDEX ")
                || upper.startsWith("UNIQUE")
                || upper.startsWith("CONSTRAINT ")
                || upper.startsWith("FOREIGN KEY")
                || upper.startsWith("FULLTEXT ")
                || upper.startsWith("SPATIAL ")
                || upper.startsWith("CHECK ");
    }

    private static String extractTableComment(String tailOptions) {
        if (StringUtils.isBlank(tailOptions)) {
            return "";
        }
        Matcher m = TABLE_COMMENT_PATTERN.matcher(tailOptions);
        if (m.find()) {
            String raw = StringUtils.defaultIfBlank(m.group(1), StringUtils.defaultString(m.group(2)));
            return unescapeSqlString(raw);
        }
        return "";
    }

    private static int findMatchingCloseParen(String text, int openIndex) {
        int depth = 0;
        boolean inSingleQuote = false;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && (i == 0 || text.charAt(i - 1) != '\\')) {
                inSingleQuote = !inSingleQuote;
            } else if (!inSingleQuote) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private static int findStatementEnd(String text, int startIndex) {
        boolean inSingleQuote = false;
        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && (i == 0 || text.charAt(i - 1) != '\\')) {
                inSingleQuote = !inSingleQuote;
            } else if (!inSingleQuote && c == ';') {
                return i;
            }
        }
        return text.length();
    }

    private static List<String> splitTopLevelCommaLines(String body) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenDepth = 0;
        boolean inSingleQuote = false;

        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\'' && (i == 0 || body.charAt(i - 1) != '\\')) {
                inSingleQuote = !inSingleQuote;
                current.append(c);
            } else if (!inSingleQuote) {
                if (c == '(') {
                    parenDepth++;
                    current.append(c);
                } else if (c == ')') {
                    parenDepth = Math.max(0, parenDepth - 1);
                    current.append(c);
                } else if (c == ',' && parenDepth == 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }
}
