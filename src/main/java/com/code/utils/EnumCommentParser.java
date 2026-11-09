package com.code.utils;

import com.code.utils.entity.EnumsEntityContent;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 增强型数据库字段枚举注释解析器
 *
 * 兼容格式示例：
 * 1. 全角格式：类型（0：首页，1：百万年薪，2：报价方案）
 * 2. 半角格式：状态(0:禁用, 1:启用; 2:审核中)
 * 3. 混合括号与分隔符：状态[0=默认、1=正常]
 */
public class EnumCommentParser {

    /**
     * 匹配最外层或最后一个括号对（支持全角（）、半角()、中括号[]）
     */
    private static final Pattern BRACKET_PATTERN = Pattern.compile("[（(\\[]([^）)\\]]+)[）)\\]]");

    /**
     * 枚举项之间的分隔符（全角逗号、半角逗号、全角分号、半角分号、顿号）
     */
    private static final String ITEM_SPLIT_REGEX = "[，,；;、]+";

    /**
     * 键值之间的分隔符（全角冒号、半角冒号、等号、破折号）
     */
    private static final String KV_SPLIT_REGEX = "[：:=\\-]+";

    /**
     * 从数据库字段注释中解析枚举项列表
     */
    public static List<EnumsEntityContent> parseEnumItems(String rawComment) {
        List<EnumsEntityContent> result = new ArrayList<>();
        if (StringUtils.isBlank(rawComment)) {
            return result;
        }

        String cleaned = StringUtils.deleteWhitespace(rawComment);
        Matcher matcher = BRACKET_PATTERN.matcher(cleaned);

        while (matcher.find()) {
            String inside = matcher.group(1);
            if (StringUtils.isBlank(inside)) {
                continue;
            }
            List<EnumsEntityContent> candidateItems = new ArrayList<>();
            String[] parts = inside.split(ITEM_SPLIT_REGEX);
            for (String part : parts) {
                if (StringUtils.isBlank(part)) {
                    continue;
                }
                String[] kv = part.split(KV_SPLIT_REGEX, 2);
                if (kv.length >= 2 && StringUtils.isNoneBlank(kv[0], kv[1])) {
                    EnumsEntityContent content = new EnumsEntityContent();
                    content.setState(kv[0].trim());
                    content.setNote(kv[1].trim());
                    candidateItems.add(content);
                }
            }
            if (!candidateItems.isEmpty()) {
                result.addAll(candidateItems);
            }
        }
        return result;
    }

    /**
     * 生成枚举预览文本（供可视化 UI 展示）
     */
    public static String formatPreview(String columnName, List<EnumsEntityContent> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(columnName).append(" -> [");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(items.get(i).getState()).append(":").append(items.get(i).getNote());
        }
        sb.append("]");
        return sb.toString();
    }
}
