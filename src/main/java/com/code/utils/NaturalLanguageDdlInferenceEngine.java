package com.code.utils;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自然语言业务领域描述转标准 DDL SQL 智能建模引擎（v4.0 M3）
 *
 * 核心能力：
 * 1. 支持中文/英文自然语言需求描述（例如：「创建商品库存表 product_stock：包含商品ID、仓库编码、库存数量、单价金额、库存状态（0：正常，1：缺货）、负责人手机号、负责人邮箱」）。
 * 2. 自动提取或翻译表名、表注释、字段英文名、SQL 类型、长度约束、非空属性、枚举括号注释及审计字段（id / create_time / update_time）。
 * 3. 支持一段话描述主子表结构（以分号或换行分隔多张表），自动为子表推断并生成指向主表的 FOREIGN KEY 约束。
 *
 * @author syrobin
 */
public final class NaturalLanguageDdlInferenceEngine {

    private static final Map<String, String> DOMAIN_DICTIONARY = new LinkedHashMap<>();

    static {
        DOMAIN_DICTIONARY.put("用户", "user");
        DOMAIN_DICTIONARY.put("客户", "customer");
        DOMAIN_DICTIONARY.put("会员", "member");
        DOMAIN_DICTIONARY.put("部门", "dept");
        DOMAIN_DICTIONARY.put("组织", "org");
        DOMAIN_DICTIONARY.put("角色", "role");
        DOMAIN_DICTIONARY.put("权限", "permission");
        DOMAIN_DICTIONARY.put("菜单", "menu");
        DOMAIN_DICTIONARY.put("订单", "order");
        DOMAIN_DICTIONARY.put("商品", "product");
        DOMAIN_DICTIONARY.put("库存", "stock");
        DOMAIN_DICTIONARY.put("仓库", "warehouse");
        DOMAIN_DICTIONARY.put("支付", "payment");
        DOMAIN_DICTIONARY.put("发票", "invoice");
        DOMAIN_DICTIONARY.put("合同", "contract");
        DOMAIN_DICTIONARY.put("明细", "item");
        DOMAIN_DICTIONARY.put("记录", "record");
        DOMAIN_DICTIONARY.put("日志", "log");
        DOMAIN_DICTIONARY.put("字典", "dict");
        DOMAIN_DICTIONARY.put("配置", "config");
        DOMAIN_DICTIONARY.put("消息", "message");
        DOMAIN_DICTIONARY.put("通知", "notice");
        DOMAIN_DICTIONARY.put("任务", "task");
        DOMAIN_DICTIONARY.put("分类", "category");
        DOMAIN_DICTIONARY.put("上级", "parent");
        DOMAIN_DICTIONARY.put("父级", "parent");
        DOMAIN_DICTIONARY.put("父", "parent");
        DOMAIN_DICTIONARY.put("负责人", "manager");
        DOMAIN_DICTIONARY.put("联系人", "contact");
        DOMAIN_DICTIONARY.put("手机号", "mobile");
        DOMAIN_DICTIONARY.put("手机", "mobile");
        DOMAIN_DICTIONARY.put("电话", "phone");
        DOMAIN_DICTIONARY.put("邮箱", "email");
        DOMAIN_DICTIONARY.put("邮件", "email");
        // 更具体词优先：避免「单价金额」按字典顺序变成 amount_unit_price
        DOMAIN_DICTIONARY.put("单价金额", "unit_price_amount");
        DOMAIN_DICTIONARY.put("单价", "unit_price");
        DOMAIN_DICTIONARY.put("价格", "price");
        DOMAIN_DICTIONARY.put("金额", "amount");
        DOMAIN_DICTIONARY.put("费用", "fee");
        DOMAIN_DICTIONARY.put("数量", "quantity");
        DOMAIN_DICTIONARY.put("阈值", "threshold");
        DOMAIN_DICTIONARY.put("排序", "sort_order");
        DOMAIN_DICTIONARY.put("状态", "status");
        DOMAIN_DICTIONARY.put("类型", "type");
        DOMAIN_DICTIONARY.put("级别", "level");
        DOMAIN_DICTIONARY.put("编码", "code");
        DOMAIN_DICTIONARY.put("编号", "no");
        DOMAIN_DICTIONARY.put("名称", "name");
        DOMAIN_DICTIONARY.put("姓名", "name");
        DOMAIN_DICTIONARY.put("标题", "title");
        DOMAIN_DICTIONARY.put("备注", "remark");
        DOMAIN_DICTIONARY.put("说明", "description");
        DOMAIN_DICTIONARY.put("描述", "description");
        DOMAIN_DICTIONARY.put("地址", "address");
        DOMAIN_DICTIONARY.put("时间", "time");
        DOMAIN_DICTIONARY.put("日期", "date");
    }

    private static final Pattern EXPLICIT_IDENTIFIER_PATTERN = Pattern.compile("([a-zA-Z][a-zA-Z0-9_]{1,48})");

    private NaturalLanguageDdlInferenceEngine() {
    }

    /**
     * 将自然语言业务描述推导为标准化的 MySQL/通用 CREATE TABLE DDL 脚本
     */
    @NotNull
    public static String inferDdlFromNaturalLanguage(@NotNull String prompt) {
        if (StringUtils.isBlank(prompt)) {
            return "";
        }
        String[] blocks = prompt.trim().split("\\n\\s*\\n|\\r\\n\\s*\\r\\n");
        StringBuilder fullDdl = new StringBuilder();
        String firstMasterTableName = null;

        for (String rawBlock : blocks) {
            String block = rawBlock.trim();
            if (block.isEmpty()) {
                continue;
            }
            TableBlueprint blueprint = parseTableBlueprint(block, firstMasterTableName);
            if (firstMasterTableName == null) {
                firstMasterTableName = blueprint.tableName;
            }
            if (fullDdl.length() > 0) {
                fullDdl.append("\n\n");
            }
            fullDdl.append(renderCreateTableDdl(blueprint));
        }
        return fullDdl.toString();
    }

    private static class ColumnBlueprint {
        String columnName;
        String sqlType;
        boolean nullable;
        String defaultValue;
        String comment;
    }

    private static class TableBlueprint {
        String tableName;
        String tableComment;
        List<ColumnBlueprint> columns = new ArrayList<>();
        String foreignKeyCol;
        String referencedTable;
    }

    private static TableBlueprint parseTableBlueprint(String text, String previousMasterTable) {
        TableBlueprint bp = new TableBlueprint();

        // 1. 拆分表头描述与字段列表（按第一个冒号 : / ： 或 "包含" / "字段" 拆分）
        String headerPart = text;
        String fieldsPart = "";
        int colonIdx = indexOfColonOutsideParens(text);
        if (colonIdx > 0) {
            headerPart = text.substring(0, colonIdx).trim();
            fieldsPart = text.substring(colonIdx + 1).trim();
        } else {
            Matcher includeMatcher = Pattern.compile("(?:包含|含|字段有|字段包括|fields|columns)\\s*(.+)$", Pattern.CASE_INSENSITIVE).matcher(text);
            if (includeMatcher.find()) {
                headerPart = text.substring(0, includeMatcher.start()).trim();
                fieldsPart = includeMatcher.group(1).trim();
            }
        }

        bp.tableName = inferTableName(headerPart);
        bp.tableComment = inferTableComment(headerPart, bp.tableName);

        Set<String> addedCols = new LinkedHashSet<>();

        // 默认注入主键 id
        ColumnBlueprint pk = new ColumnBlueprint();
        pk.columnName = "id";
        pk.sqlType = "bigint";
        pk.nullable = false;
        pk.comment = "主键ID";
        bp.columns.add(pk);
        addedCols.add("id");

        // 2. 解析每个字段短语（按括号外逗号、顿号或分号切分）
        List<String> fieldTokens = splitTokensOutsideParens(fieldsPart);
        int customIndex = 1;
        for (String rawToken : fieldTokens) {
            String token = rawToken.replaceAll("^(?:包含|以及|和|及|字段|属性)\\s*", "").trim();
            token = token.replaceAll("[。.;；]+$", "").trim();
            if (token.isEmpty()) {
                continue;
            }
            ColumnBlueprint col = inferColumnBlueprint(token, customIndex++);
            if (col != null && !addedCols.contains(col.columnName.toLowerCase(Locale.ROOT))) {
                bp.columns.add(col);
                addedCols.add(col.columnName.toLowerCase(Locale.ROOT));

                // 若当前表非主表且字段名指向前序主表外键，自动绑定 FOREIGN KEY
                if (previousMasterTable != null && !previousMasterTable.equalsIgnoreCase(bp.tableName)) {
                    String masterCore = previousMasterTable.replaceFirst("^(?:t_|sys_|biz_)", "");
                    if (col.columnName.equalsIgnoreCase(masterCore + "_id")
                            || col.columnName.equalsIgnoreCase(previousMasterTable + "_id")) {
                        bp.foreignKeyCol = col.columnName;
                        bp.referencedTable = previousMasterTable;
                    }
                }
            }
        }

        // 3. 若未显式声明创建时间，自动补齐标准审计字段 create_time / update_time
        if (!addedCols.contains("create_time") && !addedCols.contains("create_date_time")) {
            ColumnBlueprint createTime = new ColumnBlueprint();
            createTime.columnName = "create_time";
            createTime.sqlType = "datetime";
            createTime.nullable = true;
            createTime.defaultValue = "CURRENT_TIMESTAMP";
            createTime.comment = "创建时间";
            bp.columns.add(createTime);
        }
        if (!addedCols.contains("update_time") && !addedCols.contains("modify_date_time")) {
            ColumnBlueprint updateTime = new ColumnBlueprint();
            updateTime.columnName = "update_time";
            updateTime.sqlType = "datetime";
            updateTime.nullable = true;
            updateTime.defaultValue = "CURRENT_TIMESTAMP";
            updateTime.comment = "更新时间";
            bp.columns.add(updateTime);
        }

        return bp;
    }

    private static ColumnBlueprint inferColumnBlueprint(String rawToken, int seq) {
        ColumnBlueprint col = new ColumnBlueprint();
        col.comment = rawToken;

        // 提取括号前的核心词以推断字段名
        String withoutParens = rawToken.replaceAll("（[^）]*）|\\([^)]*\\)|\\[[^\\]]*\\]", "").trim();
        String explicitColName = null;
        Matcher m = EXPLICIT_IDENTIFIER_PATTERN.matcher(withoutParens);
        while (m.find()) {
            String candidate = m.group(1);
            if (!"ID".equalsIgnoreCase(candidate)) {
                explicitColName = camelToSnake(candidate);
                break;
            }
        }

        if (explicitColName != null) {
            col.columnName = explicitColName;
        } else {
            col.columnName = translateChinesePhraseToSnake(withoutParens, "field_" + seq);
        }

        if ("id".equalsIgnoreCase(col.columnName)) {
            return null;
        }

        // 判断是否为带键值枚举的注释
        boolean hasEnumItems = !EnumCommentParser.parseEnumItems(rawToken).isEmpty();
        String lowerName = col.columnName.toLowerCase(Locale.ROOT);

        if (hasEnumItems || lowerName.endsWith("_status") || lowerName.equals("status")
                || lowerName.endsWith("_type") || lowerName.equals("type")) {
            col.sqlType = "tinyint";
            col.nullable = false;
            col.defaultValue = "0";
        } else if (lowerName.endsWith("_id") || lowerName.equals("parent_id") || lowerName.equals("pid")) {
            col.sqlType = "bigint";
            col.nullable = false;
            if (lowerName.contains("parent") || lowerName.equals("pid")) {
                col.defaultValue = "0";
            }
        } else if (lowerName.contains("amount") || lowerName.contains("price") || lowerName.contains("fee")
                || withoutParens.contains("金额") || withoutParens.contains("单价") || withoutParens.contains("价格")) {
            col.sqlType = "decimal(12,2)";
            col.nullable = false;
            col.defaultValue = "0.00";
        } else if (lowerName.contains("quantity") || lowerName.contains("count") || lowerName.contains("stock")
                || lowerName.contains("threshold") || lowerName.contains("sort")
                || withoutParens.contains("数量") || withoutParens.contains("库存") || withoutParens.contains("阈值")) {
            col.sqlType = "int";
            col.nullable = false;
            col.defaultValue = "0";
        } else if (lowerName.contains("mobile") || lowerName.contains("phone")) {
            col.sqlType = "varchar(20)";
            col.nullable = false;
        } else if (lowerName.contains("email")) {
            col.sqlType = "varchar(128)";
            col.nullable = true;
        } else if (lowerName.endsWith("_code") || lowerName.endsWith("_no") || lowerName.equals("code")) {
            col.sqlType = "varchar(64)";
            col.nullable = false;
        } else if (lowerName.endsWith("_time") || lowerName.endsWith("_date")) {
            col.sqlType = "datetime";
            col.nullable = true;
        } else if (lowerName.contains("remark") || lowerName.contains("description") || withoutParens.contains("备注")) {
            col.sqlType = "varchar(255)";
            col.nullable = true;
        } else {
            col.sqlType = "varchar(64)";
            col.nullable = false;
        }

        return col;
    }

    private static String renderCreateTableDdl(TableBlueprint bp) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE `").append(bp.tableName).append("` (\n");
        for (int i = 0; i < bp.columns.size(); i++) {
            ColumnBlueprint c = bp.columns.get(i);
            sb.append("  `").append(c.columnName).append("` ").append(c.sqlType);
            if ("id".equalsIgnoreCase(c.columnName)) {
                sb.append(" NOT NULL PRIMARY KEY");
            } else if (!c.nullable) {
                sb.append(" NOT NULL");
            } else {
                sb.append(" DEFAULT NULL");
            }
            if (StringUtils.isNotBlank(c.defaultValue) && !"id".equalsIgnoreCase(c.columnName) && c.nullable == false) {
                sb.append(" DEFAULT ").append(c.defaultValue);
            } else if ("CURRENT_TIMESTAMP".equalsIgnoreCase(c.defaultValue)) {
                sb = new StringBuilder(sb.toString().replaceAll(" DEFAULT NULL$", " DEFAULT CURRENT_TIMESTAMP"));
            }
            sb.append(" COMMENT '").append(c.comment.replace("'", "''")).append("'");
            if (i < bp.columns.size() - 1 || StringUtils.isNotBlank(bp.foreignKeyCol)) {
                sb.append(",");
            }
            sb.append("\n");
        }
        if (StringUtils.isNotBlank(bp.foreignKeyCol) && StringUtils.isNotBlank(bp.referencedTable)) {
            sb.append("  CONSTRAINT `fk_").append(bp.tableName).append("_").append(bp.foreignKeyCol)
                    .append("` FOREIGN KEY (`").append(bp.foreignKeyCol).append("`) REFERENCES `")
                    .append(bp.referencedTable).append("` (`id`)\n");
        }
        sb.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='")
                .append(bp.tableComment.replace("'", "''")).append("';");
        return sb.toString();
    }

    private static String inferTableName(String header) {
        Matcher m = EXPLICIT_IDENTIFIER_PATTERN.matcher(header);
        while (m.find()) {
            String candidate = m.group(1);
            if (!"create".equalsIgnoreCase(candidate) && !"table".equalsIgnoreCase(candidate) && !"id".equalsIgnoreCase(candidate)) {
                return camelToSnake(candidate);
            }
        }
        String cleaned = header.replaceAll("^(?:请|帮我|创建|新建|生成|设计|一张|一个)+", "")
                .replaceAll("(?:表|数据结构|模型)$", "").trim();
        return translateChinesePhraseToSnake(cleaned, "biz_entity");
    }

    private static String inferTableComment(String header, String tableName) {
        String cleaned = header.replace(tableName, "")
                .replaceAll("^(?:请|帮我|创建|新建|生成|设计|一张|一个)+", "")
                .replaceAll("[()（）\\[\\]]", "")
                .trim();
        if (cleaned.isEmpty()) {
            return tableName + "表";
        }
        return cleaned.endsWith("表") ? cleaned : cleaned + "表";
    }

    private static String translateChinesePhraseToSnake(String phrase, String fallback) {
        if (StringUtils.isBlank(phrase)) {
            return fallback;
        }
        // 按键长降序，从左到右最长优先，保证「单价金额」「手机号」等复合词不被短词拆坏
        List<Map.Entry<String, String>> dictByLength = new ArrayList<>(DOMAIN_DICTIONARY.entrySet());
        dictByLength.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

        List<String> segments = new ArrayList<>();
        String working = phrase.trim();
        while (!working.isEmpty()) {
            working = working.replaceFirst("^\\s+", "");
            if (working.isEmpty()) {
                break;
            }
            Map.Entry<String, String> hit = null;
            for (Map.Entry<String, String> entry : dictByLength) {
                if (working.startsWith(entry.getKey())) {
                    hit = entry;
                    break;
                }
            }
            if (hit != null) {
                if (!segments.contains(hit.getValue())) {
                    segments.add(hit.getValue());
                }
                working = working.substring(hit.getKey().length());
                continue;
            }
            // 未命中词典：跳过当前字符，继续向右扫描
            working = working.substring(1);
        }

        if (phrase.toUpperCase(Locale.ROOT).contains("ID") && !segments.contains("id")) {
            segments.add("id");
        }
        if (segments.isEmpty()) {
            return fallback;
        }
        return String.join("_", segments);
    }

    private static String camelToSnake(String input) {
        if (StringUtils.isBlank(input)) {
            return input;
        }
        return input.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private static int indexOfColonOutsideParens(String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(' || c == '（' || c == '[') {
                depth++;
            } else if (c == ')' || c == '）' || c == ']') {
                depth = Math.max(0, depth - 1);
            } else if ((c == ':' || c == '：') && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> splitTokensOutsideParens(String text) {
        List<String> list = new ArrayList<>();
        if (StringUtils.isBlank(text)) {
            return list;
        }
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(' || c == '（' || c == '[') {
                depth++;
                current.append(c);
            } else if (c == ')' || c == '）' || c == ']') {
                depth = Math.max(0, depth - 1);
                current.append(c);
            } else if ((c == ',' || c == '，' || c == '、' || c == ';' || c == '；') && depth == 0) {
                if (current.length() > 0) {
                    list.add(current.toString().trim());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            list.add(current.toString().trim());
        }
        return list;
    }
}
