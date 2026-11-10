package com.code.utils;

import com.code.utils.entity.Table;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 数据库表结构演进与增量 Flyway / Liquibase Diff SQL 生成引擎（v4.0 M3）
 *
 * 核心能力：
 * 1. 对比新旧两个版本的 {@link DdlSqlParser.ParsedTable}（或列集合），自动识别新增列、类型/长度/非空演进列、注释变更列与删除列。
 * 2. 支持生成适配 MySQL、PostgreSQL、Oracle、达梦 (DM8) 四大方言的标准增量 ALTER TABLE 迁移脚本。
 *
 * @author syrobin
 */
public final class DdlMigrationGenerator {

    private DdlMigrationGenerator() {
    }

    /**
     * 对比旧表与新表结构，生成指定数据库方言的增量 ALTER TABLE 迁移 SQL 脚本
     */
    @NotNull
    public static String generateAlterMigrationSql(@Nullable DdlSqlParser.ParsedTable oldTable,
                                                   @NotNull DdlSqlParser.ParsedTable newTable,
                                                   @NotNull DatabaseDialect dialect) {
        String tableName = newTable.getTableName();
        StringBuilder sql = new StringBuilder();
        sql.append("-- =============================================================================\n");
        sql.append("-- Schema Evolution Migration Script for Table: ").append(tableName).append("\n");
        sql.append("-- Target Dialect: ").append(dialect.name()).append("\n");
        sql.append("-- Generated At  : ").append(CodeGenerateUtils.getDate()).append("\n");
        sql.append("-- =============================================================================\n\n");

        if (oldTable == null || oldTable.getColumns() == null || oldTable.getColumns().isEmpty()) {
            sql.append(generateCreateTableSql(newTable, dialect));
            return sql.toString();
        }

        Map<String, Table> oldColMap = new LinkedHashMap<>();
        for (Table col : oldTable.getColumns()) {
            oldColMap.put(col.getTypeName().toLowerCase(Locale.ROOT), col);
        }

        Map<String, Table> newColMap = new LinkedHashMap<>();
        for (Table col : newTable.getColumns()) {
            newColMap.put(col.getTypeName().toLowerCase(Locale.ROOT), col);
        }

        List<String> statements = new ArrayList<>();

        // 1. 检查新增列与修改列
        for (Table newCol : newTable.getColumns()) {
            String key = newCol.getTypeName().toLowerCase(Locale.ROOT);
            Table oldCol = oldColMap.get(key);
            if (oldCol == null) {
                statements.add(buildAddColumnStatement(tableName, newCol, dialect));
            } else if (isColumnChanged(oldCol, newCol)) {
                statements.addAll(buildModifyColumnStatements(tableName, oldCol, newCol, dialect));
            }
        }

        // 2. 检查被移除的列（生成安全注释提示或 DROP COLUMN 语句）
        for (Table oldCol : oldTable.getColumns()) {
            String key = oldCol.getTypeName().toLowerCase(Locale.ROOT);
            if (!newColMap.containsKey(key)) {
                statements.add(buildDropColumnStatement(tableName, oldCol, dialect));
            }
        }

        if (statements.isEmpty()) {
            sql.append("-- No structural changes detected for table ").append(tableName).append(".\n");
        } else {
            for (String stmt : statements) {
                sql.append(stmt).append("\n");
            }
        }
        return sql.toString();
    }

    /**
     * 批量对比两份完整 DDL 脚本，输出整库增量迁移 SQL
     */
    @NotNull
    public static String generateScriptDiffMigration(@Nullable String oldDdlScript,
                                                     @NotNull String newDdlScript,
                                                     @NotNull DatabaseDialect dialect) {
        Map<String, DdlSqlParser.ParsedTable> oldMap = StringUtils.isBlank(oldDdlScript)
                ? Collections.emptyMap()
                : DdlSqlParser.parseDdlScript(oldDdlScript);
        Map<String, DdlSqlParser.ParsedTable> newMap = DdlSqlParser.parseDdlScript(newDdlScript);

        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, DdlSqlParser.ParsedTable> entry : newMap.entrySet()) {
            DdlSqlParser.ParsedTable oldTbl = findIgnoreCase(oldMap, entry.getKey());
            out.append(generateAlterMigrationSql(oldTbl, entry.getValue(), dialect)).append("\n");
        }
        return out.toString().trim();
    }

    @NotNull
    public static String generateCreateTableSql(@NotNull DdlSqlParser.ParsedTable table,
                                                @NotNull DatabaseDialect dialect) {
        String tableName = table.getTableName();
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(quoteIdentifier(tableName, dialect)).append(" (\n");
        List<Table> cols = table.getColumns();
        List<String> pkCols = new ArrayList<>();
        for (int i = 0; i < cols.size(); i++) {
            Table col = cols.get(i);
            if (Boolean.TRUE.equals(col.getPrimaryKey())) {
                pkCols.add(quoteIdentifier(col.getTypeName(), dialect));
            }
            sb.append("    ").append(quoteIdentifier(col.getTypeName(), dialect))
                    .append(" ").append(toDialectSqlType(col, dialect));
            if (Boolean.TRUE.equals(col.getPrimaryKey()) || Boolean.FALSE.equals(col.getNullable())) {
                sb.append(" NOT NULL");
            }
            if (dialect == DatabaseDialect.MYSQL && StringUtils.isNotBlank(col.getColumnComment())) {
                sb.append(" COMMENT '").append(escapeSql(col.getColumnComment())).append("'");
            }
            if (i < cols.size() - 1 || !pkCols.isEmpty()) {
                sb.append(",");
            }
            sb.append("\n");
        }
        if (!pkCols.isEmpty()) {
            sb.append("    PRIMARY KEY (").append(String.join(", ", pkCols)).append(")\n");
        }
        if (dialect == DatabaseDialect.MYSQL) {
            sb.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='")
                    .append(escapeSql(table.getTableComment())).append("';\n");
        } else {
            sb.append(");\n");
            if (StringUtils.isNotBlank(table.getTableComment())) {
                sb.append("COMMENT ON TABLE ").append(quoteIdentifier(tableName, dialect))
                        .append(" IS '").append(escapeSql(table.getTableComment())).append("';\n");
            }
            for (Table col : cols) {
                if (StringUtils.isNotBlank(col.getColumnComment())) {
                    sb.append("COMMENT ON COLUMN ").append(quoteIdentifier(tableName, dialect))
                            .append(".").append(quoteIdentifier(col.getTypeName(), dialect))
                            .append(" IS '").append(escapeSql(col.getColumnComment())).append("';\n");
                }
            }
        }
        return sb.toString();
    }

    private static boolean isColumnChanged(Table oldCol, Table newCol) {
        if (!Objects.equals(oldCol.getDataType(), newCol.getDataType())) {
            return true;
        }
        if (!Objects.equals(oldCol.getMaxLength(), newCol.getMaxLength())) {
            return true;
        }
        if (!Objects.equals(oldCol.getNullable(), newCol.getNullable())) {
            return true;
        }
        return !Objects.equals(
                StringUtils.trimToEmpty(oldCol.getColumnComment()),
                StringUtils.trimToEmpty(newCol.getColumnComment())
        );
    }

    private static String buildAddColumnStatement(String tableName, Table col, DatabaseDialect dialect) {
        String tbl = quoteIdentifier(tableName, dialect);
        String colId = quoteIdentifier(col.getTypeName(), dialect);
        String sqlType = toDialectSqlType(col, dialect);
        String nullClause = Boolean.FALSE.equals(col.getNullable()) ? " NOT NULL" : " NULL";
        if (dialect == DatabaseDialect.MYSQL) {
            String commentClause = StringUtils.isNotBlank(col.getColumnComment())
                    ? " COMMENT '" + escapeSql(col.getColumnComment()) + "'"
                    : "";
            return "ALTER TABLE " + tbl + " ADD COLUMN " + colId + " " + sqlType + nullClause + commentClause + ";";
        }
        StringBuilder sb = new StringBuilder();
        if (dialect == DatabaseDialect.ORACLE) {
            sb.append("ALTER TABLE ").append(tbl).append(" ADD (").append(colId).append(" ").append(sqlType).append(nullClause).append(");");
        } else {
            sb.append("ALTER TABLE ").append(tbl).append(" ADD COLUMN ").append(colId).append(" ").append(sqlType).append(nullClause).append(";");
        }
        if (StringUtils.isNotBlank(col.getColumnComment())) {
            sb.append("\nCOMMENT ON COLUMN ").append(tbl).append(".").append(colId)
                    .append(" IS '").append(escapeSql(col.getColumnComment())).append("';");
        }
        return sb.toString();
    }

    private static List<String> buildModifyColumnStatements(String tableName,
                                                            Table oldCol,
                                                            Table newCol,
                                                            DatabaseDialect dialect) {
        List<String> list = new ArrayList<>();
        String tbl = quoteIdentifier(tableName, dialect);
        String colId = quoteIdentifier(newCol.getTypeName(), dialect);
        String sqlType = toDialectSqlType(newCol, dialect);
        String nullClause = Boolean.FALSE.equals(newCol.getNullable()) ? " NOT NULL" : " NULL";

        if (dialect == DatabaseDialect.MYSQL) {
            String commentClause = StringUtils.isNotBlank(newCol.getColumnComment())
                    ? " COMMENT '" + escapeSql(newCol.getColumnComment()) + "'"
                    : "";
            list.add("ALTER TABLE " + tbl + " MODIFY COLUMN " + colId + " " + sqlType + nullClause + commentClause + ";");
        } else if (dialect == DatabaseDialect.POSTGRESQL) {
            if (!Objects.equals(oldCol.getDataType(), newCol.getDataType())
                    || !Objects.equals(oldCol.getMaxLength(), newCol.getMaxLength())) {
                list.add("ALTER TABLE " + tbl + " ALTER COLUMN " + colId + " TYPE " + sqlType + ";");
            }
            if (!Objects.equals(oldCol.getNullable(), newCol.getNullable())) {
                list.add("ALTER TABLE " + tbl + " ALTER COLUMN " + colId
                        + (Boolean.FALSE.equals(newCol.getNullable()) ? " SET NOT NULL;" : " DROP NOT NULL;"));
            }
            if (!Objects.equals(oldCol.getColumnComment(), newCol.getColumnComment())
                    && StringUtils.isNotBlank(newCol.getColumnComment())) {
                list.add("COMMENT ON COLUMN " + tbl + "." + colId + " IS '" + escapeSql(newCol.getColumnComment()) + "';");
            }
        } else {
            list.add("ALTER TABLE " + tbl + " MODIFY (" + colId + " " + sqlType + nullClause + ");");
            if (!Objects.equals(oldCol.getColumnComment(), newCol.getColumnComment())
                    && StringUtils.isNotBlank(newCol.getColumnComment())) {
                list.add("COMMENT ON COLUMN " + tbl + "." + colId + " IS '" + escapeSql(newCol.getColumnComment()) + "';");
            }
        }
        return list;
    }

    private static String buildDropColumnStatement(String tableName, Table oldCol, DatabaseDialect dialect) {
        String tbl = quoteIdentifier(tableName, dialect);
        String colId = quoteIdentifier(oldCol.getTypeName(), dialect);
        return "ALTER TABLE " + tbl + " DROP COLUMN " + colId + ";";
    }

    private static String toDialectSqlType(Table col, DatabaseDialect dialect) {
        String javaType = StringUtils.defaultIfBlank(col.getDataType(), "String");
        int len = (col.getMaxLength() != null && col.getMaxLength() > 0) ? col.getMaxLength() : 128;
        switch (javaType) {
            case "Long":
                return dialect == DatabaseDialect.ORACLE ? "NUMBER(19)" : "BIGINT";
            case "Integer":
                return dialect == DatabaseDialect.ORACLE ? "NUMBER(10)" : "INT";
            case "Short":
                return dialect == DatabaseDialect.ORACLE ? "NUMBER(5)" : "SMALLINT";
            case "BigDecimal":
                return dialect == DatabaseDialect.ORACLE ? "NUMBER(12,2)" : "DECIMAL(12,2)";
            case "Double":
                return dialect == DatabaseDialect.POSTGRESQL ? "DOUBLE PRECISION" : "DOUBLE";
            case "Float":
                return "FLOAT";
            case "Boolean":
                return dialect == DatabaseDialect.POSTGRESQL ? "BOOLEAN" : (dialect == DatabaseDialect.ORACLE ? "NUMBER(1)" : "TINYINT(1)");
            case "LocalDateTime":
                return dialect == DatabaseDialect.MYSQL ? "DATETIME" : "TIMESTAMP";
            default:
                return (dialect == DatabaseDialect.ORACLE ? "VARCHAR2(" : "VARCHAR(") + len + ")";
        }
    }

    private static String quoteIdentifier(String name, DatabaseDialect dialect) {
        if (dialect == DatabaseDialect.MYSQL) {
            return "`" + name + "`";
        }
        return "\"" + name + "\"";
    }

    private static String escapeSql(String text) {
        return StringUtils.defaultString(text).replace("'", "''");
    }

    @Nullable
    private static DdlSqlParser.ParsedTable findIgnoreCase(Map<String, DdlSqlParser.ParsedTable> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        for (Map.Entry<String, DdlSqlParser.ParsedTable> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
