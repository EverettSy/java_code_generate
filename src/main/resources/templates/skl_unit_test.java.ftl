package ${packageName}.service;

import ${packageName}.entity.${ClassName};
import ${packageName}.entity.bo.${ClassName}BO;
import ${packageName}.entity.query.${ClassName}Query;
import ${packageName}.entity.vo.${ClassName}VO;
import ${packageName}.mapper.${ClassName}Mapper;
import ${packageName}.service.impl.${ClassName}ServiceImpl;
import ${packageName}.service.query.${ClassName}ServiceQuery;
import ${packageName}.service.results.${ClassName}ServiceResults;
<#if subTableRelations?? && (subTableRelations?size > 0)>
<#list subTableRelations as rel>
import ${packageName}.entity.bo.${rel.subClassName}BO;
import ${packageName}.service.${rel.subClassName}Service;
</#list>
</#if>
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ${functionName} Service 单元测试（JUnit 5 + Mockito）
 *
 * @author ${author}
 * @since ${date}
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("${functionName} (${ClassName}Service) 单元测试")
class ${ClassName}ServiceTest {

    @InjectMocks
    private ${ClassName}ServiceImpl ${className}Service;

    @Mock
    private ${ClassName}Mapper ${className}Mapper;

    @Spy
    private ${ClassName}ServiceQuery ${className}ServiceQuery;

    @Spy
    private ${ClassName}ServiceResults ${className}ServiceResults;
<#if subTableRelations?? && (subTableRelations?size > 0)>
<#list subTableRelations as rel>

    @Mock
    private ${rel.subClassName}Service ${rel.subClassVarName}Service;
</#list>
</#if>

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(${className}Service, "baseMapper", ${className}Mapper);
    }

    @Test
    @DisplayName("集合条件查询 listEnhance")
    void testListEnhance() {
        ${ClassName} mockEntity = buildMockEntity();
        when(${className}Mapper.selectList(any())).thenReturn(Collections.singletonList(mockEntity));

        ${ClassName}Query query = new ${ClassName}Query();
        List<${ClassName}VO> result = ${className}Service.listEnhance(query);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(${className}Mapper, times(1)).selectList(any());
    }

    @Test
    @DisplayName("分页条件查询 pageEnhance")
    void testPageEnhance() {
        ${ClassName} mockEntity = buildMockEntity();
        Page<${ClassName}> entityPage = new Page<>(1, 10);
        entityPage.setRecords(Collections.singletonList(mockEntity));
        entityPage.setTotal(1L);
        when(${className}Mapper.selectPage(any(), any())).thenReturn(entityPage);

        ${ClassName}Query query = new ${ClassName}Query();
        Page<${ClassName}VO> resultPage = ${className}Service.pageEnhance(new Page<>(1, 10), query);

        assertNotNull(resultPage);
        assertEquals(1L, resultPage.getTotal());
        assertEquals(1, resultPage.getRecords().size());
        verify(${className}Mapper, times(1)).selectPage(any(), any());
    }

    @Test
    @DisplayName("单条详情查询 getOneEnhance")
    void testGetOneEnhance() {
        ${ClassName} mockEntity = buildMockEntity();
        when(${className}Mapper.selectOne(any())).thenReturn(mockEntity);

        ${ClassName}Query query = new ${ClassName}Query();
        ${ClassName}VO vo = ${className}Service.getOneEnhance(query);

        assertNotNull(vo);
        verify(${className}Mapper, times(1)).selectOne(any());
    }

    @Test
    @DisplayName("总数统计 countEnhance")
    void testCountEnhance() {
        when(${className}Mapper.selectCount(any())).thenReturn(5L);

        ${ClassName}Query query = new ${ClassName}Query();
        Long count = ${className}Service.countEnhance(query);

        assertEquals(5L, count);
        verify(${className}Mapper, times(1)).selectCount(any());
    }

    @Test
    @DisplayName("新增保存 saveEnhance")
    void testSaveEnhance() {
        ${ClassName}BO bo = buildMockBO();
        when(${className}Mapper.insert(any(${ClassName}.class))).thenAnswer(invocation -> {
            ${ClassName} entity = invocation.getArgument(0);
<#list list as column>
<#if column.primaryKey?? && column.primaryKey>
<#if column.dataType == 'String'>
            entity.set${column.columnNameUpper}("1");
<#elseif column.dataType == 'Integer'>
            entity.set${column.columnNameUpper}(1);
<#else>
            entity.set${column.columnNameUpper}(1L);
</#if>
</#if>
</#list>
            return 1;
        });

        String id = ${className}Service.saveEnhance(bo);

        assertNotNull(id);
        verify(${className}Mapper, times(1)).insert(any(${ClassName}.class));
<#if subTableRelations?? && (subTableRelations?size > 0)>
<#list subTableRelations as rel>
        verify(${rel.subClassVarName}Service, atLeastOnce()).saveEnhance(any(${rel.subClassName}BO.class));
</#list>
</#if>
    }

    @Test
    @DisplayName("修改更新 updateEnhance")
    void testUpdateEnhance() {
        ${ClassName}BO bo = buildMockBO();
        when(${className}Mapper.update(any(${ClassName}.class), any(QueryWrapper.class))).thenReturn(1);

        Boolean updated = ${className}Service.updateEnhance(bo);

        assertTrue(updated);
        verify(${className}Mapper, times(1)).update(any(${ClassName}.class), any(QueryWrapper.class));
    }

    @Test
    @DisplayName("删除记录 removeEnhance")
    void testRemoveEnhance() {
        ${ClassName}BO bo = buildMockBO();
        when(${className}Mapper.delete(any(QueryWrapper.class))).thenReturn(1);

        Boolean removed = ${className}Service.removeEnhance(bo);

        assertTrue(removed);
        verify(${className}Mapper, times(1)).delete(any(QueryWrapper.class));
    }
<#if treeTable?? && treeTable>

    @Test
    @DisplayName("树形结构查询 treeEnhance")
    void testTreeEnhance() {
        ${ClassName} root = buildMockEntity();
        root.set${treeIdColumnUpper!'Id'}(1L);
        root.set${treeParentColumnUpper!'ParentId'}(0L);

        ${ClassName} child = buildMockEntity();
        child.set${treeIdColumnUpper!'Id'}(2L);
        child.set${treeParentColumnUpper!'ParentId'}(1L);

        when(${className}Mapper.selectList(any())).thenReturn(List.of(root, child));

        List<${ClassName}VO> tree = ${className}Service.treeEnhance(new ${ClassName}Query());

        assertNotNull(tree);
        assertEquals(1, tree.size());
        assertNotNull(tree.get(0).getChildren());
        assertEquals(1, tree.get(0).getChildren().size());
    }
</#if>

    private ${ClassName} buildMockEntity() {
        ${ClassName} entity = new ${ClassName}();
<#list entityColumns as column>
<#if column.dataType == 'String'>
        entity.set${column.columnNameUpper}("test_${column.columnName}");
<#elseif column.dataType == 'Long'>
        entity.set${column.columnNameUpper}(1L);
<#elseif column.dataType == 'Integer'>
        entity.set${column.columnNameUpper}(1);
<#elseif column.dataType == 'Short'>
        entity.set${column.columnNameUpper}((short) 1);
<#elseif column.dataType == 'BigDecimal'>
        entity.set${column.columnNameUpper}(BigDecimal.TEN);
<#elseif column.dataType == 'Double'>
        entity.set${column.columnNameUpper}(10.0D);
<#elseif column.dataType == 'Float'>
        entity.set${column.columnNameUpper}(10.0F);
<#elseif column.dataType == 'Boolean'>
        entity.set${column.columnNameUpper}(Boolean.TRUE);
<#elseif column.dataType == 'LocalDateTime'>
        entity.set${column.columnNameUpper}(LocalDateTime.now());
</#if>
</#list>
        return entity;
    }

    private ${ClassName}BO buildMockBO() {
        ${ClassName}BO bo = new ${ClassName}BO();
<#list list as column>
<#if column.dataType == 'String'>
        bo.set${column.columnNameUpper}("test_${column.columnName}");
<#elseif column.dataType == 'Long'>
        bo.set${column.columnNameUpper}(1L);
<#elseif column.dataType == 'Integer'>
        bo.set${column.columnNameUpper}(1);
<#elseif column.dataType == 'Short'>
        bo.set${column.columnNameUpper}((short) 1);
<#elseif column.dataType == 'BigDecimal'>
        bo.set${column.columnNameUpper}(BigDecimal.TEN);
<#elseif column.dataType == 'Double'>
        bo.set${column.columnNameUpper}(10.0D);
<#elseif column.dataType == 'Float'>
        bo.set${column.columnNameUpper}(10.0F);
<#elseif column.dataType == 'Boolean'>
        bo.set${column.columnNameUpper}(Boolean.TRUE);
<#elseif column.dataType == 'LocalDateTime'>
        bo.set${column.columnNameUpper}(LocalDateTime.now());
</#if>
</#list>
<#if subTableRelations?? && (subTableRelations?size > 0)>
<#list subTableRelations as rel>
        bo.set${rel.subClassName}List(Collections.singletonList(new ${rel.subClassName}BO()));
</#list>
</#if>
        return bo;
    }
}
