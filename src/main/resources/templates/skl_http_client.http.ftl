### ${functionName} (${tableName}) REST API 联调测试套件
### 自动生成时间: ${date} | 作者: ${author}
@contentType = application/json

### 1. ${functionName} — 分页查询 (GET /${className}/select)
GET {{baseUrl}}/${className}/select?current=1&size=10
Accept: {{contentType}}

### 2. ${functionName} — 列表查询 (GET /${className}/selectList)
GET {{baseUrl}}/${className}/selectList
Accept: {{contentType}}
<#if treeTable?? && treeTable>

### 2.5 ${functionName} — 树形结构查询 (GET /${className}/tree)
GET {{baseUrl}}/${className}/tree
Accept: {{contentType}}
</#if>

### 3. ${functionName} — 单条查询 (GET /${className}/selectOne)
GET {{baseUrl}}/${className}/selectOne?id=1
Accept: {{contentType}}

### 4. ${functionName} — 总数统计 (GET /${className}/count)
GET {{baseUrl}}/${className}/count
Accept: {{contentType}}

### 5. ${functionName} — 新增保存 (POST /${className}/save)
POST {{baseUrl}}/${className}/save
Content-Type: {{contentType}}

{
<#list list as column>
<#if !(column.primaryKey?? && column.primaryKey)>
  "${column.columnName}": <#if column.dataType == "Integer" || column.dataType == "Long" || column.dataType == "Short">1<#elseif column.dataType == "BigDecimal" || column.dataType == "Double" || column.dataType == "Float">99.99<#elseif column.dataType == "Boolean">true<#elseif column.dataType == "LocalDateTime" || column.dataType == "Date">"2026-01-01 12:00:00"<#else>"sample_${column.columnName}"</#if><#if column_has_next>,</#if>
</#if>
</#list>
}

### 6. ${functionName} — 修改更新 (PUT /${className}/update)
PUT {{baseUrl}}/${className}/update
Content-Type: {{contentType}}

{
<#list list as column>
  "${column.columnName}": <#if column.dataType == "Integer" || column.dataType == "Long" || column.dataType == "Short">1<#elseif column.dataType == "BigDecimal" || column.dataType == "Double" || column.dataType == "Float">99.99<#elseif column.dataType == "Boolean">true<#elseif column.dataType == "LocalDateTime" || column.dataType == "Date">"2026-01-01 12:00:00"<#else>"updated_${column.columnName}"</#if><#if column_has_next>,</#if>
</#list>
}

### 7. ${functionName} — 删除记录 (DELETE /${className}/remove)
DELETE {{baseUrl}}/${className}/remove
Content-Type: {{contentType}}

{
  "id": 1
}
<#if excelEnabled?? && excelEnabled>

### 8. ${functionName} — 导出 Excel 数据 (GET /${className}/exportExcel)
GET {{baseUrl}}/${className}/exportExcel
Accept: {{contentType}}
</#if>
