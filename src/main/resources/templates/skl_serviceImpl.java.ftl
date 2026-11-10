package ${packageName}.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import ${packageName}.entity.${ClassName};
import ${packageName}.entity.bo.${ClassName}BO;
import ${packageName}.entity.query.${ClassName}Query;
import ${packageName}.entity.vo.${ClassName}VO;
<#if mapperStrategy?? && mapperStrategy == "MAPSTRUCT">
import ${packageName}.entity.converter.${ClassName}Converter;
<#elseif mapperStrategy?? && mapperStrategy == "HUTOOL">
import cn.hutool.core.convert.Convert;
<#else>
import org.springframework.beans.BeanUtils;
</#if>
import ${packageName}.mapper.${ClassName}Mapper;
import ${packageName}.service.${ClassName}Service;
<#if subTableRelations?? && (subTableRelations?size > 0)>
<#list subTableRelations as rel>
import ${packageName}.entity.bo.${rel.subClassName}BO;
import ${packageName}.service.${rel.subClassName}Service;
</#list>
</#if>
import ${packageName}.service.query.${ClassName}ServiceQuery;
import ${packageName}.service.results.${ClassName}ServiceResults;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

<#if treeTable?? && treeTable>
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
<#else>
import java.util.List;
</#if>

/**
 * ${functionName} Service 实现类
 *
 * @author ${author}
 * @since ${date}
 */
@Slf4j
@Service
@Setter(onMethod_ = {@Autowired})
public class ${ClassName}ServiceImpl extends ServiceImpl<${ClassName}Mapper, ${ClassName}> implements ${ClassName}Service {

    private ${ClassName}Mapper ${className}Mapper;
    private ${ClassName}ServiceResults ${className}ServiceResults;
    private ${ClassName}ServiceQuery ${className}ServiceQuery;
<#if subTableRelations?? && (subTableRelations?size > 0)>
<#list subTableRelations as rel>
    private ${rel.subClassName}Service ${rel.subClassVarName}Service;
</#list>
</#if>

    @Override
    public List<${ClassName}VO> listEnhance(${ClassName}Query ${className}Query) {
<#if mapperStrategy?? && mapperStrategy == "MAPSTRUCT">
        ${ClassName} ${className} = ${ClassName}Converter.INSTANCE.queryToEntity(${className}Query);
<#elseif mapperStrategy?? && mapperStrategy == "HUTOOL">
        ${ClassName} ${className} = Convert.convert(${ClassName}.class, ${className}Query);
<#else>
        ${ClassName} ${className} = new ${ClassName}();
        BeanUtils.copyProperties(${className}Query, ${className});
</#if>
        QueryWrapper<${ClassName}> queryWrapper = new QueryWrapper<>(${className});
        query(${className}Query, queryWrapper);
        return ${className}ServiceResults.assignment(list(queryWrapper));
    }
<#if treeTable?? && treeTable>

    @Override
    public List<${ClassName}VO> treeEnhance(${ClassName}Query ${className}Query) {
        List<${ClassName}VO> allNodes = listEnhance(${className}Query);
        if (allNodes == null || allNodes.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Object, ${ClassName}VO> nodeMap = new LinkedHashMap<>();
        for (${ClassName}VO node : allNodes) {
            if (node.get${treeIdColumnUpper!"Id"}() != null) {
                nodeMap.put(node.get${treeIdColumnUpper!"Id"}(), node);
            }
        }
        List<${ClassName}VO> roots = new ArrayList<>();
        for (${ClassName}VO node : allNodes) {
            Object parentKey = node.get${treeParentColumnUpper!"ParentId"}();
            ${ClassName}VO parentNode = parentKey != null ? nodeMap.get(parentKey) : null;
            if (parentNode != null && !parentKey.equals(node.get${treeIdColumnUpper!"Id"}())) {
                if (parentNode.getChildren() == null) {
                    parentNode.setChildren(new ArrayList<>());
                }
                parentNode.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }
</#if>

    @Override
    public Page<${ClassName}VO> pageEnhance(Page page, ${ClassName}Query ${className}Query) {
<#if mapperStrategy?? && mapperStrategy == "MAPSTRUCT">
        ${ClassName} ${className} = ${ClassName}Converter.INSTANCE.queryToEntity(${className}Query);
<#elseif mapperStrategy?? && mapperStrategy == "HUTOOL">
        ${ClassName} ${className} = Convert.convert(${ClassName}.class, ${className}Query);
<#else>
        ${ClassName} ${className} = new ${ClassName}();
        BeanUtils.copyProperties(${className}Query, ${className});
</#if>
        QueryWrapper<${ClassName}> queryWrapper = new QueryWrapper<>(${className});
        query(${className}Query, queryWrapper);
        return ${className}ServiceResults.assignment(page(page, queryWrapper));
    }

    @Override
    public ${ClassName}VO getOneEnhance(${ClassName}Query ${className}Query) {
<#if mapperStrategy?? && mapperStrategy == "MAPSTRUCT">
        ${ClassName} ${className} = ${ClassName}Converter.INSTANCE.queryToEntity(${className}Query);
<#elseif mapperStrategy?? && mapperStrategy == "HUTOOL">
        ${ClassName} ${className} = Convert.convert(${ClassName}.class, ${className}Query);
<#else>
        ${ClassName} ${className} = new ${ClassName}();
        BeanUtils.copyProperties(${className}Query, ${className});
</#if>
        QueryWrapper<${ClassName}> queryWrapper = new QueryWrapper<>(${className});
        query(${className}Query, queryWrapper);
        return ${className}ServiceResults.assignment(getOne(queryWrapper));
    }

    @Override
    public Long countEnhance(${ClassName}Query ${className}Query) {
<#if mapperStrategy?? && mapperStrategy == "MAPSTRUCT">
        ${ClassName} ${className} = ${ClassName}Converter.INSTANCE.queryToEntity(${className}Query);
<#elseif mapperStrategy?? && mapperStrategy == "HUTOOL">
        ${ClassName} ${className} = Convert.convert(${ClassName}.class, ${className}Query);
<#else>
        ${ClassName} ${className} = new ${ClassName}();
        BeanUtils.copyProperties(${className}Query, ${className});
</#if>
        QueryWrapper<${ClassName}> queryWrapper = new QueryWrapper<>(${className});
        query(${className}Query, queryWrapper);
        return count(queryWrapper);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, timeout = 36000, rollbackFor = Exception.class)
    public String saveEnhance(${ClassName}BO ${className}BO) {
<#if mapperStrategy?? && mapperStrategy == "MAPSTRUCT">
        ${ClassName} ${className} = ${ClassName}Converter.INSTANCE.boToEntity(${className}BO);
<#elseif mapperStrategy?? && mapperStrategy == "HUTOOL">
        ${ClassName} ${className} = Convert.convert(${ClassName}.class, ${className}BO);
<#else>
        ${ClassName} ${className} = new ${ClassName}();
        BeanUtils.copyProperties(${className}BO, ${className});
</#if>
        save(${className});
<#if subTableRelations?? && (subTableRelations?size > 0)>
<#list subTableRelations as rel>
        if (${className}BO.get${rel.subClassName}List() != null && !${className}BO.get${rel.subClassName}List().isEmpty()) {
            for (${rel.subClassName}BO subItem : ${className}BO.get${rel.subClassName}List()) {
                subItem.set${rel.foreignKeyPropertyUpper}(${className}.get${rel.masterKeyPropertyUpper}());
                ${rel.subClassVarName}Service.saveEnhance(subItem);
            }
        }
</#list>
</#if>
        return "";
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, timeout = 36000, rollbackFor = Exception.class)
    public Boolean updateEnhance(${ClassName}BO ${className}BO) {
<#if mapperStrategy?? && mapperStrategy == "MAPSTRUCT">
        ${ClassName} ${className} = ${ClassName}Converter.INSTANCE.boToEntity(${className}BO);
<#elseif mapperStrategy?? && mapperStrategy == "HUTOOL">
        ${ClassName} ${className} = Convert.convert(${ClassName}.class, ${className}BO);
<#else>
        ${ClassName} ${className} = new ${ClassName}();
        BeanUtils.copyProperties(${className}BO, ${className});
</#if>
        UpdateWrapper<${ClassName}> updateWrapper = new UpdateWrapper<>();
        return update(${className}, updateWrapper);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, timeout = 36000, rollbackFor = Exception.class)
    public Boolean removeEnhance(${ClassName}BO ${className}BO) {
<#if mapperStrategy?? && mapperStrategy == "MAPSTRUCT">
        ${ClassName} ${className} = ${ClassName}Converter.INSTANCE.boToEntity(${className}BO);
<#elseif mapperStrategy?? && mapperStrategy == "HUTOOL">
        ${ClassName} ${className} = Convert.convert(${ClassName}.class, ${className}BO);
<#else>
        ${ClassName} ${className} = new ${ClassName}();
        BeanUtils.copyProperties(${className}BO, ${className});
</#if>
        QueryWrapper<${ClassName}> queryWrapper = new QueryWrapper<>(${className});
        return remove(queryWrapper);
    }

    private void query(${ClassName}Query ${className}Query, QueryWrapper<${ClassName}> queryWrapper) {
        ${className}ServiceQuery.query(${className}Query, queryWrapper);
        if (Boolean.TRUE.equals(${className}Query.getCollation())) {
            queryWrapper.orderByAsc(${className}Query.getCollationFields());
        } else if (StringUtils.isNotBlank(${className}Query.getCollationFields())) {
            queryWrapper.orderByDesc(${className}Query.getCollationFields());
        }
        if (StringUtils.isNotBlank(${className}Query.getFields())) {
            queryWrapper.select(${className}Query.getFields());
        }
    }
}
