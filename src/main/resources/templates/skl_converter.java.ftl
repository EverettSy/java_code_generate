package ${packageName}.entity.converter;

import ${packageName}.entity.${ClassName};
import ${packageName}.entity.bo.${ClassName}BO;
import ${packageName}.entity.query.${ClassName}Query;
import ${packageName}.entity.vo.${ClassName}VO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * ${functionName} MapStruct 对象转换器
 *
 * @author ${author}
 * @since ${date}
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ${ClassName}Converter {

    ${ClassName}Converter INSTANCE = Mappers.getMapper(${ClassName}Converter.class);

    /**
     * BO 转 Entity
     */
    ${ClassName} boToEntity(${ClassName}BO bo);

    /**
     * Query 转 Entity
     */
    ${ClassName} queryToEntity(${ClassName}Query query);

    /**
     * Entity 转 VO
     */
    ${ClassName}VO entityToVO(${ClassName} entity);

    /**
     * Entity 列表转 VO 列表
     */
    List<${ClassName}VO> entityListToVOList(List<${ClassName}> entityList);
}
