package ${packageName}.service.results;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ${packageName}.entity.${ClassName};
import ${packageName}.entity.vo.${ClassName}VO;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * ${functionName} Service 查询结果转换类
 *
 * @author ${author}
 * @since ${date}
 */
@Slf4j
@Service
@Setter(onMethod_ = {@Autowired})
public class ${ClassName}ServiceResults {

    /**
     * 单条实体转 VO
     */
    public ${ClassName}VO assignment(${ClassName} ${className}) {
        if (${className} == null) {
            return null;
        }
        ${ClassName}VO vo = new ${ClassName}VO();
        BeanUtils.copyProperties(${className}, vo);
        return vo;
    }

    /**
     * 分页实体转 VO
     */
    public Page<${ClassName}VO> assignment(Page<${ClassName}> page) {
        if (page == null) {
            return new Page<>();
        }
        Page<${ClassName}VO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(assignment(page.getRecords()));
        return voPage;
    }

    /**
     * 列表实体转 VO
     */
    public List<${ClassName}VO> assignment(List<${ClassName}> list) {
        List<${ClassName}VO> voList = new ArrayList<>();
        if (list == null) {
            return voList;
        }
        for (${ClassName} item : list) {
            voList.add(assignment(item));
        }
        return voList;
    }
}
