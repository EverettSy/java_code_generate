package com.code.utils;

import com.code.utils.entity.SubTableRelation;
import com.code.utils.entity.Table;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 表关联拓扑与树形自关联自动发现引擎（v4.0 M1：支持 TreeVO 自关联树形表检测、DDL FOREIGN KEY 解析与同名外键主子表自动发现）
 *
 * @author syrobin
 */
public class RelationTopologyDetector {

    private static final Set<String> TREE_PARENT_COLUMN_NAMES = new HashSet<>(Arrays.asList(
            "parent_id", "pid", "parent_code", "p_id", "upper_id"
    ));

    /**
     * 检测给定表字段列表是否构成自关联树形表（如包含 parent_id / pid / parent_code），返回父节点关联字段
     */
    @Nullable
    public static Table detectTreeParentColumn(@Nullable List<Table> columns) {
        if (columns == null || columns.isEmpty()) {
            return null;
        }
        for (Table col : columns) {
            if (col == null || StringUtils.isBlank(col.getTypeName())) {
                continue;
            }
            String lower = col.getTypeName().trim().toLowerCase(Locale.ROOT);
            if (TREE_PARENT_COLUMN_NAMES.contains(lower)) {
                return col;
            }
        }
        return null;
    }

    /**
     * 查找表的主键列（若无显式主键则优先匹配 id 列或第一列）
     */
    @Nullable
    public static Table detectPrimaryKeyColumn(@Nullable List<Table> columns) {
        if (columns == null || columns.isEmpty()) {
            return null;
        }
        for (Table col : columns) {
            if (col != null && Boolean.TRUE.equals(col.getPrimaryKey())) {
                return col;
            }
        }
        for (Table col : columns) {
            if (col != null && "id".equalsIgnoreCase(col.getTypeName())) {
                return col;
            }
        }
        return columns.get(0);
    }

    /**
     * 从多表 DDL 解析结果或已缓存的表字段映射中，为指定主表自动发现所有 One-to-Many 子表关联关系：
     * 1. 优先匹配 DDL 中显式声明的 FOREIGN KEY (fk_col) REFERENCES master_table(pk_col)
     * 2. 其次按命名规范匹配：子表名以主表名开头或存在语义从属，且子表包含 `{master_table}_id` 或 `{stripped_master}_id` 外键列
     */
    @NotNull
    public static List<SubTableRelation> discoverSubTableRelations(@NotNull String masterTableName,
                                                                   @Nullable Map<String, DdlSqlParser.ParsedTable> parsedDdlMap,
                                                                   @Nullable Map<String, List<Table>> resolvedColumnsMap,
                                                                   @Nullable String tablePrefixes) {
        if (StringUtils.isBlank(masterTableName)) {
            return Collections.emptyList();
        }
        String normMaster = masterTableName.trim();
        String lowerMaster = normMaster.toLowerCase(Locale.ROOT);
        String strippedMaster = stripTablePrefix(lowerMaster, tablePrefixes);

        Map<String, List<Table>> allTableCols = new LinkedHashMap<>();
        Map<String, String> tableComments = new HashMap<>();
        Map<String, Map<String, String>> explicitFkMap = new HashMap<>();

        if (parsedDdlMap != null) {
            for (Map.Entry<String, DdlSqlParser.ParsedTable> entry : parsedDdlMap.entrySet()) {
                DdlSqlParser.ParsedTable pt = entry.getValue();
                if (pt != null) {
                    String tName = StringUtils.defaultIfBlank(pt.getTableName(), entry.getKey());
                    allTableCols.put(tName, pt.getColumns());
                    tableComments.put(tName, StringUtils.defaultString(pt.getTableComment()));
                    if (pt.getForeignKeys() != null && !pt.getForeignKeys().isEmpty()) {
                        explicitFkMap.put(tName, pt.getForeignKeys());
                    }
                }
            }
        }
        if (resolvedColumnsMap != null) {
            for (Map.Entry<String, List<Table>> entry : resolvedColumnsMap.entrySet()) {
                allTableCols.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        List<Table> masterCols = findColumnsIgnoreCase(allTableCols, normMaster);
        Table masterPkCol = detectPrimaryKeyColumn(masterCols);
        String masterPkName = masterPkCol != null && StringUtils.isNotBlank(masterPkCol.getTypeName())
                ? masterPkCol.getTypeName()
                : "id";

        Set<String> candidateFkNames = new LinkedHashSet<>();
        candidateFkNames.add(lowerMaster + "_" + masterPkName.toLowerCase(Locale.ROOT));
        candidateFkNames.add(lowerMaster + "_id");
        if (StringUtils.isNotBlank(strippedMaster) && !strippedMaster.equals(lowerMaster)) {
            candidateFkNames.add(strippedMaster + "_" + masterPkName.toLowerCase(Locale.ROOT));
            candidateFkNames.add(strippedMaster + "_id");
        }
        // 若主表形如 user_order，也支持子表 user_order_item 使用 order_id 作为外键
        int lastUnderscore = strippedMaster.lastIndexOf('_');
        if (lastUnderscore > 0 && lastUnderscore < strippedMaster.length() - 1) {
            String tailNoun = strippedMaster.substring(lastUnderscore + 1);
            if (tailNoun.length() >= 3) {
                candidateFkNames.add(tailNoun + "_id");
            }
        }

        List<SubTableRelation> discovered = new ArrayList<>();
        Set<String> addedSubTables = new HashSet<>();

        for (Map.Entry<String, List<Table>> entry : allTableCols.entrySet()) {
            String candidateTable = entry.getKey();
            if (candidateTable == null || candidateTable.equalsIgnoreCase(normMaster)) {
                continue;
            }
            List<Table> subCols = entry.getValue();
            if (subCols == null || subCols.isEmpty()) {
                continue;
            }

            // 1. 检查显式 DDL FOREIGN KEY 约束
            Map<String, String> fks = explicitFkMap.get(candidateTable);
            if (fks != null) {
                for (Map.Entry<String, String> fkEntry : fks.entrySet()) {
                    String fkCol = fkEntry.getKey();
                    String refTarget = fkEntry.getValue(); // 格式: master_table.pk_col
                    if (StringUtils.isNotBlank(refTarget)) {
                        String[] parts = refTarget.split("\\.");
                        String refTable = parts[0];
                        String refPk = parts.length > 1 ? parts[1] : masterPkName;
                        if (refTable.equalsIgnoreCase(normMaster) && addedSubTables.add(candidateTable.toLowerCase(Locale.ROOT))) {
                            String comment = buildSubRelationComment(candidateTable, tableComments.get(candidateTable), tablePrefixes);
                            SubTableRelation rel = new SubTableRelation(candidateTable, fkCol, refPk, comment);
                            rel.recomputeDerivedNames(tablePrefixes);
                            discovered.add(rel);
                        }
                    }
                }
            }
            if (addedSubTables.contains(candidateTable.toLowerCase(Locale.ROOT))) {
                continue;
            }

            // 2. 按字段命名与表名前缀从属关系自动推导
            String lowerSubTable = candidateTable.toLowerCase(Locale.ROOT);
            String strippedSubTable = stripTablePrefix(lowerSubTable, tablePrefixes);
            boolean tableNameRelated = strippedSubTable.startsWith(strippedMaster + "_");

            for (Table col : subCols) {
                if (col == null || StringUtils.isBlank(col.getTypeName())) {
                    continue;
                }
                String colLower = col.getTypeName().trim().toLowerCase(Locale.ROOT);
                boolean exactFullFk = colLower.equals(lowerMaster + "_id") || colLower.equals(strippedMaster + "_id");
                boolean shortNounFk = tableNameRelated && candidateFkNames.contains(colLower);
                if (exactFullFk || shortNounFk) {
                    if (addedSubTables.add(lowerSubTable)) {
                        String comment = buildSubRelationComment(candidateTable, tableComments.get(candidateTable), tablePrefixes);
                        SubTableRelation rel = new SubTableRelation(candidateTable, col.getTypeName(), masterPkName, comment);
                        rel.recomputeDerivedNames(tablePrefixes);
                        discovered.add(rel);
                    }
                    break;
                }
            }
        }

        return discovered;
    }

    private static List<Table> findColumnsIgnoreCase(Map<String, List<Table>> map, String table) {
        List<Table> direct = map.get(table);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, List<Table>> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(table)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String stripTablePrefix(String lowerTableName, @Nullable String tablePrefixes) {
        if (StringUtils.isBlank(lowerTableName) || StringUtils.isBlank(tablePrefixes)) {
            return lowerTableName;
        }
        for (String rawPrefix : tablePrefixes.split("[,;\\s]+")) {
            String p = rawPrefix.trim().toLowerCase(Locale.ROOT);
            if (!p.isEmpty() && lowerTableName.startsWith(p) && lowerTableName.length() > p.length()) {
                return lowerTableName.substring(p.length());
            }
        }
        return lowerTableName;
    }

    private static String buildSubRelationComment(String subTableName, @Nullable String rawTableComment, @Nullable String tablePrefixes) {
        if (StringUtils.isNotBlank(rawTableComment)) {
            return rawTableComment.trim() + "列表";
        }
        return CodeGenerateUtils.toUpper(subTableName, tablePrefixes) + "子表列表";
    }
}
