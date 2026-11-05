package com.code.utils;

import com.code.utils.entity.Table;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 离线 DDL SQL 解析引擎（P3 阶段：无需连接真实数据库，直接从 CREATE TABLE 语句解析表结构与字段元数据，兼容 JDK 21~25+）
 */
public class DdlSqlParser {

    public static class ParsedTable {
        private String tableName;
        private String tableComment = "";
        private List<Table> columns = new ArrayList<>();

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
    }

    private static final Pattern CREATE_TABLE_HEADER_PATTERN = Pattern.compile(
            "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:[`\"\\[]?\\w+[`\"\\]]?\\.)?[`\"\\[]?([a-zA-Z0-9_]+)[`\"\\]]?\\s*\\("
    );

    private static final Pattern TABLE_COMMENT_PATTERN = Pattern.compile(
            "(?i)COMMENT\\s*=\\s*'([^']*)'|COMMENT\\s+'([^']*)'"
    );

    private static final Pattern PRIMARY_KEY_CONSTRAINT_PATTERN = Pattern.compile(
            "(?i)PRIMARY\\s+KEY\\s*\\(([^)]+)\\)"
    );

    private static final Pattern COLUMN_LINE_PATTERN = Pattern.compile(
            "^\\s*[`\"\\[]?([a-zA-Z0-9_]+)[`\"\\]]?\\s+([a-zA-Z0-9_]+)(?:\\s*\\(\\s*(\\d+)(?:\\s*,\\s*\\d+)?\\s*\\))?(.*)$"
    );

    private static final Pattern COLUMN_COMMENT_PATTERN = Pattern.compile(
            "(?i)COMMENT\\s+'([^']*)'"
    );

    /**
     * 解析包含一个或多个 CREATE TABLE 的 SQL 文本，返回表名到 ParsedTable 的映射
     */
    public static Map<String, ParsedTable> parseDdlScript(String sqlScript) {
        Map<String, ParsedTable> result = new LinkedHashMap<>();
        if (StringUtils.isBlank(sqlScript)) {
            return result;
        }

        Matcher headerMatcher = CREATE_TABLE_HEADER_PATTERN.matcher(sqlScript);
        while (headerMatcher.find()) {
            String tableName = headerMatcher.group(1).trim();
            int openParenIndex = headerMatcher.end() - 1;
            int closeParenIndex = findMatchingCloseParen(sqlScript, openParenIndex);
            if (closeParenIndex < 0) {
                continue;
            }

            String bodyContent = sqlScript.substring(openParenIndex + 1, closeParenIndex);
            int statementEnd = findStatementEnd(sqlScript, closeParenIndex + 1);
            String tailOptions = sqlScript.substring(closeParenIndex + 1, statementEnd);

            ParsedTable parsedTable = new ParsedTable();
            parsedTable.setTableName(tableName);
            parsedTable.setTableComment(extractTableComment(tailOptions));
            parsedTable.setColumns(parseColumnsFromBody(bodyContent));

            result.put(tableName, parsedTable);
        }
        return result;
    }

    private static List<Table> parseColumnsFromBody(String bodyContent) {
        List<Table> columns = new ArrayList<>();
        Set<String> primaryKeys = new HashSet<>();

        List<String> lines = splitTopLevelCommaLines(bodyContent);

        for (String rawLine : lines) {
            String trimmed = rawLine.trim();
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
        }

        for (String rawLine : lines) {
            String trimmed = rawLine.trim();
            if (StringUtils.isBlank(trimmed) || isConstraintLine(trimmed)) {
                continue;
            }

            Matcher colMatcher = COLUMN_LINE_PATTERN.matcher(trimmed);
            if (!colMatcher.matches()) {
                continue;
            }

            String rawColName = colMatcher.group(1).trim();
            String rawSqlType = colMatcher.group(2).trim();
            String lengthStr = colMatcher.group(3);
            String modifiers = StringUtils.defaultString(colMatcher.group(4));

            Table tableCol = new Table();
            tableCol.setTypeName(rawColName);
            String camelLower = StringUtils.uncapitalize(CodeGenerateUtils.toUpper(rawColName.toLowerCase(Locale.ROOT)));
            tableCol.setColumnName(camelLower);
            tableCol.setColumnNameUpper(StringUtils.capitalize(camelLower));
            tableCol.setDataType(CodeGenerateUtils.sqlType2JavaType(rawSqlType));

            if (StringUtils.isNotBlank(lengthStr)) {
                try {
                    tableCol.setMaxLength(Integer.parseInt(lengthStr));
                } catch (NumberFormatException ignored) {
                }
            }

            String upperMod = modifiers.toUpperCase(Locale.ROOT);
            boolean isNotNull = upperMod.contains("NOT NULL") || upperMod.contains("PRIMARY KEY");
            boolean isPk = upperMod.contains("PRIMARY KEY") || primaryKeys.contains(rawColName.toLowerCase(Locale.ROOT));
            tableCol.setNullable(!isNotNull);
            tableCol.setPrimaryKey(isPk);

            Matcher commentMatcher = COLUMN_COMMENT_PATTERN.matcher(modifiers);
            if (commentMatcher.find()) {
                tableCol.setColumnComment(commentMatcher.group(1).trim());
            } else {
                tableCol.setColumnComment("");
            }

            columns.add(tableCol);
        }

        return columns;
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
            return StringUtils.defaultIfBlank(m.group(1), StringUtils.defaultString(m.group(2))).trim();
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
