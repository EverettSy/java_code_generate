/**
 * ${functionName} (${tableName}) 前端 TypeScript 类型定义与 Axios API 模块
 *
 * @author ${author}
 * @since ${date}
 */
import axios, { AxiosPromise } from 'axios';

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
}

/**
 * ${functionName} 视图对象 (VO)
 */
export interface ${ClassName}VO {
<#list list as column>
  /** ${column.columnComment!} */
  ${column.columnName}?: <#if column.dataType == "Integer" || column.dataType == "Short" || column.dataType == "Double" || column.dataType == "Float">number<#elseif column.dataType == "Long" || column.dataType == "BigDecimal">string | number<#elseif column.dataType == "Boolean">boolean<#else>string</#if>;
</#list>
<#if subTableRelations?? && (subTableRelations?size > 0)>
<#list subTableRelations as rel>
  /** ${rel.relationComment! (rel.subClassName + "子表列表")} */
  ${rel.subClassVarName}List?: Record<string, any>[];
</#list>
</#if>
}

/**
 * ${functionName} 业务提交对象 (BO)
 */
export interface ${ClassName}BO {
<#list list as column>
  /** ${column.columnComment!} */
  ${column.columnName}<#if column.nullable?? && !column.nullable && !(column.primaryKey?? && column.primaryKey)><#else>?</#if>: <#if column.dataType == "Integer" || column.dataType == "Short" || column.dataType == "Double" || column.dataType == "Float">number<#elseif column.dataType == "Long" || column.dataType == "BigDecimal">string | number<#elseif column.dataType == "Boolean">boolean<#else>string</#if>;
</#list>
<#if subTableRelations?? && (subTableRelations?size > 0)>
<#list subTableRelations as rel>
  /** ${rel.relationComment! (rel.subClassName + "子表列表")} */
  ${rel.subClassVarName}List?: Record<string, any>[];
</#list>
</#if>
}

/**
 * ${functionName} 查询参数对象 (Query)
 */
export interface ${ClassName}Query {
  current?: number;
  size?: number;
  fields?: string;
  collation?: boolean;
  collationFields?: string;
<#list list as column>
  /** ${column.columnComment!} */
  ${column.columnName}?: <#if column.dataType == "Integer" || column.dataType == "Short" || column.dataType == "Double" || column.dataType == "Float">number<#elseif column.dataType == "Long" || column.dataType == "BigDecimal">string | number<#elseif column.dataType == "Boolean">boolean<#else>string</#if>;
</#list>
<#if beanQuery?? && (beanQuery?size > 0)>
<#list beanQuery as bq>
  /** ${bq.description!} */
  ${bq.name}?: string | number;
</#list>
</#if>
}

const BASE_PATH = '/${className}';

/**
 * ${functionName} 分页查询
 */
export function select${ClassName}Page(params: ${ClassName}Query): AxiosPromise<PageResult<${ClassName}VO>> {
  return axios.get(`${r"${BASE_PATH}"}/select`, { params });
}

/**
 * ${functionName} 列表查询
 */
export function select${ClassName}List(params?: ${ClassName}Query): AxiosPromise<${ClassName}VO[]> {
  return axios.get(`${r"${BASE_PATH}"}/selectList`, { params });
}

/**
 * ${functionName} 单条详情查询
 */
export function select${ClassName}One(params: ${ClassName}Query): AxiosPromise<${ClassName}VO> {
  return axios.get(`${r"${BASE_PATH}"}/selectOne`, { params });
}

/**
 * ${functionName} 总数统计
 */
export function count${ClassName}(params?: ${ClassName}Query): AxiosPromise<number> {
  return axios.get(`${r"${BASE_PATH}"}/count`, { params });
}

/**
 * ${functionName} 新增保存
 */
export function save${ClassName}(data: ${ClassName}BO): AxiosPromise<string> {
  return axios.post(`${r"${BASE_PATH}"}/save`, data);
}

/**
 * ${functionName} 修改更新
 */
export function update${ClassName}(data: ${ClassName}BO): AxiosPromise<boolean> {
  return axios.put(`${r"${BASE_PATH}"}/update`, data);
}

/**
 * ${functionName} 删除
 */
export function remove${ClassName}(data: Partial<${ClassName}BO>): AxiosPromise<boolean> {
  return axios.delete(`${r"${BASE_PATH}"}/remove`, { data });
}
