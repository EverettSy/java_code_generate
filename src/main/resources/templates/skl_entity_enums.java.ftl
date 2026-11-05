package ${packageName}.entity.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * ${functionName} - ${enumsEntityName} 状态枚举
 *
 * @author ${author}
 * @since ${date}
 */
@Getter
@AllArgsConstructor
public enum ${ClassName}${enumsEntityName}Enum implements IEnum<Integer> {
<#list enumsEntitiesList as item>

    /**
     * ${item.note!}
     */
    ${item.note!}(${item.state}, "${item.note!}")<#if item_has_next>,<#else>;</#if>
</#list>

    private final Integer value;
    private final String desc;

    public static ${ClassName}${enumsEntityName}Enum getEnum(Integer value) {
        if (value == null) {
            return null;
        }
        for (${ClassName}${enumsEntityName}Enum item : values()) {
            if (item.getValue().equals(value)) {
                return item;
            }
        }
        return null;
    }

    public static String getDesc(Integer value) {
        ${ClassName}${enumsEntityName}Enum item = getEnum(value);
        return item != null ? item.getDesc() : "";
    }
}
