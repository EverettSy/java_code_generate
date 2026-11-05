package ${packageName}.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import ${packageName}.entity.${ClassName};
import ${packageName}.entity.bo.${ClassName}BO;
import ${packageName}.entity.query.${ClassName}Query;
import ${packageName}.entity.vo.${ClassName}VO;

import java.util.List;

/**
 * ${functionName} Service 接口
 *
 * @author ${author}
 * @since ${date}
 */
public interface ${ClassName}Service extends IService<${ClassName}> {

    /**
     * 集合条件查询
     */
    List<${ClassName}VO> listEnhance(${ClassName}Query ${className}Query);

    /**
     * 分页条件查询
     */
    Page<${ClassName}VO> pageEnhance(Page page, ${ClassName}Query ${className}Query);

    /**
     * 单条条件查询
     */
    ${ClassName}VO getOneEnhance(${ClassName}Query ${className}Query);

    /**
     * 总数统计
     */
    Long countEnhance(${ClassName}Query ${className}Query);

    /**
     * 新增
     */
    String saveEnhance(${ClassName}BO ${className}BO);

    /**
     * 修改
     */
    Boolean updateEnhance(${ClassName}BO ${className}BO);

    /**
     * 删除
     */
    Boolean removeEnhance(${ClassName}BO ${className}BO);
}
