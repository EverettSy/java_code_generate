package ${packageName}.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import ${packageName}.entity.${ClassName};
import ${packageName}.entity.bo.${ClassName}BO;
import ${packageName}.entity.query.${ClassName}Query;
import ${packageName}.entity.vo.${ClassName}VO;
import ${packageName}.mapper.${ClassName}Mapper;
import ${packageName}.service.${ClassName}Service;
import ${packageName}.service.query.${ClassName}ServiceQuery;
import ${packageName}.service.results.${ClassName}ServiceResults;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    @Override
    public List<${ClassName}VO> listEnhance(${ClassName}Query ${className}Query) {
        ${ClassName} ${className} = new ${ClassName}();
        BeanUtils.copyProperties(${className}Query, ${className});
        QueryWrapper<${ClassName}> queryWrapper = new QueryWrapper<>(${className});
        query(${className}Query, queryWrapper);
        return ${className}ServiceResults.assignment(list(queryWrapper));
    }

    @Override
    public Page<${ClassName}VO> pageEnhance(Page page, ${ClassName}Query ${className}Query) {
        ${ClassName} ${className} = new ${ClassName}();
        BeanUtils.copyProperties(${className}Query, ${className});
        QueryWrapper<${ClassName}> queryWrapper = new QueryWrapper<>(${className});
        query(${className}Query, queryWrapper);
        return ${className}ServiceResults.assignment(page(page, queryWrapper));
    }

    @Override
    public ${ClassName}VO getOneEnhance(${ClassName}Query ${className}Query) {
        ${ClassName} ${className} = new ${ClassName}();
        BeanUtils.copyProperties(${className}Query, ${className});
        QueryWrapper<${ClassName}> queryWrapper = new QueryWrapper<>(${className});
        query(${className}Query, queryWrapper);
        return ${className}ServiceResults.assignment(getOne(queryWrapper));
    }

    @Override
    public Long countEnhance(${ClassName}Query ${className}Query) {
        ${ClassName} ${className} = new ${ClassName}();
        BeanUtils.copyProperties(${className}Query, ${className});
        QueryWrapper<${ClassName}> queryWrapper = new QueryWrapper<>(${className});
        query(${className}Query, queryWrapper);
        return count(queryWrapper);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, timeout = 36000, rollbackFor = Exception.class)
    public String saveEnhance(${ClassName}BO ${className}BO) {
        ${ClassName} ${className} = new ${ClassName}();
        BeanUtils.copyProperties(${className}BO, ${className});
        save(${className});
        return "";
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, timeout = 36000, rollbackFor = Exception.class)
    public Boolean updateEnhance(${ClassName}BO ${className}BO) {
        ${ClassName} ${className} = new ${ClassName}();
        BeanUtils.copyProperties(${className}BO, ${className});
        UpdateWrapper<${ClassName}> updateWrapper = new UpdateWrapper<>();
        return update(${className}, updateWrapper);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, timeout = 36000, rollbackFor = Exception.class)
    public Boolean removeEnhance(${ClassName}BO ${className}BO) {
        ${ClassName} ${className} = new ${ClassName}();
        BeanUtils.copyProperties(${className}BO, ${className});
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
