-- =============================================================================
-- Flyway / Liquibase Standardized DDL Migration Script
-- Table       : ${tableName} (${functionName})
-- Author      : ${author}
-- Generated At: ${date}
-- =============================================================================

CREATE TABLE IF NOT EXISTS `${tableName}` (
<#list list as column>
    `${column.typeName}` <#if column.dataType == 'Long'>bigint<#elseif column.dataType == 'Integer'>int<#elseif column.dataType == 'Short'>smallint<#elseif column.dataType == 'BigDecimal'>decimal(12,2)<#elseif column.dataType == 'Double'>double<#elseif column.dataType == 'Float'>float<#elseif column.dataType == 'Boolean'>tinyint(1)<#elseif column.dataType == 'LocalDateTime'>datetime<#else>varchar(${(column.maxLength?? && (column.maxLength > 0))?then(column.maxLength?c, '128')})</#if> <#if (column.primaryKey?? && column.primaryKey) || (column.nullable?? && !column.nullable)>NOT NULL<#else>DEFAULT NULL</#if> COMMENT '${column.columnComment!}'<#if column_has_next || (treeIdColumnName?? && treeIdColumnName?has_content)>,</#if>
</#list>
<#assign pkCols = []>
<#list list as column>
<#if column.primaryKey?? && column.primaryKey>
<#assign pkCols = pkCols + ["`" + column.typeName + "`"]>
</#if>
</#list>
<#if (pkCols?size > 0)>
    PRIMARY KEY (${pkCols?join(", ")})
<#else>
    PRIMARY KEY (`id`)
</#if>
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='${functionName}';
<#if treeTable?? && treeTable>

CREATE INDEX `idx_${tableName}_parent` ON `${tableName}` (`${treeParentColumnName}`);
</#if>
