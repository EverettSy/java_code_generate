package com.code.utils;

import com.code.utils.entity.BeanQuery;
import com.code.utils.entity.ServiceQuery;
import com.code.utils.entity.Table;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 智能查询规则推导引擎（v4.0 M1：基于表字段名称、数据类型与注释语义自动推导 BeanQuery 扩展属性与 ServiceQuery 查询规则）
 *
 * 推导策略：
 * 1. 时间/日期字段（LocalDateTime / Date / LocalDate 或后缀为 _time / _date / _at）：
 *    自动生成起止时间区间查询（type="9" Between），并自动在 BeanQuery 中补齐 startXxx 与 endXxx 扩展属性。
 * 2. 名称/标题/描述类文本字段（后缀为 _name / _title / _desc / _remark / _address / _content 或注释含“名称/标题/姓名”）：
 *    自动生成全模糊查询（type="1" Like）。
 * 3. 编码/单号/手机号类前缀特征字段（后缀为 _no / _code / _sn / _mobile / _phone / _email 或注释含“编号/编码/单号”）：
 *    自动生成右模糊查询（type="2" LikeRight，兼顾 B+Tree 索引前缀命中）。
 * 4. 状态/类型/外键类离散或精确字段（后缀为 _status / _state / _type / _flag / _id 或含枚举注释）：
 *    自动生成精确匹配查询（type="0" Eq，自动跳过主键 id）。
 *
 * @author syrobin
 */
public class SmartQueryRuleInferencer {

    public static class InferredQueryRules {
        private final List<BeanQuery> beanQueries;
        private final List<ServiceQuery> serviceQueries;

        public InferredQueryRules(List<BeanQuery> beanQueries, List<ServiceQuery> serviceQueries) {
            this.beanQueries = beanQueries != null ? beanQueries : new ArrayList<>();
            this.serviceQueries = serviceQueries != null ? serviceQueries : new ArrayList<>();
        }

        @NotNull
        public List<BeanQuery> getBeanQueries() {
            return beanQueries;
        }

        @NotNull
        public List<ServiceQuery> getServiceQueries() {
            return serviceQueries;
        }
    }

    /**
     * 兼容简写别名 InferredRules
     */
    public static class InferredRules extends InferredQueryRules {
        public InferredRules(List<BeanQuery> beanQueries, List<ServiceQuery> serviceQueries) {
            super(beanQueries, serviceQueries);
        }
    }

    /**
     * 根据表字段元数据列表自动推导 BeanQuery 与 ServiceQuery 规则集
     */
    @NotNull
    public static InferredRules inferFromColumns(@NotNull List<Table> columns) {
        List<BeanQuery> beanQueries = new ArrayList<>();
        List<ServiceQuery> serviceQueries = new ArrayList<>();
        Set<String> existingBeanNames = new HashSet<>();
        Set<String> existingServiceCols = new HashSet<>();

        for (Table col : columns) {
            if (col == null || StringUtils.isBlank(col.getTypeName()) || StringUtils.isBlank(col.getColumnName())) {
                continue;
            }
            String rawCol = col.getTypeName().trim();
            String lowerCol = rawCol.toLowerCase(Locale.ROOT);
            String propUpper = StringUtils.defaultIfBlank(col.getColumnNameUpper(), StringUtils.capitalize(col.getColumnName()));
            String dataType = StringUtils.defaultString(col.getDataType(), "String");
            String comment = cleanCommentSummary(col.getColumnComment(), propUpper);

            // 跳过主键与逻辑删除等不作为常规筛选条件的系统字段
            if (Boolean.TRUE.equals(col.getPrimaryKey())
                    || "id".equals(lowerCol)
                    || "is_deleted".equals(lowerCol)
                    || "deleted".equals(lowerCol)
                    || "version".equals(lowerCol)) {
                continue;
            }

            String wrappedCol = "`" + rawCol.replace("`", "") + "`";

            // 1. 时间/日期字段 -> Between 区间查询 (type = "8") + 起止 BeanQuery 字段
            if (isDateOrTimeColumn(lowerCol, dataType)) {
                String startUpper = "Start" + propUpper;
                String endUpper = "End" + propUpper;
                String startField = StringUtils.uncapitalize(startUpper);
                String endField = StringUtils.uncapitalize(endUpper);

                if (existingBeanNames.add(startField)) {
                    BeanQuery startBq = new BeanQuery();
                    startBq.setName(startField);
                    startBq.setFormat(dataType);
                    startBq.setType("LocalDateTime".equals(dataType) || "Date".equals(dataType) ? "4" : "0");
                    startBq.setDescription(comment + "（起）");
                    beanQueries.add(startBq);
                }
                if (existingBeanNames.add(endField)) {
                    BeanQuery endBq = new BeanQuery();
                    endBq.setName(endField);
                    endBq.setFormat(dataType);
                    endBq.setType("LocalDateTime".equals(dataType) || "Date".equals(dataType) ? "4" : "0");
                    endBq.setDescription(comment + "（止）");
                    beanQueries.add(endBq);
                }

                if (existingServiceCols.add(lowerCol + "#8")) {
                    ServiceQuery sq = new ServiceQuery();
                    sq.setType("8");
                    sq.setColumn(wrappedCol);
                    sq.setParameter(propUpper);
                    sq.setParameterType(false);
                    sq.setStart(startUpper);
                    sq.setEnd(endUpper);
                    serviceQueries.add(sq);
                }
                continue;
            }

            // 2. 名称/标题/描述/编号/编码类 String 字段 -> 模糊查询 (type = "1")
            if ("String".equals(dataType)
                    && (isFuzzyNameOrTitleColumn(lowerCol, col.getColumnComment())
                    || isPrefixCodeOrNumberColumn(lowerCol, col.getColumnComment()))) {
                if (existingServiceCols.add(lowerCol + "#1")) {
                    ServiceQuery sq = new ServiceQuery();
                    sq.setType("1");
                    sq.setColumn(wrappedCol);
                    sq.setParameter(propUpper);
                    sq.setParameterType(true);
                    serviceQueries.add(sq);
                }
                continue;
            }

            // 4. 状态/类型/外键 ID 或包含枚举定义的字段 -> Eq 精确查询 (type = "0")
            if (isExactFilterColumn(lowerCol, dataType, col.getColumnComment())) {
                if (existingServiceCols.add(lowerCol + "#0")) {
                    ServiceQuery sq = new ServiceQuery();
                    sq.setType("0");
                    sq.setColumn(wrappedCol);
                    sq.setParameter(propUpper);
                    sq.setParameterType("String".equals(dataType));
                    serviceQueries.add(sq);
                }
            }
        }

        return new InferredRules(beanQueries, serviceQueries);
    }

    private static boolean isDateOrTimeColumn(String lowerCol, String dataType) {
        if ("LocalDateTime".equals(dataType) || "Date".equals(dataType) || "LocalDate".equals(dataType)) {
            return true;
        }
        return lowerCol.endsWith("_time")
                || lowerCol.endsWith("_date")
                || lowerCol.endsWith("_datetime")
                || lowerCol.endsWith("_at");
    }

    private static boolean isFuzzyNameOrTitleColumn(String lowerCol, String comment) {
        if (lowerCol.endsWith("_name") || lowerCol.equals("name")
                || lowerCol.endsWith("_title") || lowerCol.equals("title")
                || lowerCol.endsWith("_desc") || lowerCol.endsWith("_description")
                || lowerCol.endsWith("_remark") || lowerCol.endsWith("_address")
                || lowerCol.endsWith("_content") || lowerCol.endsWith("_keyword")) {
            return true;
        }
        String c = StringUtils.defaultString(comment);
        return c.contains("名称") || c.contains("标题") || c.contains("姓名") || c.contains("昵称") || c.contains("地址") || c.contains("备注");
    }

    private static boolean isPrefixCodeOrNumberColumn(String lowerCol, String comment) {
        if (lowerCol.endsWith("_no") || lowerCol.endsWith("_code") || lowerCol.equals("code")
                || lowerCol.endsWith("_sn") || lowerCol.endsWith("_num")
                || lowerCol.endsWith("_mobile") || lowerCol.endsWith("_phone")
                || lowerCol.endsWith("_email") || lowerCol.endsWith("_account")) {
            return true;
        }
        String c = StringUtils.defaultString(comment);
        return c.contains("编号") || c.contains("编码") || c.contains("单号") || c.contains("流水号") || c.contains("手机") || c.contains("邮箱");
    }

    private static boolean isExactFilterColumn(String lowerCol, String dataType, String comment) {
        if (lowerCol.endsWith("_status") || lowerCol.equals("status")
                || lowerCol.endsWith("_state") || lowerCol.equals("state")
                || lowerCol.endsWith("_type") || lowerCol.equals("type")
                || lowerCol.endsWith("_flag") || lowerCol.startsWith("is_")
                || lowerCol.endsWith("_id") || lowerCol.equals("parent_id") || lowerCol.equals("pid")) {
            return true;
        }
        if ("Boolean".equals(dataType) || "Integer".equals(dataType) || "Short".equals(dataType)) {
            return !EnumCommentParser.parseEnumItems(comment).isEmpty();
        }
        return false;
    }

    private static String cleanCommentSummary(String comment, String fallback) {
        if (StringUtils.isBlank(comment)) {
            return fallback;
        }
        String trimmed = comment.trim();
        int parenIdx = trimmed.indexOf('(');
        int fullParenIdx = trimmed.indexOf('（');
        int cut = -1;
        if (parenIdx > 0 && fullParenIdx > 0) {
            cut = Math.min(parenIdx, fullParenIdx);
        } else if (parenIdx > 0) {
            cut = parenIdx;
        } else if (fullParenIdx > 0) {
            cut = fullParenIdx;
        }
        return cut > 0 ? trimmed.substring(0, cut).trim() : trimmed;
    }
}
